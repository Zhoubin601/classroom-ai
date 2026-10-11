"""Collect this run's evidence without retaining authentication credentials."""
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]

def read_json(name):
    return json.loads((evidence / name).read_text(encoding='utf-8-sig'))

def sha256(file):
    return hashlib.sha256(Path(file).read_bytes()).hexdigest().upper()

# Spring tests can print a generated security password; retain only the event.
for log in evidence.glob('*.log'):
    text = log.read_text(encoding='utf-8-sig', errors='replace')
    text = re.sub(r'(Using generated security password:)\s*\S+', r'\1 [REDACTED]', text)
    log.write_text(text, encoding='utf-8')

groups = {}
errors = []
for label, filename in {
    'fullBrowser': 'all-features-proxy/results.json',
    'roleBoundaries': 'boundaries/results.json',
    'fixRegression': 'fix-results.json',
    'office': 'office-strict/fix-acceptance-results.json',
    'microScope': 'micro-scope/browser-results.json',
}.items():
    data = read_json(filename)
    rows = data if isinstance(data, list) else data['results']
    groups[label] = {
        'checks': len(rows),
        'passed': sum(r.get('status', r.get('result')) == 'PASS' for r in rows),
        'failed': sum(r.get('status', r.get('result')) == 'FAIL' for r in rows),
        'evidence': filename,
    }
    if isinstance(data, dict):
        groups[label]['completed'] = data.get('completed', True)
        errors.extend(data.get('pageErrors', []))

java = dict(tests=0, failures=0, errors=0, skipped=0)
for file in (root / 'backend/target/surefire-reports').glob('TEST-*.xml'):
    suite = ET.parse(file).getroot()
    for key in java:
        java[key] += int(suite.get(key, '0'))
java['passed'] = java['tests'] - java['failures'] - java['errors'] - java['skipped']

before = read_json('sources-before.json')
changed = []
for file in before:
    path = Path(file['path'])
    if not path.exists() or sha256(path) != file['sha256']:
        changed.append(str(path))
raw_changed = [p for p in changed if Path(p).is_relative_to(root / 'raw')]
attachments = []
for name in ['1.png', '2.png']:
    file = Path('C:/Users/a3185/Desktop') / name
    attachments.append(dict(name=name, sha256=sha256(file), exists=file.exists()))

summary = {
    'date': '2026-10-09', 'timezone': 'Asia/Shanghai',
    'collectedAt': datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'testedVersion': 'Current working tree with preexisting uncommitted changes; freshly rebuilt backend JAR and Vue production bundle',
    'backendJarSha256': sha256(root / 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'),
    'transport': 'Real Chrome / Playwright through real Vite proxy / Spring Boot / LibreOffice / isolated MySQL 8.0.36 and Redis',
    'groups': groups, 'java': java, 'pageErrors': errors,
    'changedSourceFiles': changed, 'sourceUnchanged': not changed, 'rawUnchanged': not raw_changed,
    'screenshots': len(list(evidence.rglob('*.png'))),
    'nativeCameraSender': read_json('vision-transport-results.json'),
    'cleanup': read_json('cleanup.json'),
    'suiteExitCodes': read_json('run-results.json'),
    'attachments': attachments,
    'confirmedDemoPolicies': ['Director CSV includes other departments', 'Director student archive is global', 'Micro-slice endpoints enforce confirmed course scope; supervisor read only'],
    'dailyReadonly': read_json('daily-readonly.json'),
    'dailyBackendJarSha256': subprocess.check_output(['docker','exec','classroom-backend','sha256sum','/app/app.jar'],text=True).split()[0].upper(),
    'radarRenderedProof': json.loads((root/'docs/20261009-radar-proof-evidence-v1/results.json').read_text(encoding='utf-8')),
    'radarProofCleanup': json.loads((root/'docs/20261009-radar-proof-evidence-v1/cleanup.json').read_text(encoding='utf-8-sig')),
    'notTested': ['Live camera / GPU inference accuracy', 'Real micro-slice video playback', 'Wall-clock 24-hour wait', 'Full business-write regression on daily 8080 deployment; daily instance only checked read-only and not replaced'],
}
(evidence / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({k: summary[k] for k in ['groups', 'java', 'screenshots', 'sourceUnchanged', 'rawUnchanged', 'pageErrors']}, ensure_ascii=False, indent=2))
