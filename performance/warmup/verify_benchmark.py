#!/usr/bin/env python3
"""Recompute latency summaries and validate raw evidence: verify_benchmark.py RESULTS_DIR."""

import argparse
import json
import math
from pathlib import Path
import statistics

from run_benchmark import digest, order_for_pairs, summarize, valid_response, warmup_log


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return json.loads(path.read_text())


def close(actual, expected):
    return isinstance(actual, (int, float)) and math.isfinite(actual) and math.isclose(
        actual, expected, rel_tol=1e-9, abs_tol=1e-6)


def verify(output):
    require(not (output / 'failure.json').exists(), 'Run contains a recorded failure')
    require(not (output / 'cleanup-failure.json').exists(), 'Container cleanup failed')
    manifest = read(output / 'manifest.json')
    require(set(manifest) == {p.name for p in output.iterdir() if p.is_file() and p.name != 'manifest.json'},
            'Artifact file list differs from manifest')
    for name, checksum in manifest.items():
        require(Path(name).name == name and name != 'manifest.json', 'Unsafe artifact path')
        require(digest((output / name).read_bytes()) == checksum, f'Checksum mismatch: {name}')
    meta, summary = read(output / 'metadata.json'), read(output / 'summary.json')
    require(meta['protocol'] == 1, 'Unknown measurement protocol')
    require(summary['status'] == 'completed', 'Incomplete run')
    if meta['purpose'] == 'comparison':
        require(meta['pairs'] >= 5 and meta['initial_requests'] == 100 and meta['steady_seconds'] == 60,
                'Insufficient comparison protocol')
    require(meta['purpose'] in ('comparison', 'smoke'), 'Unknown purpose')
    order = order_for_pairs(meta['pairs'])
    require(meta['order'] == [list(item) for item in order], 'Incorrect alternating order')
    require([(r['pair'], r['mode']) for r in summary['runs']] == order, 'Missing, extra or reordered runs')
    side = read(output / 'side-effects.json')
    require(side['passed'] is True and side['before']['db'] == side['after']['db'], 'DB side effects')
    require(side['before']['session'] == side['after']['session']
            and side['before']['redis_keys'] == side['after']['redis_keys']
            and 0 < side['after']['pttl'] < side['before']['pttl'], 'Redis changed during warmup')
    require(side['warmup'] == warmup_log(output / 'side-effect-check.log', 'on', meta['iterations']),
            'Invalid preflight warmup')
    for run in summary['runs']:
        name = run['name']
        require(name == f'pair-{run["pair"]:02d}-{run["mode"]}', 'Invalid run name')
        require(read(output / f'{name}-summary.json') == run, f'Run summary differs: {name}')
        require(run['db_unchanged'] is True and run['db_after'] == side['before']['db'], f'Fixture mutated: {name}')
        require(run['warmup'] == warmup_log(output / f'{name}.log', run['mode'], meta['iterations']),
                f'Warmup mismatch: {name}')
        polls = read(output / f'{name}-readiness.json')
        require(polls and polls[-1]['status'] == 200 and close(polls[-1]['elapsed_ms'], run['readiness_ms']),
                f'Readiness mismatch: {name}')
        require(0 < run['readiness_ms'] < 120000 and all(p['status'] != 200 for p in polls[:-1]),
                f'Invalid first ready observation: {name}')
        response = (output / f'{name}-response.json').read_bytes()
        require(valid_response(response), f'Invalid response fixture: {name}')
        records = [json.loads(line) for line in (output / f'{name}-requests.jsonl').read_text().splitlines()]
        expected_phases = ['initial'] * meta['initial_requests'] + ['steady'] * (meta['steady_seconds'] * meta['rps'])
        require([r['phase'] for r in records] == expected_phases, f'Missing or reordered requests: {name}')
        previous_finished = run['readiness_ms']
        for record in records:
            require(record['status'] == 200 and record['valid'] is True, f'Failed request: {name}')
            require(record['body_sha256'] == digest(response), f'Response content changed: {name}')
            require(record['started_ms'] >= previous_finished and record['finished_ms'] >= record['started_ms'],
                    f'Overlapping or negative request timing: {name}')
            require(close(record['latency_ms'], record['finished_ms'] - record['started_ms']), f'Latency mismatch: {name}')
            require(close(record['start_lag_ms'], max(0, record['started_ms'] - record['scheduled_ms']))
                    and record['start_lag_ms'] <= 1000, f'Schedule lag mismatch: {name}')
            require(close(record['schedule_to_finish_ms'], record['finished_ms'] - record['scheduled_ms']),
                    f'Schedule latency mismatch: {name}')
            previous_finished = record['finished_ms']
        for phase in ('initial', 'steady'):
            phase_records = [r for r in records if r['phase'] == phase]
            require([r['index'] for r in phase_records] == list(range(len(phase_records))), f'Index mismatch: {name}')
            for i, r in enumerate(phase_records):
                require(close(r['scheduled_ms'], phase_records[0]['scheduled_ms'] + i * 1000 / meta['rps']),
                        f'Arrival schedule mismatch: {name}')
            calculated = summarize(phase_records)
            require(set(run[phase]) == set(calculated), f'Metric set mismatch: {name}')
            for key, value in calculated.items():
                require(close(run[phase][key], value), f'Summary cannot be reproduced: {name} {phase} {key}')
        samples = read(output / f'{name}-resources.json')
        require(any('rss_kib' in sample for sample in samples), f'No process samples: {name}')
        for host in [run['host_before'], run['host_after'], *samples]:
            require(host['load'][0] <= 2 * host['logical_cpus'], f'Host overloaded: {name}')
    pairs = []
    for pair in range(1, meta['pairs'] + 1):
        group = {r['mode']: r for r in summary['runs'] if r['pair'] == pair}
        pairs.append({'pair': pair, 'readiness_on_minus_off_ms': group['on']['readiness_ms'] - group['off']['readiness_ms'],
                      'initial_p95_off_ms': group['off']['initial']['p95_ms'],
                      'initial_p95_on_ms': group['on']['initial']['p95_ms'],
                      'scheduled_p95_off_ms': group['off']['initial']['scheduled_p95_ms'],
                      'scheduled_p95_on_ms': group['on']['initial']['scheduled_p95_ms']})
    return {'verified_runs': len(summary['runs']), 'purpose': meta['purpose'], 'pairs': pairs,
            'median_initial_p95_ms': {mode: statistics.median(r['initial']['p95_ms'] for r in summary['runs']
                                                             if r['mode'] == mode) for mode in ('off', 'on')},
            'interpretation': 'Observed local values only; inspect all pairs and startup costs before making claims.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    print(json.dumps(verify(parser.parse_args().output), ensure_ascii=False, indent=2))
