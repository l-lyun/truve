"""Pure metric parsing and counter-delta checks; no Docker/JVM side effects."""
import unittest
from unittest.mock import patch

from run_matrix import db_delta, ensure_host_headroom, metric


class MeasurementHelpersTest(unittest.TestCase):
    def test_host_overload_rejected(self):
        with patch('run_matrix.os.getloadavg', return_value=(268, 180, 150)), patch('run_matrix.os.cpu_count', return_value=8):
            with self.assertRaises(RuntimeError):
                ensure_host_headroom()

    def test_host_guard_does_not_claim_isolation(self):
        with patch('run_matrix.os.getloadavg', return_value=(2, 2, 2)), patch('run_matrix.os.cpu_count', return_value=8):
            self.assertEqual(8, ensure_host_headroom()['logical_cpus'])

    def test_counter_label_selection(self):
        data = 'count{outcome="failed"} 0\ncount{outcome="published"} 1000\n'
        self.assertEqual(1000, metric(data, 'count', 'outcome="published"'))

    def test_missing_metric_is_not_zero(self):
        with self.assertRaises(RuntimeError):
            metric('', 'count')

    def test_ambiguous_series_rejected(self):
        with self.assertRaises(RuntimeError):
            metric('count{a="1"} 1\ncount{a="2"} 2', 'count')

    def test_cpu_and_statement_units(self):
        before = dict(digest='x', claim_queries=20, claim_wait_ps=100,
                      mysql_cpu_usec=1000000, monotonic=10)
        after = dict(digest='x', claim_queries=80, claim_wait_ps=2000000000100,
                     mysql_cpu_usec=1300000, monotonic=40)
        result = db_delta(before, after)
        self.assertEqual(2, result['claim_queries_per_second'])
        self.assertEqual(2, result['claim_statement_seconds'])
        self.assertEqual(.3, result['mysql_cpu_seconds'])
        self.assertEqual(1, result['mysql_cpu_percent_one_core'])

    def test_counter_reset_rejected(self):
        before = dict(digest='x', claim_queries=20, claim_wait_ps=100,
                      mysql_cpu_usec=1000000, monotonic=10)
        after = dict(before, claim_queries=0, monotonic=40)
        with self.assertRaises(RuntimeError):
            db_delta(before, after)

    def test_digest_change_rejected(self):
        with self.assertRaises(RuntimeError):
            db_delta({'digest': 'a'}, {'digest': 'b'})


if __name__ == '__main__':
    unittest.main()
