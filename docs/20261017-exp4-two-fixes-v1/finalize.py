"""Verify fixed sources, actual test results and isolated-environment cleanup."""
import hashlib
import json
import re
import shutil
import subprocess
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]
def read(relative):
    return json.loads((evidence / relative).read_text(encoding='utf-8-sig'))
source = read('fixed-source.json')
runtime = read('runtime-artifacts.json')
frontend = read('frontend-exit.json')
sprint2 = read('sprint2-browser/results.json')
full = read('full-regression/verification-summary.json')
policy = read('policy-browser/retry-v5/results.json')
fixtures = read('policy-browser/retry-v5/fixture-results.json')
assert frontend['tests'] == 0 and frontend['build'] == 0
assert read('backend-package-exit.json')['exitCode'] == 0
assert len(sprint2) == 7 and set(sprint2.values()) == {'PASS'}
assert read('sprint2-exit.json')['exitCode'] == 0
assert full['backend']['tests'] == 294 and full['backend']['suiteCount'] == 41
assert all(full['backend'][key] == 0 for key in ('failures', 'errors', 'skipped'))
assert policy.get('completedAt') and not policy['pageErrors']
assert policy['businessAcceptanceStatus'] == 'PASS' and policy['probeExecutionStatus'] == 'PASS'
assert policy['passed'] == 14 and policy['failed'] == 0
assert all(case['status'] == 'PASS' for case in policy['proof'])
assert fixtures['mocked'] and fixtures['status'] == 'PASS' and not fixtures['pageErrors']
assert fixtures['passed'] == 3 and fixtures['failed'] == 0
for name in ('sprint3-browser/browser-results.json', 'resource-browser/results.json', 'supplement/results.json'):
    result = read('full-regression/' + name)
    assert not result.get('pageErrors'), name

changed_after_test = []
for name, expected in source['protectedAfterFix'].items():
    file = root / name
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != expected:
        changed_after_test.append(name)
assert not changed_after_test, changed_after_test
for name, expected in runtime['artifactsSha256'].items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected, name
original = Path('D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx')
original_sha = hashlib.sha256(original.read_bytes()).hexdigest()
assert original_sha == '299cd2d510e7a8da609ec2fe513e3810e616d3562673bce09ee16e81688437bb'
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip()
assert head == source['head']

generated = root / 'runtime/browser-tests/classroom-exp3-browser-c3877fefc0'
removed_runtime = False
if generated.exists():
    assert generated.resolve().parent == (root / 'runtime/browser-tests').resolve()
    assert not generated.is_symlink() and not any(file.is_symlink() for file in generated.rglob('*'))
    shutil.rmtree(generated)
    removed_runtime = True
redactions = 0
for file in evidence.rglob('*'):
    if not file.is_file() or file.suffix.lower() not in {'.log', '.xml', '.txt', '.html', '.json'}:
        continue
    raw = file.read_bytes()
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    text = raw.decode(encoding, errors='replace')
    cleaned = text
    for pattern, replacement in [
        (r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[REDACTED_TEST_TOKEN]'),
        (r'(?im)(Using generated security password:\s*)[^\s<]+', r'\1[REDACTED_TEST_PASSWORD]'),
        (r'(?im)(x-csrf-token:\s*)[^\r\n<]+', r'\1[REDACTED_TEST_CSRF]'),
        (r'(?i)(<property\s+name="[^"\n]*(?:password|secret|token)[^"\n]*"\s+value=")[^"\n]+', r'\1[REDACTED_TEST_VALUE]'),
    ]:
        cleaned, count = re.subn(pattern, replacement, cleaned)
        redactions += count
    if cleaned != text:
        file.write_text(cleaned, encoding='utf-8')
containers = subprocess.check_output(['docker', 'ps', '--format', '{{.Names}}'], text=True).splitlines()
test_containers = [name for name in containers if name.startswith(('classroom-exp3-browser-c3877fefc0', 'classroom-policy-fix-', 'exp4-20261017-fixed-'))]
ps = "$ports=@(13316,13317,13318,13319,13320,16318,16319,16320,18081,18082,18083,18089,18090,18091,15173,15179,15180,15181); @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $ports } | Select-Object -ExpandProperty LocalPort -Unique) | ConvertTo-Json -Compress"
port_text = subprocess.check_output(['powershell.exe', '-NoProfile', '-Command', ps], text=True).strip()
ports = json.loads(port_text) if port_text else []
assert not test_containers and not ports, (test_containers, ports)
summary = {
    'completedAt': datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),
    'basedOnCommit': head,
    'workingTreeFixNotCommitted': True,
    'protectedFilesAfterFix': len(source['protectedAfterFix']),
    'changedAfterTestingStarted': changed_after_test,
    'authorizedModifiedTrackedFiles': source['modifiedTrackedFiles'],
    'newSourceFiles': source['newSourceFiles'],
    'originalPlanSha256': original_sha,
    'frontendTests': 27,
    'backend': {key: full['backend'][key] for key in ('suiteCount', 'tests', 'failures', 'errors', 'skipped')},
    'sprint2FormalSuites': len(sprint2),
    'realPolicyCases': len(policy['proof']),
    'mockFaultInjectionCases': len(fixtures['proof']),
    'policyFinalEvidence': 'policy-browser/retry-v5/results.json',
    'policyInitialAttemptsPreserved': ['policy-browser/results.json', 'policy-browser/retry-v2/results.json', 'policy-browser/retry-v3/results.json', 'policy-browser/retry-v4/results.json'],
    'browserRegressionScriptSha256': hashlib.sha256((root / 'scripts/tests/resource-upload-policy-browser.cjs').read_bytes()).hexdigest(),
    'browser': runtime['browserVersion'],
    'resolvedDefects': ['US-07 primary upload ignores configured upper limit', 'BOPPPS labels confuse the four rating dimensions'],
    'removedOnlyThisRunGeneratedRuntime': removed_runtime,
    'additionalEvidenceRedactions': redactions,
    'remainingTestContainers': test_containers,
    'listeningTestPorts': ports,
    'dailyContainers': [name for name in containers if name in {'classroom-backend', 'classroom-mysql', 'classroom-redis'}],
}
(evidence / 'verification-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({key: summary[key] for key in ('protectedFilesAfterFix', 'changedAfterTestingStarted', 'frontendTests', 'backend', 'sprint2FormalSuites', 'realPolicyCases', 'mockFaultInjectionCases', 'remainingTestContainers', 'listeningTestPorts')}))
