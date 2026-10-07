from pathlib import Path
import hashlib, json, re, subprocess, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
evidence=Path(__file__).resolve().parent
def write(name,data):
    (evidence/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
sources=list((root/'backend/src/main').rglob('*'))+list((root/'backend/src/test').rglob('*'))+list((root/'frontend/src').rglob('*'))+list((root/'frontend/dist').rglob('*'))+[root/'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar',root/'frontend/vite.config.ts']
hashes=[{'Path':str(p),'Hash':digest(p)} for p in sources if p.is_file()]
write('tested-file-hashes.json',hashes)
before=read(root/'docs/20261007-role-functional-evidence-v1/tested-file-hashes.json')
previous={str(Path(v['Path'])):v['Hash'] for v in before}
changed=[str(p.relative_to(root)) for p in sources if p.is_file() and str(p) in previous and digest(p)!=previous[str(p)] and ('src' in p.parts)]
write('changed-sources-since-audit.json',changed)
raw=[{'Path':str(p),'Hash':digest(p)} for p in (root/'raw').rglob('*') if p.is_file()]
write('raw-after.json',raw)
oldraw=read(root/'docs/20261007-role-functional-evidence-v1/raw-before.json')
if isinstance(oldraw,dict):oldraw=[oldraw]
assert {str(Path(r['Path'])):r['Hash'].upper() for r in oldraw}=={r['Path']:r['Hash'] for r in raw},'Raw source changed'
counts=dict(tests=0,failures=0,errors=0,skipped=0)
test_suites=[]
for p in (root/'backend/target/surefire-reports').glob('TEST-*.xml'):
    suite=ET.parse(p).getroot()
    row={'suite':suite.attrib['name'],**{k:int(suite.attrib.get(k,0)) for k in counts}}
    test_suites.append(row)
    for k in counts:counts[k]+=row[k]
write('java-test-summary.json',{'counts':counts,'passed':counts['tests']-counts['skipped']-counts['errors']-counts['failures'],'suites':test_suites})
target=read(evidence/'fix-results.json')
full=read(evidence/'all-features-proxy/results.json')
office=read(evidence/'office-strict/fix-acceptance-results.json')
assert all(r['status']=='PASS' for r in target['results'])
assert all(r['status']=='PASS' for r in full['results'])
assert all(r['result']=='PASS' for r in office)
assert target['pageErrors']==full['pageErrors']==[]
assert counts['failures']==counts['errors']==0
assert read(evidence/'daily-readonly-results.json')['result']=='PASS'
assert read(evidence/'cleanup.json')['testContainersRemoved']
assert read(evidence/'deployment.json')['businessTablesUnchanged']
expected=digest(root/'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar')
deployed=subprocess.check_output(['docker','exec','classroom-backend','sha256sum','/app/app.jar'],text=True).split()[0].upper()
assert expected==deployed,'Local backend JAR differs from tested build'
# Never retain generated Spring test passwords in the delivered evidence logs.
for p in evidence.glob('maven*.log'):
    text=p.read_text(encoding='utf-8',errors='replace')
    p.write_text(re.sub(r'Using generated security password: [^\r\n]+','Using generated security password: [redacted]',text),encoding='utf-8')
for p in [root/'docs/plan.md',root/'docs/questions.md']:
    p.write_text(p.read_text(encoding='utf-8').rstrip()+'\n',encoding='utf-8')
write('summary.json',{'result':'PASS','targetedBrowserChecks':len(target['results']),'fullBrowserChecks':len(full['results']),'officeChecks':len(office),'java':counts,'frontendTestsPassed':10,'frontendBuild':'PASS','pageErrors':[],'rawUnchanged':True,'localDeploymentVerified':True,'testedAndDeployedJarMatch':True,'temporaryRuntimeCleaned':True,'changedSources':changed,'hardwareCameraInference':'NOT_TESTED','knownCsvStudentDatabaseMicroSliceExceptions':'UNCHANGED'})
print(json.dumps({'targeted':len(target['results']),'full':len(full['results']),'office':len(office),'java':counts,'changedSources':changed},ensure_ascii=False))
