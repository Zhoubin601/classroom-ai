"""Linux adaptation of the existing PowerShell acceptance launchers.

Uses the current rebuilt JAR and only this run's disposable MySQL/Redis.
Keeps service processes alive for the complete acceptance run.
"""
import os
import re
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

ROOT = Path('/workspace/classroom-ai')
EVIDENCE = ROOT / 'docs/fix-exp3-evidence-20261006-v1'
RUN = ROOT / 'runtime/fix-exp3-20261006-v1/managed'
RUN.mkdir(parents=True, exist_ok=True)
processes = []
handles = []

def start(args, cwd, env, name):
    output = open(RUN / (name + '.log'), 'w')
    handles.append(output)
    proc = subprocess.Popen(args, cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT,
                            start_new_session=True)
    processes.append(proc)
    return proc

def ready(port, proc):
    for _ in range(90):
        if proc.poll() is not None:
            raise RuntimeError(f'Service on {port} exited {proc.returncode}')
        try:
            route = '/' if port == 15173 else '/api/v1/auth/csrf'
            with urllib.request.urlopen(f'http://127.0.0.1:{port}{route}', timeout=2) as r:
                if r.status == 200:
                    print(f'READY {port}', flush=True)
                    return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError(f'Service {port} not ready')

def test(script, logfile, env):
    proc = subprocess.Popen(['node', str(EVIDENCE / script)], cwd=ROOT, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    processes.append(proc)
    with open(EVIDENCE / logfile, 'w') as out:
        for line in proc.stdout:
            line = re.sub(r'(?i)(authorization: Bearer )\S+', r'\1[REDACTED]', line)
            line = re.sub(r'(?i)(x-csrf-token: )\S+', r'\1[REDACTED]', line)
            out.write(line)
            print(line, end='', flush=True)
    if proc.wait() != 0:
        raise RuntimeError(f'{script} failed, see {logfile}')

try:
    env = os.environ.copy()
    env.update(SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13318/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
               SPRING_DATASOURCE_USERNAME='root', SPRING_DATASOURCE_PASSWORD='root',
               SPRING_DATA_REDIS_HOST='127.0.0.1', SPRING_DATA_REDIS_PORT='16379',
               SPRING_DATA_REDIS_PASSWORD='', APP_SEED_DEMO='false', CLASSROOM_SOFFICE=shutil.which('soffice'))
    java = '/workspace/.classroom-tools/jdk-21/bin/java'
    jar = str(ROOT / 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar')
    backend = start([java,'-Xmx512m','-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.port=18081'],RUN,env,'backend')
    ready(18081,backend)
    expired = start([java,'-Xmx512m','-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.port=18082',
                     '--classroom.resource.preview-minutes=0'],RUN,env,'expired-backend')
    ready(18082,expired)
    frontend_env = os.environ.copy()
    frontend_env['CLASSROOM_API_PROXY']='http://127.0.0.1:18081'
    frontend = start(['npm','run','preview','--','--host','127.0.0.1','--port','15173','--strictPort'],
                     ROOT / 'frontend',frontend_env,'frontend')
    ready(15173,frontend)
    browser_env = os.environ.copy()
    browser_env.update(FIX_EXP3_EVIDENCE_DIR=str(EVIDENCE),EXP3_FRONTEND_URL='http://127.0.0.1:15173',EXP3_BACKEND_URL='http://127.0.0.1:18081',
                       EXP3_EXPIRED_BACKEND_URL='http://127.0.0.1:18082',EXP3_CHROMIUM_PATH='/usr/bin/chromium',
                       EXP3_TEST_MYSQL_CONTAINER='classroom-exp3-browser-a610060002',
                       PLAYWRIGHT_MODULE='/opt/codex/cua_node/lib/node_modules/playwright')
    if not os.environ.get('RECHECK_SUPPLEMENT_ONLY'):
        test('exp3-real-browser.snapshot.cjs','browser.log',browser_env)
    test(str(ROOT / 'scripts/tests/fix-exp3-acceptance.cjs'),'acceptance.log',browser_env)
finally:
    import signal
    for proc in reversed(processes):
        if proc.poll() is None:
            try:
                if proc in [locals().get('backend'),locals().get('expired'),locals().get('frontend')]:
                    os.killpg(proc.pid,signal.SIGTERM)
                else:
                    proc.terminate()
                proc.wait(timeout=15)
            except (ProcessLookupError,subprocess.TimeoutExpired):
                proc.kill()
    for handle in handles:
        handle.close()
