"""Extract reviewable counts and test names without copying JVM/environment properties."""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

evidence = Path(__file__).resolve().parent
root = evidence.parent.parent
baseline = json.loads((evidence.parent / '20261010-attendance-test-import-check-v1/sources-before.json').read_text(encoding='utf-8-sig'))
expected_change = 'backend/src/test/java/com/classroom/ai/modules/course/AssociationMysqlTest.java'
changed = []
for entry in baseline['files']:
    path = root / entry['path']
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
        changed.append(entry['path'])
assert changed == [expected_change], changed

def parse_suite(path):
    suite = ET.parse(path).getroot()
    counts = {key: int(suite.attrib[key]) for key in ('tests', 'failures', 'errors', 'skipped')}
    counts['passed'] = counts['tests'] - counts['failures'] - counts['errors'] - counts['skipped']
    cases = []
    for case in suite.findall('testcase'):
        status = next((name for name in ('error', 'failure', 'skipped') if case.find(name) is not None), 'passed')
        cases.append({'name': case.attrib['name'], 'status': status})
    return {'suite': suite.attrib['name'], **counts, 'cases': cases}

targeted = parse_suite(evidence / 'mysql-targeted/TEST-com.classroom.ai.modules.course.AssociationMysqlTest.xml')
assert targeted['passed'] == 7 and targeted['skipped'] == 0, targeted
regression = [parse_suite(path) for path in sorted((evidence / 'backend-regression').glob('TEST-*.xml'))]
totals = {key: sum(suite[key] for suite in regression) for key in ('tests', 'passed', 'failures', 'errors', 'skipped')}
assert totals['tests'] > 0 and totals['failures'] == 0 and totals['errors'] == 0, totals
run_results = json.loads((evidence / 'run-results.json').read_text(encoding='utf-8-sig'))
assert len(run_results) == 2 and all(run['exitCode'] == 0 for run in run_results), run_results
cleanup = json.loads((evidence / 'cleanup.json').read_text(encoding='utf-8-sig'))
assert cleanup['temporaryMysqlRemoved'], cleanup
summary = {
    'at': datetime.now().astimezone().isoformat(),
    'branch': subprocess.check_output(['git', 'branch', '--show-current'], cwd=root, text=True).strip(),
    'parentCommit': baseline['head'],
    'fix': 'Add AttendanceAccessService to AssociationMysqlTest Spring @Import',
    'sourceSha256': hashlib.sha256((root / expected_change).read_bytes()).hexdigest(),
    'sourceVerification': {'checkedFiles': len(baseline['files']), 'changedFiles': changed, 'productionBackendSourceUnchanged': True},
    'mysql': {'version': '8.0.36', 'disposable': True, 'host': '127.0.0.1', 'port': 13317, 'database': 'exp3_test'},
    'targeted': targeted,
    'defaultBackendRegression': {'totals': totals, 'suites': regression},
    'runResults': run_results,
    'cleanup': cleanup,
}
(evidence / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'targeted': {key: targeted[key] for key in ('tests', 'passed', 'failures', 'errors', 'skipped')}, 'regression': totals, 'changedFiles': changed}, ensure_ascii=False))
