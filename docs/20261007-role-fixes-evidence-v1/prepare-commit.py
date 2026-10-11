from pathlib import Path
import os, json, subprocess, re
root=Path(__file__).resolve().parents[2]
def git(*args,env=None):return subprocess.check_output(['git',*args],cwd=root,env=env).decode('utf-8').strip()
tracked=git('diff','--name-only','-z').split('\0')
files=[p for p in tracked if p]
files += [p for p in git('ls-files','--others','--exclude-standard','backend/src/test','scripts/repair-course-self-prerequisite.cjs','scripts/run-association-mysql-tests.ps1').splitlines() if p]
files += ['docs/20261007-association-audit-v1.md','docs/20261007-association-fixes-v1.md','output/20261007-role-integration-check-v1.md','output/20261007-role-integration-fixes-v1.md']
for folder in ['20261007-association-audit-evidence-v1','20261007-association-fixes-evidence-v1','20261007-role-functional-evidence-v1','20261007-role-fixes-evidence-v1']:
    for p in (root/'docs'/folder).rglob('*'):
        if p.is_file() and p.suffix in {'.json','.cjs','.ps1','.py','.md'} and p.name!='prepare-commit.py':files.append(str(p.relative_to(root)).replace('\\','/'))
files=sorted(set(files))
for f in files:
    text=(root/f).read_text(encoding='utf-8-sig')
    if re.search(r'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',text):raise RuntimeError('JWT found in '+f)
index=root/'runtime/role-fixes-commit.index'
if index.exists():index.unlink()  # Only the task-owned index created by this script.
env={**os.environ,'GIT_INDEX_FILE':str(index)}
git('read-tree','HEAD',env=env)
git('add','--',*files,env=env)
git('diff','--cached','--check',env=env)
tree=git('write-tree',env=env)
parent=git('rev-parse','HEAD')
message='fix: close role permission gaps and preserve teaching associations\n\nScope realtime dashboards, serialize frozen offering writes, restrict roster candidates, and correct download media types. Preserve course ownership, syllabus catalogs, prerequisites, attendance identity, and current account integrity.\n\nValidated with 149 Java tests (47 conditional skips), 10 frontend tests, and real Chrome checks: 13 targeted, 23 full-page, and 6 Office scenarios.\n'
commit=subprocess.check_output(['git','commit-tree',tree,'-p',parent],input=message.encode(),cwd=root,env=env).decode().strip()
(root/'docs/20261007-role-fixes-evidence-v1/commit-source.json').write_text(json.dumps({'sourceCommit':commit,'base':parent,'fileCount':len(files),'targetBranch':'fix-exp3','included':files},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'sourceCommit':commit,'files':len(files)},ensure_ascii=False))
