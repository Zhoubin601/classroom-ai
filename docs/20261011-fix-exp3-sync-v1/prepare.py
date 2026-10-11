"""Finalize provenance and record selected paths; do not alter raw or artifacts."""
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = Path(__file__).resolve().parent

def digest(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()

def git(*args):
    return subprocess.check_output(['git', '-c', 'core.quotepath=false', *args], cwd=ROOT, encoding='utf-8').strip()

def main():
    audit = json.loads((WORK / 'audit.json').read_text(encoding='utf-8'))
    if audit['credentialMatches']:
        raise SystemExit('Review credential matches before staging.')
    note = '\n\n## 2026-10-11 本地资料同步授权\n用户明确授权提交并push到fix-exp3。提交本地交接、验收、清理记录、正式交接材料与设计说明；11张真人摄像头截图和两个含人脸数据的历史源码包保留本地，新增精确.gitignore规则。业务源码无改动；raw未改写，main不变。来源为当前用户请求、本地Git及文件/ZIP只读核验，引用日期2026-10-11。明细docs/20261011-fix-exp3-sync-v1.md；最终提交与推送结果以Git实时远端核验为准。\n'
    for relative in ('docs/source-notes.md', 'docs/analysis-notes.md', 'docs/plan.md', 'docs/questions.md', 'memory-bank/project-context.md'):
        path = ROOT / relative
        content = path.read_text(encoding='utf-8-sig')
        if '## 2026-10-11 本地资料同步授权' in content:
            raise SystemExit('Provenance already added; refusing duplicate.')
        path.write_text(content.rstrip() + note, encoding='utf-8', newline='\n')
    retained = audit['physicalCameraImagesLocalOnly'] + [
        'output/20260921-第二组-Sprint1源代码-v3.zip',
        'output/20260922-第二组-Sprint1源代码-v4.zip']
    protected = ['raw/README.md'] + retained
    snapshot = {'branch': git('branch', '--show-current'), 'head': git('rev-parse', 'HEAD'),
                'main': git('rev-parse', 'refs/heads/main'), 'stashes': git('stash', 'list', '--format=%H').splitlines(),
                'protectedFiles': {relative: digest(ROOT / relative) for relative in protected},
                'localOnlyFiles': retained}
    (WORK / 'protected-before.json').write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding='utf-8')
    with zipfile.ZipFile(ROOT / 'output/20261010-developer-handover-materials-v1.zip') as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == 22
    paths = sorted(set(git('ls-files', '--others', '--exclude-standard').splitlines() + git('diff', '--name-only').splitlines()))
    assert not set(paths).intersection(retained)
    assert all(path.startswith(('docs/', 'output/', 'design-system/', 'memory-bank/')) or path == '.gitignore' for path in paths)
    selection = {'branch': snapshot['branch'], 'startingHead': snapshot['head'], 'files': paths,
                 'fileCount': len(paths), 'bytes': sum((ROOT / path).stat().st_size for path in paths),
                 'localOnlyFiles': retained, 'codeChanged': False, 'handoverZipCrcValid': True,
                 'rawUntouched': True, 'remoteMainNotTargeted': True}
    (WORK / 'selection.json').write_text(json.dumps(selection, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in selection.items() if key not in {'files', 'localOnlyFiles'}}, ensure_ascii=True, indent=2))

if __name__ == '__main__':
    main()
