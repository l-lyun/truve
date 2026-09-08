#!/usr/bin/env python3
"""Starts two owned JVMs, kills A after durable claim, observes B recovering the work."""
import csv
import datetime
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid
import zipfile
from run_baseline import sql

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_MS = 60000
EVENTS = 20


def http(port, endpoint):
    with urllib.request.urlopen(f'http://localhost:{port}/actuator/{endpoint}', timeout=3) as response:
        return response.read().decode()


def wait_ready(process, port):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'JVM {port} exited: {process.returncode}')
        try:
            if json.loads(http(port, 'health/readiness'))['status'] == 'UP':
                return
        except (OSError, ValueError):
            pass
        time.sleep(.5)
    raise RuntimeError(f'JVM {port} readiness timeout')


def metric(data, name, label=None):
    values = [float(line.split('}')[1].strip().split()[0]) for line in data.splitlines()
              if line.startswith(name + '{') and (label is None or label in line)]
    if len(values) != 1:
        raise RuntimeError(f'Expected one series for {name}: {values}')
    return values[0]


def main():
    if not os.environ.get('MYSQL_PWD'):
        raise RuntimeError('Set MYSQL_PWD for the dedicated truve-pr12 database')
    os.chdir(ROOT)
    for port in (18084, 18085):
        with socket.socket() as check:
            if check.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} occupied; stop the identified existing Truve process first')
    jars = [p for p in (ROOT / 'ticketing/build/libs').glob('*.jar') if not p.name.endswith('-plain.jar')]
    if len(jars) != 1:
        raise RuntimeError('Build exactly one ticketing bootJar first')
    jar = jars[0]
    run_id = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6]
    folder = ROOT / 'performance/outbox/results' / ('claim-crash-' + run_id)
    folder.mkdir(parents=True)
    topic = 'truve.outbox.claimcrash.' + uuid.uuid4().hex
    marker = folder / 'claimed.csv'
    metadata = {'sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'topic': topic, 'events': EVENTS, 'claim_timeout_ms': TIMEOUT_MS,
                'recovery_scan_ms': 1000, 'relay_fixed_delay_ms': 1000,
                'time_basis': 'JVM Asia/Seoul, seed MySQL +09:00',
                'kill_point': 'after claim transaction commit, before Kafka send',
                'scope': 'local JVM process SIGKILL, real DB and Kafka, independent keys, no business Consumer',
                'test_overrides': 'claim timeout 60s vs default 300s, scan 1s vs 30s, poll 1s vs 3s'}
    (folder / 'metadata.json').write_text(json.dumps(metadata, indent=2))
    (folder / 'source.diff').write_bytes(subprocess.check_output(['git', 'diff', '--', 'ticketing/src/main']))
    gate_source = ROOT / 'ticketing/src/main/java/org/truve/platform/ticketing/service/booking/outbox/service/OutboxClaimFaultGate.java'
    (folder / 'OutboxClaimFaultGate.java.txt').write_bytes(gate_source.read_bytes())
    observations = []
    started = time.monotonic()

    def event(name, **details):
        item = {'event': name, 'elapsed_seconds': round(time.monotonic() - started, 3),
                'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), **details}
        observations.append(item)
        (folder / 'timeline.json').write_text(json.dumps(observations, indent=2))
        print(json.dumps(item), flush=True)

    def state():
        return sql(f"SELECT id,status,retry_count,HEX(claim_token),claimed_at,published_at,message_key FROM ticketing_outbox_events WHERE topic='{topic}' ORDER BY id")

    env = os.environ.copy()
    env.update({'TICKETING_MYSQL_URL': 'jdbc:mysql://localhost:23306/ticketing_db?allowPublicKeyRetrieval=true&useSSL=false',
                'TICKETING_MYSQL_USERNAME': 'root', 'TICKETING_MYSQL_PASSWORD': os.environ['MYSQL_PWD'],
                'USER_REDIS_HOST': 'localhost', 'USER_REDIS_PORT': '26379', 'KAFKA_SERVERS': 'localhost:29094',
                'SPRING_JPA_SHOW_SQL': 'false', 'TICKETING_OUTBOX_CLAIM_ENABLED': 'true'})
    common = ['java', '-Duser.timezone=Asia/Seoul', '-jar', str(jar),
              f'--ticketing.outbox.claim-timeout-ms={TIMEOUT_MS}',
              '--ticketing.outbox.claim-recovery-delay-ms=1000',
              '--ticketing.outbox.relay.fixed-delay-ms=1000',
              '--ticketing.outbox.cleanup.cron=-', '--spring.kafka.listener.auto-startup=false']
    a = b = None
    try:
        with tempfile.TemporaryDirectory(prefix='truve-kafka-probe-') as probe_dir:
            libraries = []
            with zipfile.ZipFile(jar) as archive:
                for entry in archive.namelist():
                    if entry.startswith('BOOT-INF/lib/') and Path(entry).name.startswith(('kafka-clients-', 'slf4j-api-', 'lz4-java-', 'snappy-java-', 'zstd-jni-')):
                        path = Path(probe_dir) / Path(entry).name
                        path.write_bytes(archive.read(entry))
                        libraries.append(str(path))

            def probe(mode):
                result = subprocess.run(['java', '--class-path', os.pathsep.join(libraries),
                                         str(ROOT / 'performance/outbox/KafkaProbe.java'), mode, topic],
                                        capture_output=True, text=True, timeout=60)
                (folder / f'kafka-{mode}.txt').write_text(result.stdout)
                (folder / f'kafka-{mode}-stderr.txt').write_text(result.stderr)
                result.check_returncode()
                return result.stdout.strip()

            probe('prepare')
            with (folder / 'relay-a.log').open('w') as log_a:
                a = subprocess.Popen(common + ['--server.port=18085', '--spring.profiles.active=local,outbox-fault-test',
                                               f'--ticketing.outbox.fault.marker-file={marker}'], env=env,
                                     stdout=log_a, stderr=subprocess.STDOUT)
            wait_ready(a, 18085)
            event('a_ready', pid=a.pid)
            sql(f"""INSERT INTO ticketing_outbox_events(created_at,updated_at,event_type,message_key,payload,retry_count,status,topic)
                WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<{EVENTS})
                SELECT NOW(6),NOW(6),'CRASH',CONCAT('crash-',n),'{{}}',0,'PENDING','{topic}' FROM seq""")
            deadline = time.monotonic() + 30
            while not marker.exists() or len(marker.read_text().splitlines()) < EVENTS + 1:
                if a.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('A did not reach complete claim marker')
                time.sleep(.2)
            marked_at = time.monotonic()
            claimed = state()
            (folder / 'claimed-db.tsv').write_text(claimed)
            rows = [r.split('\t') for r in claimed.splitlines()]
            assert len(rows) == EVENTS and all(r[1] == 'PROCESSING' and r[5] == 'NULL' for r in rows)
            with marker.open() as source:
                marked_tokens = {r['id']: uuid.UUID(r['claimToken']).hex.upper() for r in csv.DictReader(source)}
            assert marked_tokens == {r[0]: r[3].upper() for r in rows}
            event('a_claim_committed_and_paused', rows=EVENTS)
            assert probe('end') == '0', 'Kafka already contains events before A termination'
            with (folder / 'relay-b.log').open('w') as log_b:
                b = subprocess.Popen(common + ['--server.port=18084', '--spring.profiles.active=local'],
                                     env=env, stdout=log_b, stderr=subprocess.STDOUT)
            wait_ready(b, 18084)
            assert time.monotonic() - marked_at < TIMEOUT_MS / 1000 - 5, 'B startup used up the claim lease'
            before_metrics = http(18084, 'prometheus')
            (folder / 'b-before.prom').write_text(before_metrics)
            assert all(r.split('\t')[1] == 'PROCESSING' for r in state().splitlines())
            event('b_ready_before_a_kill', pid=b.pid)
            killed = time.monotonic()
            a.kill()  # Only this script's own child, never a looked-up or user-supplied PID.
            a.wait(timeout=10)
            assert a.returncode == -9
            event('a_sigkill', returncode=a.returncode)
            (folder / 'after-kill-db.tsv').write_text(state())
            deadline = killed + TIMEOUT_MS / 1000 + 30
            while True:
                current = state()
                rows = [r.split('\t') for r in current.splitlines()]
                published = sum(r[1] == 'PUBLISHED' for r in rows)
                event('recovery_poll', published=published)
                if published == EVENTS:
                    break
                if b.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('B recovery deadline exceeded or JVM exited')
                time.sleep(1)
            recovered = time.monotonic()
            (folder / 'final-db.tsv').write_text(current)
            assert all(r[2] == '1' and r[3] == 'NULL' and r[5] != 'NULL' for r in rows)
            records = probe('read').splitlines()
            keys = [r.split('\t')[1] for r in records]
            assert len(records) == EVENTS and len(set(keys)) == EVENTS
            assert set(keys) == {r[6] for r in rows}
            after_metrics = http(18084, 'prometheus')
            (folder / 'b-after.prom').write_text(after_metrics)
            recovered_delta = metric(after_metrics, 'ticketing_outbox_claims_recovered_total') - metric(before_metrics, 'ticketing_outbox_claims_recovered_total')
            published_delta = metric(after_metrics, 'ticketing_outbox_completion_total', 'outcome="published"') - metric(before_metrics, 'ticketing_outbox_completion_total', 'outcome="published"')
            assert recovered_delta == published_delta == EVENTS
            summary = {'status': 'PASS', 'events': EVENTS, 'a_returncode': a.returncode,
                       'b_alive': b.poll() is None, 'published': EVENTS, 'kafka_records': len(records),
                       'kafka_unique_keys': len(set(keys)), 'missing': 0,
                       'b_recovered_counter_delta': recovered_delta, 'b_published_counter_delta': published_delta,
                       'kill_to_all_published_observed_seconds': round(recovered - killed, 3),
                       'claim_marker_to_all_published_observed_seconds': round(recovered - marked_at, 3),
                       'claim_timeout_ms': TIMEOUT_MS, 'b_pid': b.pid}
            (folder / 'summary.json').write_text(json.dumps(summary, indent=2))
            event('complete', **summary)
    except BaseException as error:
        event('failed', error=str(error))
        raise
    finally:
        if a is not None and a.poll() is None:
            a.terminate()
            try:
                a.wait(timeout=15)
            except subprocess.TimeoutExpired:
                a.kill()
                a.wait(timeout=10)
        if b is not None and b.poll() is None:
            b.terminate()
            try:
                b.wait(timeout=15)
            except subprocess.TimeoutExpired:
                b.kill()
                b.wait(timeout=10)
        print(f'Results: {folder}', flush=True)


if __name__ == '__main__':
    main()
