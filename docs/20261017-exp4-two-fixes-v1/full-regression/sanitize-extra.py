"""Redact generated framework and fixture credentials from this audit's evidence."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
total = 0
for file in root.rglob('*'):
    if file.suffix.lower() not in {'.log', '.xml', '.txt', '.html', '.json'}:
        continue
    raw = file.read_bytes()
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    content = raw.decode(encoding, errors='replace')
    cleaned, count = re.subn(
        r'(?im)(Using generated security password:)(?![ \t]*\[REDACTED)[^\r\n<]+',
        r'\1 [REDACTED_GENERATED_SECURITY_PASSWORD]', content)
    cleaned, count_xml = re.subn(
        r'(?i)(<property\b(?=[^>]*\bname="[^"]*(?:password|secret|token)[^"]*")[^>]*\bvalue=")(?!\[REDACTED)([^"]+)(")',
        r'\1[REDACTED_TEST_SECRET]\3', cleaned)
    cleaned, count_json = re.subn(
        r'(?i)(["\']password["\']\s*:\s*["\'])(?!\[REDACTED)([^"\']+)(["\'])',
        r'\1[REDACTED_TEST_PASSWORD]\3', cleaned)
    if count + count_xml + count_json:
        file.write_text(cleaned, encoding='utf-8')
        total += count + count_xml + count_json
print('Additional redacted test credential values:', total)
