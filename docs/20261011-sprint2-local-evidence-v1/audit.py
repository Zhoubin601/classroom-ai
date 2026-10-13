from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

evidence = Path(__file__).resolve().parent
root = evidence.parent.parent

def dump(name, value):
    (evidence / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

patterns = [
    re.compile(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'),
    re.compile(r'(?<=Using generated security password: )(?!\[REDACTED\])[^\r\n]+'),
    re.compile(r'(?i)(?<="password":")(?!\[REDACTED\])[^"\r\n]+'),
]
redactions = []
for file in evidence.rglob('*'):
    if not file.is_file() or file.suffix not in {'.log', '.txt', '.xml', '.json'}:
        continue
    content = file.read_text(encoding='utf-8-sig', errors='replace')
    count = 0
    for pattern in patterns:
        content, matches = pattern.subn('[REDACTED]', content)
        count += matches
    if count:
        file.write_text(content, encoding='utf-8')
        redactions.append({'file': str(file.relative_to(evidence)), 'matches': count})
if redactions:
    previous = evidence / 'redactions.json'
    saved = json.loads(previous.read_text(encoding='utf-8')) if previous.exists() else []
    dump('redactions.json', saved + redactions)

def counts(files):
    suites = []
    for file in files:
        node = ET.parse(file).getroot()
        record = {'name': node.attrib['name']}
        record.update({key: int(node.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')})
        record['passed'] = record['tests'] - record['failures'] - record['errors'] - record['skipped']
        suites.append(record)
    return {'total': {key: sum(s[key] for s in suites) for key in ('tests', 'failures', 'errors', 'skipped', 'passed')}, 'suites': suites}

ordinary = counts(sorted((evidence / 'backend-ordinary').glob('TEST-*.xml')))
mysql = counts([evidence / 'sprint2-mysql.xml'])
dump('backend-counts.json', {'ordinary': ordinary, 'sprint2_mysql': mysql,
    'deduplicated_passed': ordinary['total']['passed'] + mysql['total']['passed'],
    'remaining_environment_skipped': ordinary['total']['skipped'] - mysql['total']['passed']})

protected = json.loads((evidence / 'protected-before.json').read_text(encoding='utf-8-sig'))
protection = []
for record in protected:
    current = hashlib.sha256(Path(record['Path']).read_bytes()).hexdigest().upper()
    protection.append({'path': record['Path'], 'sha256_before': record['Hash'], 'sha256_after': current, 'unchanged': current == record['Hash']})
dump('protection.json', protection)
assert all(item['unchanged'] for item in protection)

tracked = subprocess.check_output(['git', 'ls-files', 'backend/src', 'frontend/src', 'vision', 'scripts'], cwd=root, encoding='utf-8').splitlines()
hashes = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in tracked if (root / name).is_file()}
dump('tested-source-sha256.json', hashes)
dump('build-sha256.json', {str(file.relative_to(root)): hashlib.sha256(file.read_bytes()).hexdigest() for file in [root / 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar', root / 'frontend/dist/index.html']})

print(json.dumps({'ordinary': ordinary['total'], 'mysql': mysql['total'], 'protected': all(item['unchanged'] for item in protection), 'redacted_files_this_run': len(redactions)}, ensure_ascii=False))
