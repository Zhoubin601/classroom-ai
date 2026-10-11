"""Inspect candidate Git paths without printing or persisting credential values."""
import json
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = Path(__file__).resolve().parent
TEXT_SUFFIXES = {'.json', '.md', '.txt', '.xml', '.py', '.ps1', '.cjs', '.mjs', '.js', '.ts', '.java', '.yml', '.yaml', '.sql', '.html', '.svg', '.csv', '.mmd', '.dumpstream', '.attempt2', '.attempt3'}
SECRET_PATTERNS = {
    'jwt': re.compile(r'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'),
    'private_key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'openai_key': re.compile(r'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,}'),
    'github_token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})'),
    'aws_key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
}
ASSIGNMENT = re.compile(r'''(?i)(password|passwd|api[_-]?key|access[_-]?token|authorization|secret)\s*["']?\s*[:=]\s*["']([^"'\r\n]{4,})["']''')

def inspect_text(path, content):
    hits = []
    for kind, regex in SECRET_PATTERNS.items():
        for match in regex.finditer(content):
            hits.append({'path': path, 'line': content.count('\n', 0, match.start()) + 1, 'kind': kind})
    assignments = []
    for match in ASSIGNMENT.finditer(content):
        value = match.group(2)
        assignments.append({'path': path, 'line': content.count('\n', 0, match.start()) + 1,
                            'field': match.group(1), 'valueLength': len(value),
                            'placeholder': bool(re.search(r'\$\{|\$env:|process\.|os\.|getenv|<|Bearer|REDACT|example|changeme', value, re.I)),
                            'demoLiteral': value.lower() in {'123456', 'root', 'root123', 'admin123', 'teacher123', 'supervisor123', 'password', 'test', 'test123', 'exp3test', 'exp3_test'}})
    return hits, assignments

def main():
    untracked = subprocess.check_output(['git', '-c', 'core.quotepath=false', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, encoding='utf-8').splitlines()
    modified = subprocess.check_output(['git', '-c', 'core.quotepath=false', 'diff', '--name-only'], cwd=ROOT, encoding='utf-8').splitlines()
    paths = sorted(set(untracked + modified))
    high_confidence, assignments, archives, local_images = [], [], [], []
    for relative in paths:
        path = ROOT / relative
        if not path.is_file():
            continue
        if re.search(r'20261010-camera-integration-evidence-v1/(camera-micro(?:-attempt2)?|camera-trend)/.*\.png$', relative):
            local_images.append(relative)
        if path.suffix in TEXT_SUFFIXES and path.stat().st_size < 5_000_000:
            hits, fields = inspect_text(relative, path.read_text(encoding='utf-8-sig', errors='replace'))
            high_confidence.extend(hits)
            assignments.extend(fields)
        if path.suffix == '.zip':
            with zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                notable = [name for name in names if re.search(r'(^|/)(?:\.git|\.venv\w*|node_modules|uploads|register_config\.json)(/|$)|(^|/)\.env(?:$|\.)', name, re.I)]
                archives.append({'path': relative, 'entries': len(names), 'notable_paths': notable[:50], 'notable_count': len(notable)})
                for entry in archive.infolist():
                    if Path(entry.filename).suffix in TEXT_SUFFIXES and entry.file_size < 2_000_000:
                        hits, fields = inspect_text(relative + '::' + entry.filename, archive.read(entry).decode('utf-8-sig', errors='replace'))
                        high_confidence.extend(hits)
                        assignments.extend(fields)
    result = {'candidateFiles': len(paths), 'candidateBytes': sum((ROOT / path).stat().st_size for path in paths if (ROOT / path).is_file()),
              'physicalCameraImagesLocalOnly': local_images, 'credentialMatches': high_confidence,
              'credentialFieldMetadata': assignments, 'archives': archives}
    (WORK / 'audit.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key not in {'credentialFieldMetadata', 'physicalCameraImagesLocalOnly'}}, ensure_ascii=True, indent=2))
    print(json.dumps({'physicalCameraImagesLocalOnly': len(local_images), 'credentialFieldMetadataCount': len(assignments)}, ensure_ascii=True))

if __name__ == '__main__':
    main()
