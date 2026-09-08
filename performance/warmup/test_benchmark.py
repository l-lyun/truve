import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import run_benchmark as runner
import verify_benchmark as verifier


def response_body():
    sections = []
    for section in range(2):
        rows = []
        for row in range(10):
            rows.append({'row': chr(65 + row), 'seats': [
                {'scheduledSeatId': section * 100 + row * 10 + col + 1, 'status': 'AVAILABLE'}
                for col in range(10)]})
        sections.append({'rows': rows})
    return json.dumps({'code': 'ok', 'data': {'sections': sections}}).encode()


def refresh_manifest(root):
    runner.write_json(root / 'manifest.json', {p.name: runner.digest(p.read_bytes()) for p in root.iterdir()
                                              if p.name != 'manifest.json'})


def evidence(root):
    """Small complete dataset with independently specified 10/20ms observations."""
    meta = {'protocol': 1, 'purpose': 'smoke', 'pairs': 1, 'rps': 2,
            'initial_requests': 2, 'steady_seconds': 1, 'iterations': 100,
            'order': [[1, 'off'], [1, 'on']]}
    runner.write_json(root / 'metadata.json', meta)
    db = {'scheduled_seat': {'count': 200, 'sha256': 'unchanged'}}
    runner.write_json(root / 'side-effects.json', {
        'passed': True, 'before': {'db': db, 'session': 'value', 'redis_keys': '1', 'pttl': 600000},
        'after': {'db': db, 'session': 'value', 'redis_keys': '1', 'pttl': 590000},
        'warmup': {'iterations': 100, 'duration_ms': 100}})
    (root / 'side-effect-check.log').write_text('웜업 완료. iterations=100, durationMs=100\n')
    summaries = []
    for mode in ('off', 'on'):
        name = f'pair-01-{mode}'
        records = []
        for phase, base in [('initial', 1000), ('steady', 2000)]:
            for index, latency in enumerate([10, 20]):
                scheduled = base + index * 500
                records.append({'phase': phase, 'index': index, 'valid': True, 'status': 200,
                                'body_sha256': runner.digest(response_body()), 'latency_ms': latency,
                                'scheduled_ms': scheduled, 'started_ms': scheduled + 1,
                                'finished_ms': scheduled + 1 + latency, 'start_lag_ms': 1,
                                'schedule_to_finish_ms': latency + 1})
        (root / f'{name}-requests.jsonl').write_text(''.join(json.dumps(r) + '\n' for r in records))
        (root / f'{name}-response.json').write_bytes(response_body())
        (root / f'{name}.log').write_text('웜업 완료. iterations=100, durationMs=100\n' if mode == 'on' else 'ready\n')
        runner.write_json(root / f'{name}-readiness.json', [{'elapsed_ms': 900, 'status': 200}])
        host = {'load': [1, 1, 1], 'logical_cpus': 8}
        runner.write_json(root / f'{name}-resources.json', [{**host, 'rss_kib': 200000}])
        metrics = {'count': 2, 'errors': 0, 'first_ms': 10, 'p50_ms': 10, 'p95_ms': 20, 'p99_ms': 20,
                   'scheduled_p95_ms': 21, 'scheduled_p99_ms': 21, 'max_ms': 20,
                   'max_start_lag_ms': 1, 'completed_rps': 2 / .521}
        summary = {'name': name, 'pair': 1, 'mode': mode, 'readiness_ms': 900,
                   'warmup': {'iterations': 100, 'duration_ms': 100} if mode == 'on' else None,
                   'db_unchanged': True, 'db_after': db, 'initial': metrics, 'steady': metrics,
                   'host_before': host, 'host_after': host}
        runner.write_json(root / f'{name}-summary.json', summary)
        summaries.append(summary)
    runner.write_json(root / 'summary.json', {'status': 'completed', 'runs': summaries})
    refresh_manifest(root)


class BenchmarkTest(unittest.TestCase):
    def test_order_alternates_within_pairs(self):
        self.assertEqual(runner.order_for_pairs(3),
                         [(1, 'off'), (1, 'on'), (2, 'on'), (2, 'off'), (3, 'off'), (3, 'on')])

    def test_nearest_rank_percentiles_include_cold_tail(self):
        values = list(range(100, 0, -1))
        self.assertEqual(runner.percentile(values, 95), 95)
        self.assertEqual(runner.percentile(values, 99), 99)

    def test_duplicate_or_sold_seats_are_not_valid_successes(self):
        body = response_body()
        self.assertTrue(runner.valid_response(body))
        self.assertFalse(runner.valid_response(body.replace(b'"scheduledSeatId": 2,', b'"scheduledSeatId": 1,')))
        self.assertFalse(runner.valid_response(body.replace(b'AVAILABLE', b'SOLD', 1)))
        self.assertFalse(runner.valid_response(b'{"code":"ok","data":null}'))

    def test_cleanup_discovers_only_this_invocations_label_even_after_cli_timeout(self):
        infra = runner.Infrastructure()
        infra.created = []
        with patch.object(runner, 'command', side_effect=['owned-mysql\nowned-redis', '', '']) as command:
            infra.close()
        self.assertEqual([call.args[0] for call in command.call_args_list], [
            ['docker', 'ps', '-aq', '--filter', f'label=truve.warmup.run={infra.run_id}'],
            ['docker', 'rm', '-f', '-v', 'owned-mysql'], ['docker', 'rm', '-f', '-v', 'owned-redis']])

    def test_scheduled_latency_preserves_backlog_hidden_by_fast_http_responses(self):
        records = [{'valid': True, 'latency_ms': 20, 'schedule_to_finish_ms': 720,
                    'start_lag_ms': 700, 'scheduled_ms': 100, 'finished_ms': 820}]
        summary = runner.summarize(records)
        self.assertEqual(summary['p95_ms'], 20)
        self.assertEqual(summary['scheduled_p95_ms'], 720)

    def test_timeout_is_recorded_without_silent_retry(self):
        app = SimpleNamespace(started=runner.time.monotonic(), name='failed')
        conn = Mock(sock=None)
        conn.getresponse.side_effect = TimeoutError('slow request')
        stream = io.StringIO()
        with patch.object(runner, 'check_host'), self.assertRaisesRegex(RuntimeError, 'raw record retained'):
            runner.measure_phase(app, conn, 'test-token', 'initial', 100, 10, stream)
        self.assertEqual(conn.request.call_count, 1)
        record = json.loads(stream.getvalue())
        self.assertFalse(record['valid'])
        self.assertIn('TimeoutError', record['error'])
        self.assertGreaterEqual(record['latency_ms'], 0)

    def test_host_overload_prevents_comparison(self):
        with patch.object(runner, 'host_snapshot', return_value={'load': [17, 10, 5], 'logical_cpus': 8}):
            with self.assertRaisesRegex(RuntimeError, 'Host load'):
                runner.check_host()

    def test_error_log_is_rejected_even_with_completed_warmup(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'app.log'
            path.write_text('웜업 완료. iterations=100, durationMs=30\n{"level":"ERROR"}\n')
            with self.assertRaisesRegex(RuntimeError, 'ERROR'):
                runner.warmup_log(path, 'on', 100)

    def test_verifier_rejects_a_recorded_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'failure.json').write_text('{}')
            with self.assertRaisesRegex(ValueError, 'recorded failure'):
                verifier.verify(root)

    def test_verifier_rejects_tampered_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'metadata.json').write_text('{}')
            runner.write_json(root / 'manifest.json', {'metadata.json': runner.digest(b'original')})
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                verifier.verify(root)

    def test_complete_dataset_is_recomputed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            result = verifier.verify(root)
            self.assertEqual(result['verified_runs'], 2)
            self.assertEqual(result['median_initial_p95_ms'], {'off': 20, 'on': 20})

    def test_missing_raw_request_is_rejected_even_with_updated_checksums(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            path = root / 'pair-01-off-requests.jsonl'
            path.write_text('\n'.join(path.read_text().splitlines()[1:]) + '\n')
            refresh_manifest(root)
            with self.assertRaisesRegex(ValueError, 'Missing or reordered requests'):
                verifier.verify(root)

    def test_wrong_aggregate_is_rejected_even_if_both_summaries_agree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            summary = verifier.read(root / 'summary.json')
            summary['runs'][0]['initial']['p95_ms'] = 1
            runner.write_json(root / 'summary.json', summary)
            runner.write_json(root / 'pair-01-off-summary.json', summary['runs'][0])
            refresh_manifest(root)
            with self.assertRaisesRegex(ValueError, 'Summary cannot be reproduced'):
                verifier.verify(root)


if __name__ == '__main__':
    unittest.main()
