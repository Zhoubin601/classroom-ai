"""Redact disposable authentication values from captured test evidence before publication."""
import re
import sys
from pathlib import Path

def sanitize(root: Path) -> int:
    count = 0
    for file in root.rglob('*'):
        if file.suffix.lower() not in ('.log', '.xml', '.txt', '.html'):
            continue
        raw = file.read_bytes()
        encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
        text = raw.decode(encoding, errors='replace')
        clean, jwt_count = re.subn(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[REDACTED_TEST_TOKEN]', text)
        clean, csrf_count = re.subn(r'(?im)(x-csrf-token:\s*)[^\r\n<]+', r'\1[REDACTED_TEST_CSRF]', clean)
        if jwt_count + csrf_count:
            file.write_text(clean, encoding='utf-8')
            count += jwt_count + csrf_count
    return count

if __name__ == '__main__':
    print('Redacted test authentication values:', sanitize(Path(sys.argv[1]).resolve()))
