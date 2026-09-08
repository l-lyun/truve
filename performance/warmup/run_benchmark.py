#!/usr/bin/env python3
"""Isolated single-JVM warmup comparison. Run --help for prerequisites and examples."""

import argparse
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
TABLES = ('show_scheduled', 'seat_section', 'seat', 'scheduled_seat',
          'reservations', 'tickets', 'ticketing_outbox_events', 'booking_bot_risk_summary')
USER_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def command(args, *, data=None, timeout=60):
    result = subprocess.run(args, input=data, text=True, capture_output=True, timeout=timeout, cwd=ROOT)
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed: {result.stderr[-1500:]}')
    return result.stdout.strip()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def host_snapshot():
    return {'load': list(os.getloadavg()), 'logical_cpus': os.cpu_count() or 1}


def check_host():
    snapshot = host_snapshot()
    if snapshot['load'][0] > snapshot['logical_cpus'] * 2:
        raise RuntimeError(f'Host load exceeds 2 x logical CPUs: {snapshot}')
    return snapshot


def order_for_pairs(pairs):
    return [(pair, mode) for pair in range(1, pairs + 1)
            for mode in (('off', 'on') if pair % 2 else ('on', 'off'))]


def percentile(values, percent):
    return sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)]


def summarize(records):
    valid = [r['latency_ms'] for r in records if r['valid']]
    scheduled = [r['schedule_to_finish_ms'] for r in records if r['valid']]
    return {'count': len(records), 'errors': sum(not r['valid'] for r in records),
            'first_ms': records[0]['latency_ms'],
            'p50_ms': percentile(valid, 50) if valid else None,
            'p95_ms': percentile(valid, 95) if valid else None,
            'p99_ms': percentile(valid, 99) if valid else None,
            'scheduled_p95_ms': percentile(scheduled, 95) if scheduled else None,
            'scheduled_p99_ms': percentile(scheduled, 99) if scheduled else None,
            'max_ms': max(valid) if valid else None,
            'max_start_lag_ms': max(r['start_lag_ms'] for r in records),
            'completed_rps': len(records) / ((records[-1]['finished_ms'] - records[0]['scheduled_ms']) / 1000)}


def valid_response(body):
    try:
        result = json.loads(body)
        sections = result['data']['sections']
        seats = [seat for section in sections for row in section['rows'] for seat in row['seats']]
        return (result['code'] == 'ok' and len(sections) == 2
                and all(len(section['rows']) == 10 for section in sections)
                and all(len(row['seats']) == 10 for section in sections for row in section['rows'])
                and sorted(seat['scheduledSeatId'] for seat in seats) == list(range(1, 201))
                and all(seat['status'] == 'AVAILABLE' for seat in seats))
    except (ValueError, KeyError, TypeError):
        return False


def stop_process(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


class Infrastructure:
    """Only remove containers created by this invocation; never use existing databases."""

    def __init__(self):
        self.created = []
        self.run_id = uuid.uuid4().hex
        suffix = self.run_id[:12]
        self.mysql = 'truve-warmup-mysql-' + suffix
        self.redis = 'truve-warmup-redis-' + suffix

    def create(self, name, image, port, env=()):
        args = ['docker', 'run', '-d', '--name', name, '--label', 'truve.experiment=warmup',
                '--label', f'truve.warmup.run={self.run_id}',
                '-p', f'127.0.0.1::{port}']
        for value in env:
            args.extend(['-e', value])
        command(args + [image], timeout=180)
        self.created.append(name)
        return int(command(['docker', 'port', name, f'{port}/tcp']).split(':')[-1])

    def start(self):
        self.mysql_port = self.create(self.mysql, 'mysql:8.4', 3306,
                                      ('MYSQL_ALLOW_EMPTY_PASSWORD=yes', 'MYSQL_DATABASE=warmup_benchmark'))
        self.redis_port = self.create(self.redis, 'redis:7.4-alpine', 6379)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                self.sql('SELECT 1')
                if self.redis_command('PING') == 'PONG':
                    return
            except RuntimeError:
                pass
            time.sleep(1)
        raise RuntimeError('Dedicated MySQL/Redis startup timed out')

    def sql(self, sql):
        return command(['docker', 'exec', '-i', self.mysql, 'mysql', '-h127.0.0.1', '-uroot', '-N', '-B',
                        'warmup_benchmark'], data=sql, timeout=20)

    def redis_command(self, *args):
        return command(['docker', 'exec', self.redis, 'redis-cli', '--raw', *args])

    def snapshot(self):
        return {table: {'count': int(self.sql(f'SELECT COUNT(*) FROM {table}')),
                        'sha256': digest(self.sql(f'SELECT * FROM {table} ORDER BY id').encode())}
                for table in TABLES}

    def session(self):
        token = uuid.uuid4().hex
        self.redis_command('SET', 'ticket:session:' + token,
                           json.dumps({'userId': USER_ID, 'showScheduleId': 1}), 'EX', '600')
        return token

    def clear_session(self, token):
        self.redis_command('DEL', 'ticket:session:' + token)
        self.redis_command('ZREM', 'ticket:active:1', token)

    def close(self):
        failures = []
        # Discover by this invocation's unique label even if docker run timed out after creation.
        owned = command(['docker', 'ps', '-aq', '--filter', f'label=truve.warmup.run={self.run_id}']).split()
        for container_id in owned:
            try:
                command(['docker', 'rm', '-f', '-v', container_id])
            except (RuntimeError, subprocess.TimeoutExpired) as exc:
                failures.append(str(exc))
        if failures:
            raise RuntimeError('Owned-container cleanup failed: ' + '; '.join(failures))


class Application:
    def __init__(self, args, infra, output, name, mode, *, bootstrap=False):
        if digest(args.jar.read_bytes()) != args.jar_sha256:
            raise RuntimeError('Executable JAR changed during the experiment')
        self.args, self.output, self.name = args, output, name
        self.samples = []
        self.done = threading.Event()
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            self.port = listener.getsockname()[1]
        self.log_path = output / f'{name}.log'
        self.log = self.log_path.open('w')
        # Do not inherit Spring/JVM overrides or credentials from unrelated local work.
        env = {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'TMPDIR', 'LANG') if key in os.environ}
        env.update({'TICKETING_MYSQL_URL': f'jdbc:mysql://127.0.0.1:{infra.mysql_port}/warmup_benchmark'
                    '?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=5000',
                    'TICKETING_MYSQL_USERNAME': 'root', 'TICKETING_MYSQL_PASSWORD': '',
                    'USER_REDIS_HOST': '127.0.0.1', 'USER_REDIS_PORT': str(infra.redis_port)})
        cmd = [args.java, '-Xms256m', '-Xmx512m', '-jar', str(args.jar),
               '--spring.profiles.active=warmup-benchmark', f'--server.port={self.port}',
               '--spring.jpa.hibernate.ddl-auto=' + ('create' if bootstrap else 'validate'),
               f'--ticketing.warmup.enabled={str(mode == "on").lower()}',
               '--ticketing.warmup.show-schedule-id=1', f'--ticketing.warmup.iterations={args.iterations}',
               '--ticketing.startup.gate-enabled=true', '--spring.main.banner-mode=off']
        self.started = time.monotonic()
        self.process = subprocess.Popen(cmd, cwd=ROOT, env=env, stdout=self.log, stderr=subprocess.STDOUT)
        self.sampler = threading.Thread(target=self.sample, daemon=True)
        self.sampler.start()

    def sample(self):
        while not self.done.is_set():
            record = {'elapsed_ms': (time.monotonic() - self.started) * 1000, **host_snapshot()}
            try:
                values = command(['ps', '-p', str(self.process.pid), '-o', '%cpu=', '-o', 'rss='], timeout=3).split()
                record.update(cpu_percent_lifetime=float(values[0]), rss_kib=int(values[1]))
            except (RuntimeError, ValueError, IndexError, subprocess.TimeoutExpired):
                record['process_sample_unavailable'] = True
            self.samples.append(record)
            self.done.wait(1)

    def ready(self):
        polls = []
        connection = http.client.HTTPConnection('127.0.0.1', self.port, timeout=1)
        try:
            while time.monotonic() - self.started < 120:
                check_host()
                if self.process.poll() is not None:
                    raise RuntimeError(f'{self.name} exited before readiness; inspect {self.log_path.name}')
                status = None
                try:
                    connection.request('GET', '/actuator/health/readiness')
                    response = connection.getresponse()
                    body = response.read()
                    status = response.status
                    if status == 200 and json.loads(body).get('status') == 'UP':
                        elapsed = (time.monotonic() - self.started) * 1000
                        polls.append({'elapsed_ms': elapsed, 'status': status})
                        return elapsed
                except (OSError, http.client.HTTPException, ValueError):
                    connection.close()
                polls.append({'elapsed_ms': (time.monotonic() - self.started) * 1000, 'status': status})
                time.sleep(.05)
            raise RuntimeError(f'{self.name} readiness exceeded 120 seconds')
        finally:
            connection.close()
            write_json(self.output / f'{self.name}-readiness.json', polls)

    def close(self):
        stop_process(self.process)
        self.done.set()
        self.sampler.join(timeout=5)
        self.log.close()
        write_json(self.output / f'{self.name}-resources.json', self.samples)


def measure_phase(app, connection, token, phase, count, rps, stream):
    records = []
    start = time.monotonic()
    for index in range(count):
        scheduled = start + index / rps
        delay = scheduled - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        check_host()
        before = time.monotonic()
        record = {'phase': phase, 'index': index,
                  'scheduled_ms': (scheduled - app.started) * 1000,
                  'started_ms': (before - app.started) * 1000,
                  'start_lag_ms': max(0, (before - scheduled) * 1000),
                  'connection_reused': connection.sock is not None, 'status': None, 'valid': False}
        try:
            if before - scheduled > 1:
                raise RuntimeError('Request start is over 1 second behind schedule')
            connection.request('GET', '/api/ticketing/1', headers={
                'X-User-Id': USER_ID, 'X-Session-Ticket': token, 'Accept': 'application/json'})
            response = connection.getresponse()
            body = response.read()
            finished = time.monotonic()
            record.update(status=response.status, valid=response.status == 200 and valid_response(body),
                          body_sha256=digest(body))
            if phase == 'initial' and index == 0:
                (app.output / f'{app.name}-response.json').write_bytes(body)
        except (OSError, http.client.HTTPException, RuntimeError) as exc:
            finished = time.monotonic()
            record['error'] = type(exc).__name__ + ': ' + str(exc)
        record.update(finished_ms=(finished - app.started) * 1000,
                      latency_ms=(finished - before) * 1000,
                      schedule_to_finish_ms=(finished - scheduled) * 1000)
        stream.write(json.dumps(record) + '\n')
        stream.flush()
        records.append(record)
        if not record['valid']:
            raise RuntimeError(f'{app.name} {phase} request {index} failed; raw record retained')
    return summarize(records)


def warmup_log(path, mode, iterations):
    text = path.read_text()
    completed = re.findall(r'웜업 완료\. iterations=(\d+), durationMs=(\d+)', text)
    if mode == 'on' and (len(completed) != 1 or int(completed[0][0]) != iterations):
        raise RuntimeError('Warmup completion missing or inconsistent')
    if mode == 'off' and completed:
        raise RuntimeError('OFF run unexpectedly warmed up')
    if re.search(r'"level"\s*:\s*"ERROR"', text):
        raise RuntimeError('Application ERROR logged; inspect raw log')
    return {'iterations': int(completed[0][0]), 'duration_ms': int(completed[0][1])} if completed else None


def preflight(args, infra, output):
    app = Application(args, infra, output, 'schema-bootstrap', 'off', bootstrap=True)
    try:
        app.ready()
    finally:
        app.close()
    infra.sql((Path(__file__).parent / 'fixture.sql').read_text())
    baseline = infra.snapshot()
    token = infra.session()
    key = 'ticket:session:' + token
    before = {'db': baseline, 'redis_keys': infra.redis_command('DBSIZE'),
              'session': infra.redis_command('GET', key), 'pttl': int(infra.redis_command('PTTL', key))}
    app = Application(args, infra, output, 'side-effect-check', 'on')
    try:
        app.ready()
    finally:
        app.close()
    after = {'db': infra.snapshot(), 'redis_keys': infra.redis_command('DBSIZE'),
             'session': infra.redis_command('GET', key), 'pttl': int(infra.redis_command('PTTL', key))}
    passed = (before['db'] == after['db'] and before['session'] == after['session']
              and before['redis_keys'] == after['redis_keys'] and 0 < after['pttl'] < before['pttl'])
    write_json(output / 'side-effects.json', {'before': before, 'after': after, 'passed': passed,
                                             'warmup': warmup_log(app.log_path, 'on', args.iterations)})
    if not passed:
        raise RuntimeError('Warmup side-effect check failed')
    infra.clear_session(token)
    return baseline


def run(args):
    args.jar = args.jar.resolve()
    if not args.jar.is_file():
        raise ValueError('Build ticketing:bootJar first and pass the executable JAR')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    args.jar_sha256 = digest(args.jar.read_bytes())
    metadata = {'protocol': 1, 'purpose': args.purpose, 'pairs': args.pairs, 'rps': args.rps,
                'initial_requests': args.initial_requests, 'steady_seconds': args.steady_seconds,
                'iterations': args.iterations, 'order': order_for_pairs(args.pairs),
                'jar_sha256': args.jar_sha256, 'source_head': command(['git', 'rev-parse', 'HEAD']),
                'jar_provenance': 'Supplied JAR hash; repository head recorded, JAR source is not inferred',
                'source_diff_sha256': digest(command(['git', 'diff', 'HEAD', '--', 'ticketing', 'performance/warmup']).encode()),
                'java': subprocess.run(
                    [args.java, '-version'], capture_output=True, text=True, check=True).stderr.strip(),
                'platform': platform.platform(), 'python': platform.python_version(), 'host': host_snapshot(),
                'heap': '-Xms256m -Xmx512m', 'readiness_poll_ms': 50, 'request_timeout_seconds': 5,
                'profile': 'warmup-benchmark', 'resource_sample_seconds': 1,
                'client': 'single-flight paced client; late requests catch up sequentially, never concurrent',
                'max_start_lag_ms': 1000,
                'limitations': ['Shared host; host-load guard is heuristic, not isolation proof',
                                'Fresh JVM, persistent dependency/OS caches; not cold DB',
                                'Direct Ticketing HTTP; excludes gateway JWT and Kafka workflows',
                                'Initial p99 uses only 100 requests; do not claim C2 or pure JIT causation',
                                'Read HTTP latency together with scheduled latency, including client backlog',
                                'CPU samples are ps lifetime averages; RSS peaks are sampled']}
    write_json(output / 'metadata.json', metadata)
    infra = Infrastructure()
    summaries = []
    try:
        check_host()
        infra.start()
        metadata['images'] = {name: json.loads(command(['docker', 'inspect', name]))[0]['Image']
                              for name in infra.created}
        write_json(output / 'metadata.json', metadata)
        baseline = preflight(args, infra, output)
        for pair, mode in order_for_pairs(args.pairs):
            host_before = check_host()
            name = f'pair-{pair:02d}-{mode}'
            token = infra.session()
            app = Application(args, infra, output, name, mode)
            connection = http.client.HTTPConnection('127.0.0.1', app.port, timeout=5)
            try:
                ready_ms = app.ready()
                with (output / f'{name}-requests.jsonl').open('w') as stream:
                    initial = measure_phase(app, connection, token, 'initial', args.initial_requests, args.rps, stream)
                    steady = measure_phase(app, connection, token, 'steady', args.steady_seconds * args.rps,
                                           args.rps, stream)
            finally:
                connection.close()
                app.close()
            after = infra.snapshot()
            infra.clear_session(token)
            if after != baseline:
                write_json(output / f'{name}-db-after.json', after)
                raise RuntimeError('Measurement mutated fixture, reservations, tickets or Outbox')
            summary = {'name': name, 'pair': pair, 'mode': mode, 'readiness_ms': ready_ms,
                       'warmup': warmup_log(app.log_path, mode, args.iterations),
                       'initial': initial, 'steady': steady, 'db_unchanged': True, 'db_after': after,
                       'host_before': host_before, 'host_after': check_host()}
            write_json(output / f'{name}-summary.json', summary)
            summaries.append(summary)
            print(f'{name}: ready={ready_ms:.1f}ms initial p95={initial["p95_ms"]:.2f}ms', flush=True)
        write_json(output / 'summary.json', {'status': 'completed', 'runs': summaries})
    except BaseException as exc:
        write_json(output / 'failure.json', {'type': type(exc).__name__, 'reason': str(exc),
                                            'completed_runs': len(summaries), 'host': host_snapshot()})
        raise
    finally:
        try:
            infra.close()
        except Exception as exc:
            write_json(output / 'cleanup-failure.json', {'reason': str(exc), 'owned': infra.created})
            raise
    write_json(output / 'manifest.json', {p.name: digest(p.read_bytes()) for p in sorted(output.iterdir())
                                          if p.is_file()})


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, epilog=(
        'Prerequisites: Docker, Java 21, Python 3.10+. Build: ./gradlew :ticketing:bootJar. '
        'Example: python3 performance/warmup/run_benchmark.py --jar ticketing/build/libs/ticketing-0.0.1-SNAPSHOT.jar '
        '--output /tmp/warmup-run. Containers use new loopback-only ports and are removed afterward. '
        'For a short validation use --purpose smoke --pairs 1 --initial-requests 10 --steady-seconds 2.'))
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java', default=shutil.which('java') or 'java')
    parser.add_argument('--purpose', choices=('comparison', 'smoke'), default='comparison')
    parser.add_argument('--pairs', type=int, default=5)
    parser.add_argument('--rps', type=int, default=10)
    parser.add_argument('--initial-requests', type=int, default=100)
    parser.add_argument('--steady-seconds', type=int, default=60)
    parser.add_argument('--iterations', type=int, default=100)
    args = parser.parse_args()
    if not (1 <= args.pairs <= 10 and 1 <= args.rps <= 100 and 1 <= args.initial_requests <= 1000
            and 1 <= args.steady_seconds <= 120 and 1 <= args.iterations <= 1000):
        parser.error('Counts and rates must be positive and within the documented local bounds')
    if args.purpose == 'comparison' and (args.pairs < 5 or args.initial_requests != 100 or args.steady_seconds != 60):
        parser.error('Comparison requires at least 5 pairs, 100 initial requests and 60 steady seconds')
    return args


if __name__ == '__main__':
    run(parse_args())
