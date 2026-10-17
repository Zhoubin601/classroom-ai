"""Build an explicit publication scope without staging unrelated local material."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]
fixed = json.loads((evidence / 'fixed-source.json').read_text(encoding='utf-8'))
verification = json.loads((evidence / 'verification-summary.json').read_text(encoding='utf-8'))
assert subprocess.check_output(['git', 'branch', '--show-current'], cwd=root).decode().strip() == 'exp4'
existing_staged = set(filter(None, subprocess.check_output(['git', 'diff', '--cached', '--name-only', '-z'], cwd=root).decode('utf-8').split('\0')))
changed = [name for name, digest in fixed['protectedAfterFix'].items() if not (root / name).is_file() or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest]
assert not changed, changed
assert hashlib.sha256((root / 'scripts/tests/resource-upload-policy-browser.cjs').read_bytes()).hexdigest() == verification['browserRegressionScriptSha256']

paths = set(fixed['modifiedTrackedFiles'] + fixed['newSourceFiles'])
paths.add('scripts/tests/resource-upload-policy-browser.cjs')
paths.update('docs/' + name for name in ['source-notes.md', 'analysis-notes.md', 'questions.md', 'plan.md', '20261017-exp4-two-fixes-verification-v1.md', '20261017-exp4-acceptance-check-v1.md'])
paths.update(str(file.relative_to(root)).replace('\\', '/') for file in evidence.rglob('*') if file.is_file() and '__pycache__' not in file.parts and file.suffix not in {'.pyc', '.pid'})
assert all(not name.startswith(('raw/', 'runtime/', 'memory-bank/', 'output/')) for name in paths)
assert existing_staged.issubset(paths), {'preexistingFilesOutsideScope': sorted(existing_staged - paths)}

findings = []
total = 0
for name in sorted(paths):
    file = root / name
    assert file.is_file(), name
    total += file.stat().st_size
    assert file.stat().st_size < 50 * 1024 * 1024, name
    if file.suffix.lower() in {'.py', '.cjs', '.mjs', '.ts', '.vue', '.java', '.md', '.log', '.xml', '.txt', '.json', '.ps1', '.html'}:
        data = file.read_bytes()
        encoding = 'utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
        content = data.decode(encoding, errors='replace')
        for label, pattern in [('JWT', r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'), ('private-key', r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'), ('provider-key', r'\b(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-proj-[A-Za-z0-9_-]{30,})')]:
            if re.search(pattern, content):
                findings.append({'file': name, 'type': label})
        if file.suffix.lower() in {'.log', '.xml', '.txt'}:
            for match in re.finditer(r'(?im)Using generated security password:\s*([^\r\n<]+)', content):
                if not match.group(1).startswith('[REDACTED'):
                    findings.append({'file': name, 'type': 'unredacted-test-password'})
assert not findings, findings
manifest = {'branch': 'exp4', 'remote': 'origin', 'baseCommit': fixed['head'], 'files': sorted(paths), 'fileCount': len(paths), 'bytes': total, 'credentialScanFindings': findings, 'sourceMatchesTestedState': True, 'scope': 'Two-defect implementation, required tests, current verification records; unrelated older untracked evidence excluded.'}
(evidence / 'push-scope.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
# Include the scope manifest itself after writing its deterministic candidate list.
paths.add(str((evidence / 'push-scope.json').relative_to(root)).replace('\\', '/'))
ordered = sorted(paths)
for start in range(0, len(ordered), 40):
    subprocess.run(['git', 'add', '-f', '--', *ordered[start:start + 40]], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
staged = subprocess.check_output(['git', 'diff', '--cached', '--name-only', '-z'], cwd=root).decode('utf-8').split('\0')
staged = set(filter(None, staged))
assert staged == paths, {'unexpected': sorted(staged - paths), 'missing': sorted(paths - staged)}
source_paths = [name for name in sorted(paths) if name.startswith(('backend/', 'frontend/', 'scripts/')) or name in {'docs/source-notes.md', 'docs/analysis-notes.md', 'docs/questions.md', 'docs/plan.md', 'docs/20261017-exp4-two-fixes-verification-v1.md', 'docs/20261017-exp4-acceptance-check-v1.md'}]
# Captured logs/XML keep original whitespace; source changes must remain clean.
check = subprocess.run(['git', 'diff', '--cached', '--check', '--', *source_paths], cwd=root, capture_output=True, text=True, errors='replace')
if check.returncode:
    print(json.dumps({'sourceWhitespaceCheckFailed': (check.stdout + check.stderr).splitlines()[:12]}))
    raise SystemExit(check.returncode)
print(json.dumps({'stagedFiles': len(staged), 'candidateBytes': total, 'sourceMatchesTestedState': True, 'credentialScanFindings': findings, 'excludedUnrelatedUntrackedFiles': True}))
