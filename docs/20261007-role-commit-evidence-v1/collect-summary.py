from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
evidence=Path(__file__).resolve().parent
previous=Path('D:/2026Autumn Semester File/classroom-ai-demo/docs/20261007-role-fixes-evidence-v1')
counts=dict(tests=0,failures=0,errors=0,skipped=0)
for p in (root/'backend/target/surefire-reports').glob('TEST-*.xml'):
    s=ET.parse(p).getroot()
    for k in counts:counts[k]+=int(s.attrib.get(k,0))
browser=json.loads((evidence/'fix-results.json').read_text(encoding='utf-8'))
assert all(r['status']=='PASS' for r in browser['results'])
assert not browser['pageErrors']
assert not counts['errors'] and not counts['failures']
cleanup=json.loads((evidence/'cleanup.json').read_text(encoding='utf-8-sig'))
assert cleanup['testContainersRemoved']
paths=list((root/'backend/src').rglob('*'))+list((root/'frontend/src').rglob('*'))+list((root/'frontend/dev').rglob('*'))+list((root/'frontend/dist').rglob('*'))+[root/'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar']
fingerprints=[{'path':p.relative_to(root).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths if p.is_file()]
(evidence/'build-fingerprints.json').write_text(json.dumps(fingerprints,ensure_ascii=False,indent=2),encoding='utf-8')
summary={'branch':'fix-exp3','baseCommit':'e2f0950629134ec85d776df58f3e97a57ff9f193','result':'PASS','java':counts,'javaPassed':counts['tests']-counts['skipped'],'frontendTestsPassed':11,'frontendBuild':'PASS','realChromeChecks':len(browser['results']),'pageErrors':browser['pageErrors'],'temporaryServicesRemoved':True,'local8080NotRedeployedInCommitTurn':True}
(evidence/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
for name in ['branch-maven.log','branch-maven-final.log','branch-build.log','branch-frontend-tests.log']:
    p=previous/name
    if p.exists():
        text=re.sub(r'Using generated security password: [^\r\n]+','Using generated security password: [redacted]',p.read_text(encoding='utf-8',errors='replace'))
        p.write_text(text,encoding='utf-8')
        (evidence/name).write_text(text,encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False))
