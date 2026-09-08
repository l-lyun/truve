#!/usr/bin/env python3
"""Bounded local Relay baseline. Only writes to the dedicated truve-pr12 MySQL container."""
import argparse
import csv
import datetime
import json
import math
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid


def command(args):
    return subprocess.check_output(args, text=True).strip()


def sql(query):
    # Password comes from the environment, not command-line arguments or result files.
    return command(['docker', 'exec', '-e', 'MYSQL_PWD', 'truve-pr12-mysql',
                    'mysql', '-uroot', '--batch', '--skip-column-names', 'ticketing_db', '-e',
                    "SET SESSION time_zone='+09:00'; " + query])


def snapshot(path):
    with urllib.request.urlopen('http://localhost:18084/actuator/prometheus', timeout=10) as response:
        data = response.read()
        path.write_bytes(data)
        for line in data.decode().splitlines():
            if line.startswith('ticketing_outbox_completion_total{') and 'outcome="published"' in line:
                return float(line.split('}')[1].strip().split()[0])
        raise RuntimeError('Published completion metric missing; restart instrumented app')


def percentile(values, percent):
    return sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--events', type=int, default=1000)
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--timeout', type=int, help='seconds; default scales with event count')
    args = parser.parse_args()
    if not 1 <= args.events <= 10000 or not 1 <= args.repeats <= 10:
        parser.error('events must be 1..10000 and repeats 1..10')
    if args.timeout is None:
        args.timeout = max(180, math.ceil(args.events / 100) * 6 + 30)
    if args.timeout <= 0:
        parser.error('timeout must be positive')
    if not os.environ.get('MYSQL_PWD'):
        parser.error('Set MYSQL_PWD to the dedicated local database password')
    root = Path(__file__).resolve().parents[2]
    os.chdir(root)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder = root / 'performance/outbox/results' / (stamp + '-' + uuid.uuid4().hex[:6])
    folder.mkdir(parents=True)
    metadata = {
        'sha': command(['git', 'rev-parse', 'HEAD']),
        'working_tree': command(['git', 'status', '--short']),
        'events': args.events, 'repeats': args.repeats, 'relay_instances': 1,
        'poll_delay_ms': 3000, 'pending_batch_limit': 100,
        'payload': '{}', 'key_distribution': 'unique key per event; no same-key ordering experiment',
        'scope': 'normal-operation DB seed -> real Relay -> Kafka ack -> DB PUBLISHED; no business Consumer',
        'resources': 'host JVM and shared local Docker; no fixed CPU/memory limit; exploratory baseline',
        'percentile': 'nearest rank over per-event publication-record timestamp lag, not exact commit or consumer latency',
        'time_basis': 'JVM Asia/Seoul; seed MySQL session +09:00; clocks share local host',
        'java': subprocess.run(['java', '-version'], capture_output=True, text=True).stderr,
        'container_images': command(['docker', 'inspect', 'truve-pr12-mysql', 'truve-pr12-kafka',
                                     '--format', '{{.Name}} {{.Image}}']).splitlines(),
    }
    (folder / 'metadata.json').write_text(json.dumps(metadata, indent=2, ensure_ascii=False))
    (folder / 'source.diff').write_text(command(['git', 'diff', '--', 'ticketing/src/main']))
    summaries = []
    for run in range(1, args.repeats + 1):
        topic = 'truve.outbox.baseline.' + uuid.uuid4().hex
        before_count = snapshot(folder / f'run-{run}-before.prom')
        started = time.monotonic()
        sql(f"""SET SESSION cte_max_recursion_depth=10001;
            INSERT INTO ticketing_outbox_events
            (created_at,updated_at,event_type,message_key,payload,retry_count,status,topic)
            WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<{args.events})
            SELECT NOW(6),NOW(6),'BASELINE',CONCAT('event-',n),'{{}}',0,'PENDING','{topic}' FROM seq;""")
        observations = []
        while True:
            counts = sql(f"SELECT status,COUNT(*) FROM ticketing_outbox_events WHERE topic='{topic}' GROUP BY status")
            states = dict((s, int(n)) for s, n in (row.split('\t') for row in counts.splitlines()))
            elapsed = time.monotonic() - started
            observations.append({'elapsed_seconds': elapsed, 'states': states})
            if states.get('PUBLISHED', 0) == args.events:
                break
            if elapsed > args.timeout:
                (folder / f'run-{run}-timeout.json').write_text(json.dumps(observations, indent=2))
                raise RuntimeError(f'Run {run} timed out: {states}; evidence at {folder}')
            time.sleep(1)
        raw = sql(f"""SELECT id,message_key,status,retry_count,created_at,published_at,
            TIMESTAMPDIFF(MICROSECOND,created_at,published_at)/1000.0
            FROM ticketing_outbox_events WHERE topic='{topic}' ORDER BY id""")
        rows = [line.split('\t') for line in raw.splitlines()]
        with (folder / f'run-{run}-events.csv').open('w', newline='') as output:
            writer = csv.writer(output)
            writer.writerow(['id','key','status','retry_count','created_at','published_at','publication_record_lag_ms'])
            writer.writerows(rows)
        (folder / f'run-{run}-polling.json').write_text(json.dumps(observations, indent=2))
        after_count = snapshot(folder / f'run-{run}-after.prom')
        if after_count - before_count != args.events:
            raise RuntimeError('Committed published metric delta differs from row count')
        lags = [float(row[6]) for row in rows]
        if len(rows) != args.events or min(lags) < 0:
            raise RuntimeError('Row count or timestamp invariant failed; inspect raw results')
        summary = {'run': run, 'topic': topic, 'published': len(rows),
                   'committed_published_metric_delta': after_count - before_count,
                   'remaining': args.events - len(rows), 'retries': sum(int(row[3]) for row in rows),
                   'observed_drain_seconds': round(elapsed, 3),
                   'observed_events_per_second': round(args.events / elapsed, 2),
                   'publication_record_lag_p50_ms': percentile(lags, 50),
                   'publication_record_lag_p95_ms': percentile(lags, 95),
                   'publication_record_lag_p99_ms': percentile(lags, 99),
                   'publication_record_lag_max_ms': max(lags)}
        summaries.append(summary)
        (folder / 'summary.json').write_text(json.dumps(summaries, indent=2))
        print(json.dumps(summary), flush=True)
    print(f'Results: {folder}', flush=True)


if __name__ == '__main__':
    main()
