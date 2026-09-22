#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Real production runtime/configuration; no test framework or cloud credentials.
python3 - <<'PY'
import contextlib
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

root = Path.cwd()
libs = root / 'server/build/install/server/lib'
validation = root / 'server/build/libs/server-observability-validation.jar'
main = 'kr.co.cotton.vlrgg_mobile.observability.validation.ObservabilityValidationMainKt'
assert validation.is_file(), 'Build :server:observabilityValidationJar first'
assert list(libs.glob('*.jar')), 'Build :server:installDist first'
for jar in libs.glob('*.jar'):
    with zipfile.ZipFile(jar) as archive:
        assert not any('/observability/validation/' in name for name in archive.namelist()), 'Harness leaked into production'
with zipfile.ZipFile(validation) as archive:
    assert all(name.startswith(('META-INF/', 'kr/')) for name in archive.namelist())
    assert all('/observability/validation/' in name for name in archive.namelist() if name.endswith('.class'))

env = {k: v for k, v in os.environ.items() if not k.startswith(('K_', 'VLRGG_', 'GOOGLE_CLOUD_'))}
env.pop('SMOKE_ID_TOKEN', None)
env.update(GOOGLE_CLOUD_PROJECT='validation-project', K_REVISION='local-validation')
java = [str(Path(env['JAVA_HOME']) / 'bin/java')] if env.get('JAVA_HOME') else ['java']
command = java + ['-cp', f'{libs}/*:{validation}', main]
for guard in ({}, {'VLRGG_OBSERVABILITY_LOCAL': 'true', 'K_SERVICE': 'production'},
              {'K_SERVICE': 'vlrgg-query-check'},
              {'K_SERVICE': 'production', 'VLRGG_OBSERVABILITY_VALIDATION': 'true'}):
    result = subprocess.run(command, env=env | guard, capture_output=True, timeout=15)
    assert result.returncode != 0, 'Harness guard allowed unsafe startup'

def request(port, path, expected, method='GET', headers=None):
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', method=method, headers=headers or {})
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read()
        assert response.status == expected, f'{path}: expected {expected}, got {response.status}'
        assert b'OBSERVABILITY_RAW_SECRET_SENTINEL' not in body
        return json.loads(body)

@contextlib.contextmanager
def running(args, overrides, output, port):
    # Bind first so an unrelated local server cannot make a broken launch pass.
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))
    with open(output, 'wb') as log:
        process = subprocess.Popen(args, env=env | overrides | {'PORT': str(port)}, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                assert process.poll() is None, 'Server terminated before readiness'
                try:
                    if request(port, '/health', 200) == {'status': 'ok'}:
                        break
                except (OSError, AssertionError):
                    time.sleep(.1)
            else:
                raise AssertionError('Server readiness timed out')
            assert process.poll() is None
            yield
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

with tempfile.TemporaryDirectory(prefix='vlrgg-observability-') as directory:
    production_log = Path(directory) / 'production.log'
    with running([str(root / 'server/build/install/server/bin/server')], {}, production_log, 18080):
        subprocess.run(['bash', '.github/scripts/smoke-query-server.sh', '--local'], env=env, check=True)
        for path in ('internal', 'internal/other', 'parsing', 'upstream', 'expected'):
            request(18080, '/__observability/' + path, 404)
        for path in ('health/fail', 'health/restore', 'exit'):
            request(18080, '/__observability/' + path, 404, 'POST')

    output = Path(directory) / 'validation.log'
    with running(command, {'VLRGG_OBSERVABILITY_LOCAL': 'true'}, output, 18081):
        trace = '0123456789abcdef0123456789abcdef'
        request(18081, '/__observability/internal', 500, headers={'X-Cloud-Trace-Context': trace + '/1;o=1'})
        request(18081, '/__observability/internal/other', 500, headers={'X-Cloud-Trace-Context': 'invalid'})
        request(18081, '/__observability/parsing', 502)
        request(18081, '/__observability/upstream', 502)
        request(18081, '/__observability/expected', 400)
        request(18081, '/__observability/exit', 404, 'POST')
        request(18081, '/__observability/health/fail', 200, 'POST')
        assert request(18081, '/health', 503) == {'status': 'unavailable'}
        request(18081, '/__observability/health/restore', 200, 'POST')
        assert request(18081, '/health', 200) == {'status': 'ok'}
    raw = output.read_bytes()
    assert b'OBSERVABILITY_RAW_SECRET_SENTINEL' not in raw, 'Raw exception leaked'
    assert b'\x1b' not in raw, 'ANSI escape in stdout'
    events = []
    for line in raw.splitlines(keepends=True):
        if line.startswith(b'{'):
            assert len(line) <= 16384 and line.endswith(b'\n'), 'Invalid record size or terminator'
            events.append(json.loads(line))
        else:
            assert not line.lstrip().startswith((b'at ', b'Caused by:')), 'Automatic exception footer'
    assert len(events) == 5, f'Expected exactly five JSON events, got {len(events)}'
    errors = [event for event in events if event['severity'] == 'ERROR']
    assert len(errors) == 3
    for event in errors:
        assert event['@type'] == 'type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent'
        assert event['serviceContext'] == {'service': 'vlrgg-server-local', 'version': 'local-validation'}
        assert '\n\tat ' in event['message'], 'Missing Java-shaped safe stack'
    for event in events:
        assert 'logging.googleapis.com/spanId' not in event and 'logging.googleapis.com/trace_sampled' not in event
        if event['severity'] == 'WARN':
            assert '@type' not in event and '\n\tat ' not in event.get('message', '')
    assert events[0]['logging.googleapis.com/trace'] == f'projects/validation-project/traces/{trace}'
    assert all('logging.googleapis.com/trace' not in event for event in events[1:])
print('PASS packaged production isolation, harness guards, structured stdout, trace, and health controls')
PY
