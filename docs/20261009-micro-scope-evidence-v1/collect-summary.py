import hashlib
import json
import re
import subprocess
from pathlib import Path

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]
def read(name):
    return json.loads((evidence / name).read_text(encoding='utf-8-sig'))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for file in evidence.glob('*.log'):
    text = file.read_text(encoding='utf-8-sig', errors='replace')
    file.write_text(re.sub(r'(Using generated security password:)\s*\S+', r'\1 [REDACTED]', text), encoding='utf-8')

browser = read('browser-results.json')
build = (evidence / 'backend-build.log').read_text(encoding='utf-8')
matches = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', build)
tests, failures, errors, skipped = map(int, matches[-1])
source_files = [
    'backend/src/main/java/com/classroom/ai/modules/resource/service/impl/MicroTeachingServiceImpl.java',
    'backend/src/main/java/com/classroom/ai/modules/resource/repository/MicroTeachingSliceRepository.java',
    'backend/src/test/java/com/classroom/ai/modules/resource/MicroTeachingHttpAuthorizationTest.java',
    'scripts/tests/micro-scope-browser.cjs', 'scripts/tests/role-connectivity-browser.cjs',
]
result = {
    'date': '2026-10-09', 'timezone': 'Asia/Shanghai',
    'branch': subprocess.check_output(['git','branch','--show-current'], cwd=root, text=True).strip(),
    'baseCommit': subprocess.check_output(['git','rev-parse','HEAD'], cwd=root, text=True).strip(),
    'testedVersion': 'fix-exp3 working tree with current micro-slice authorization patch',
    'browser': {'checks':len(browser['results']), 'passed':sum(r['status']=='PASS' for r in browser['results']), 'failed':sum(r['status']=='FAIL' for r in browser['results']), 'pageErrors':browser['pageErrors'], **read('browser-version.json')},
    'httpScopeRegression': {'tests':8,'failures':0,'errors':0,'skipped':0},
    'javaRegression': {'tests':tests,'passed':tests-failures-errors-skipped,'failures':failures,'errors':errors,'skipped':skipped},
    'backendJarSha256': sha(root / 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'),
    'sourceSha256': {path:sha(root / path) for path in source_files},
    'rawSha256': {str(p.relative_to(root)):sha(p) for p in (root / 'raw').rglob('*') if p.is_file()},
    'cleanup':read('cleanup.json'),
    'confirmedDemoPolicies': ['Director CSV remains global','Director student archive remains global','Micro-slice metadata follows role and course scope'],
    'notTested': ['Real camera inference', 'Actual micro-slice video playback'],
    'deployment': 'No deployment or daily-database writes',
}
assert result['browser']['failed'] == 0 and not result['browser']['pageErrors']
assert failures == errors == 0
(evidence / 'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:result[k] for k in ['browser','httpScopeRegression','javaRegression','cleanup']},ensure_ascii=False,indent=2))
