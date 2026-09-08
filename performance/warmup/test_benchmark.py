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


if __name__ == '__main__':
    unittest.main()
