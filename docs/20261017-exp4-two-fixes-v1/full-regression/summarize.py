"""Summarize fresh evidence for the fixed working tree; never import earlier results."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
suites = []
for file in sorted((root / 'backend-full-xml').glob('TEST-*.xml')):
    suite = ET.parse(file).getroot()
    suites.append({
        'name': suite.attrib['name'],
        **{key: int(suite.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')},
        'timeSeconds': float(suite.attrib.get('time', 0)),
        'evidence': str(file.relative_to(root)),
    })
summary = {
    'run': json.loads((root / 'full-results.json').read_text(encoding='utf-8-sig')),
    'artifacts': json.loads((root / 'runtime-artifacts.json').read_text(encoding='utf-8-sig')),
    'backend': {
        'suiteCount': len(suites),
        **{key: sum(suite[key] for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')},
        'suites': suites,
    },
    'browserStages': {},
    'notes': [
        'This run tests the current uncommitted US07 upload-policy and BOPPPS copy fixes. The base Git commit alone does not identify this fixed artifact.',
        'Sprint1 uses real Chrome and real HTTP/JWT/Spring/isolated MySQL. Requests are relayed to the ephemeral Spring test server; responses are not simulated.',
        'Sprint3 and supplement use real Chrome against the newly packaged JAR, isolated MySQL and Redis.',
        'Resource race regression uses Playwright route mocks. Real resource upload/versioning/revocation is separately checked in the Sprint3 stage.',
        'The default 24-hour release delay is checked in the isolated database, then time is advanced for downstream UI checks; it is not a wall-clock 24-hour wait.',
        'All accounts, PDF and H264 media in this run are synthetic fixtures.',
    ],
}
for name, relative in {
    'sprint3RealBackend': 'sprint3-browser/browser-results.json',
    'resourceRacesWithMockedNetwork': 'resource-browser/results.json',
    'supplementRealBackend': 'supplement/results.json',
}.items():
    file = root / relative
    if file.exists():
        data = json.loads(file.read_text(encoding='utf-8-sig'))
        summary['browserStages'][name] = {
            'passedGroups': len(data.get('proof', [])),
            'pageErrors': data.get('pageErrors', []),
            'proof': data.get('proof', []),
            'evidence': relative,
            'browserVersion': data.get('browserVersion'),
        }
performance = root / 'legacy-browser/us0406-query-performance.json'
if performance.exists():
    summary['sprint1Performance'] = json.loads(performance.read_text(encoding='utf-8-sig'))
cleanup = root / 'cleanup-verification.json'
if cleanup.exists():
    summary['cleanup'] = json.loads(cleanup.read_text(encoding='utf-8-sig'))
(root / 'verification-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({
    'backend': {key: value for key, value in summary['backend'].items() if key != 'suites'},
    'browserStages': {name: {'passedGroups': value['passedGroups'], 'pageErrors': value['pageErrors']} for name, value in summary['browserStages'].items()},
}, ensure_ascii=False, indent=2))
