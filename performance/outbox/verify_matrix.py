#!/usr/bin/env python3
"""Independently recheck saved raw DB/Kafka records and summary values (read-only)."""
import json
from pathlib import Path
import statistics
import sys

from run_baseline import percentile


def verify(folder):
    blocks = json.loads((folder / 'summary.json').read_text())
    measured = 0
    saved_runs = [json.loads(path.read_text()) for path in sorted(folder.glob('*-r[0-9]*-summary.json'))]
    for run in saved_runs:
            prefix = run['prefix']
            rows = [line.split('\t') for line in (folder / f'{prefix}-events.tsv').read_text().splitlines()]
            records = [line.split('\t') for line in (folder / f'{prefix}-kafka-read.tsv').read_text().splitlines()]
            db_keys = [row[1] for row in rows]
            kafka_keys = [row[1] for row in records]
            count = run['events']
            assert len(rows) == len(records) == len(set(db_keys)) == len(set(kafka_keys)) == count, prefix
            assert set(db_keys) == set(kafka_keys), prefix
            assert all(row[2] == 'PUBLISHED' and row[3] == '0' and row[5:] == ['NULL', 'NULL'] for row in rows), prefix
            assert all(record[2] == '{}' for record in records), prefix
            assert [int(record[0]) for record in records] == list(range(count)), prefix
            assert percentile([float(row[4]) for row in rows], 99) == run['publication_record_lag_p99_ms'], prefix
            assert sum(run['per_relay_published']) == count, prefix
            assert run['completion_counters'] == {'published': count, 'failed': 0, 'stale': 0}, prefix
            assert all(run[name] == 0 for name in ('missing', 'unexpected', 'duplicates', 'retries', 'recovered')), prefix
            timeline = json.loads((folder / f'{prefix}-timeline.json').read_text())
            assert timeline[-1]['states'] == {'PUBLISHED': count}, prefix
            assert round(timeline[-1]['elapsed'], 3) == run['observed_drain_seconds'], prefix
            measured += count
    for block in blocks:
        assert statistics.median(r['publication_record_lag_p99_ms'] for r in block['runs']) == block['median_p99_ms']
    errors = []
    for log in sorted(folder.glob('*.log')):
        for line in log.read_text().splitlines():
            if '"level":"ERROR"' in line or ' ERROR ' in line:
                errors.append({'file': log.name, 'line': line})
    result = {'raw_data_verification': 'PASS', 'completed_blocks': len(blocks), 'completed_runs': len(saved_runs),
              'measured_events': measured, 'application_error_log_count': len(errors), 'errors': errors}
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return result


if __name__ == '__main__':
    verify(Path(sys.argv[1]).resolve())
