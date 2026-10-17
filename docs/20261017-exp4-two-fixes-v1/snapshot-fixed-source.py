import hashlib
import json
import subprocess
from pathlib import Path

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]
before = json.loads((evidence / 'baseline.json').read_text(encoding='utf-8'))['protectedBeforeFix']
all_names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root).decode('utf-8').split('\0')
names = set(before)
names.update(name for name in all_names if name.startswith(('backend/src/', 'frontend/src/', 'frontend/tests/')))
hashes = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in names if (root / name).is_file()}
changed = sorted(name for name, original_hash in before.items() if hashes.get(name) != original_hash)
data = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip(), 'protectedAfterFix': hashes, 'modifiedTrackedFiles': changed, 'newSourceFiles': sorted(set(hashes) - set(before))}
(evidence / 'fixed-source.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'protectedFiles': len(hashes), 'modifiedTrackedFiles': changed, 'newSourceFiles': data['newSourceFiles']}))
