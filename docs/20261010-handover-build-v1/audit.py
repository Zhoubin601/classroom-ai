from pathlib import Path
import hashlib, json, re, subprocess

ROOT = Path(__file__).resolve().parents[2]
WORK = Path(__file__).resolve().parent

def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def protected():
    paths = []
    for name in ['raw', 'frontend/src', 'frontend/dev', 'backend/src', 'vision', 'scripts']:
        for p in (ROOT / name).rglob('*'):
            if p.is_file() and not any(t in p.parts for t in ['__pycache__', 'models', 'node_modules', 'target', '.tools']):
                paths.append(p)
    for name in ['frontend/package.json', 'frontend/vite.config.ts', 'backend/pom.xml', 'backend/Dockerfile', 'start_project.ps1', 'stop_project.ps1', 'start_project.bat', 'stop_project.bat', 'README.md', '.gitignore']:
        paths.append(ROOT / name)
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(set(paths))}

def inventory():
    endpoints, entities = [], []
    for p in sorted((ROOT / 'backend/src/main/java').rglob('*.java')):
        text = p.read_text(encoding='utf-8-sig')
        rel = p.relative_to(ROOT).as_posix()
        if '@RestController' in text:
            cl = re.search(r'public class\s+\w+', text)
            prefix = text[:cl.start()] if cl else ''
            base = re.search(r'@RequestMapping\("([^"]+)"\)', prefix)
            base = base.group(1) if base else ''
            for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\b(?:\((.*?)\))?', text, re.S):
                args = m.group(2) or ''
                route = re.search(r'"([^"]*)"', args)
                route = base + (route.group(1) if route else '')
                # Captures annotations only; authorization is manually audited separately.
                endpoints.append({'method': m.group(1).upper(), 'path': route, 'source': rel, 'line': text[:m.start()].count('\n')+1})
        if '@Entity' in text:
            table = re.search(r'@Table\(name\s*=\s*"([^"]+)"', text)
            cls = re.search(r'public class\s+(\w+)', text)
            fields = re.findall(r'private\s+([\w<>.]+)\s+(\w+)\s*(?:[;=])', text)
            joins = re.findall(r'@JoinColumn\(name\s*=\s*"([^"]+)"[^)]*\)\s*private\s+(\w+)\s+(\w+)', text)
            entities.append({'entity': cls.group(1) if cls else '', 'table': table.group(1) if table else '', 'fields': fields, 'joins': joins, 'source': rel})
    return endpoints, entities

if __name__ == '__main__':
    baseline = WORK / 'protected-before.json'
    if not baseline.exists():
        baseline.write_text(json.dumps(protected(), ensure_ascii=False, indent=2), encoding='utf-8')
    head = subprocess.check_output(['git','rev-parse','HEAD'], cwd=ROOT, text=True).strip()
    branch = subprocess.check_output(['git','branch','--show-current'], cwd=ROOT, text=True).strip()
    endpoints, entities = inventory()
    (WORK / 'code-inventory.json').write_text(json.dumps({'date':'2026-10-10','head':head,'branch':branch,'endpoints':endpoints,'entities':entities}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'branch':branch,'head':head,'protected_files':len(protected()),'endpoint_annotations':len(endpoints),'entities':len(entities)}, ensure_ascii=False))
