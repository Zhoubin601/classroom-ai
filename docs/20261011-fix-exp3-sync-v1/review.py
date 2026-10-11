"""Check staged scope, syntax and preservation before the authorized push."""
import ast
import hashlib
import json
import subprocess
from pathlib import Path
from audit import inspect_text, TEXT_SUFFIXES

ROOT = Path(__file__).resolve().parents[2]
WORK = Path(__file__).resolve().parent

def git(*args):
    return subprocess.check_output(['git', '-c', 'core.quotepath=false', *args], cwd=ROOT, encoding='utf-8').strip()

def main():
    staged = git('diff', '--cached', '--name-only').splitlines()
    baseline = json.loads((WORK / 'protected-before.json').read_text(encoding='utf-8'))
    assert git('branch', '--show-current') == 'fix-exp3'
    assert git('rev-parse', 'HEAD') == baseline['head']
    assert git('rev-parse', 'refs/heads/main') == baseline['main']
    assert git('stash', 'list', '--format=%H').splitlines() == baseline['stashes']
    assert not set(staged).intersection(baseline['localOnlyFiles'])
    assert all(path == '.gitignore' or path.startswith(('docs/', 'output/', 'memory-bank/', 'design-system/')) for path in staged)
    assert not git('diff', '--cached', '--name-only', '--', 'frontend', 'backend', 'vision', 'scripts', 'raw')
    for relative, expected in baseline['protectedFiles'].items():
        with (ROOT / relative).open('rb') as handle:
            assert hashlib.file_digest(handle, 'sha256').hexdigest() == expected, relative
    syntax, findings = [], []
    for relative in staged:
        path = ROOT / relative
        if path.suffix in TEXT_SUFFIXES and path.stat().st_size < 5_000_000:
            content = path.read_text(encoding='utf-8-sig', errors='replace')
            matches, _ = inspect_text(relative, content)
            findings.extend(matches)
            if path.suffix == '.py':
                ast.parse(content, filename=relative)
                syntax.append(relative)
            elif path.suffix in {'.cjs', '.mjs', '.js'}:
                subprocess.run(['node', '--check', str(path)], cwd=ROOT, check=True, capture_output=True)
                syntax.append(relative)
    assert not findings, findings
    result = {'passed': True, 'reviewedStagedFiles': len(staged), 'stagedBytes': sum((ROOT / path).stat().st_size for path in staged),
              'scopeRestrictedToDocsOutputsAndIgnoreRules': True, 'sourceAndRawNotStaged': True,
              'credentialTokenPatternsAbsent': True, 'pythonAndNodeSyntaxFilesPassed': len(syntax),
              'physicalImagesAndHistoricalArchivesPreserved': len(baseline['protectedFiles']) - 1,
              'rawHashUnchanged': True, 'mainAndStashesUnchanged': True,
              'historicalEvidenceWhitespacePreserved': True, 'files': staged}
    (WORK / 'staged-review.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key != 'files'}, ensure_ascii=True, indent=2))

if __name__ == '__main__':
    main()
