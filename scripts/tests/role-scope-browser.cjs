const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const evidence=path.resolve(process.env.ROLE_EVIDENCE_DIR||path.join(__dirname,'../../docs/role-connectivity-evidence-20261007-v1'));
const state=JSON.parse(fs.readFileSync(path.join(evidence,'environment.json'),'utf8').replace(/^\uFEFF/,''));
const results=[],http=[],stamp=Date.now();let browser;
async function check(name,fn){try{const details=await fn();results.push({name,status:'PASS',details});console.log('PASS',name,JSON.stringify(details??{}));}catch(e){results.push({name,status:'FAIL',error:e.message});console.log('FAIL',name,e.message);}}
async function login(username){const c=await browser.newContext(),p=await c.newPage();p.setDefaultTimeout(15000);p.on('dialog',d=>d.accept());await p.goto(state.frontend);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.TEST_DEMO_PASSWORD||'123456');await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();return {p,username};}
async function api(a,route,method='GET',data){const r=await a.p.evaluate(async({route,method,data})=>{const r=await fetch(route,{method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),...(data!==undefined?{'Content-Type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});const bytes=await r.arrayBuffer();let body;try{body=JSON.parse(new TextDecoder().decode(bytes));}catch{}return {status:r.status,body,bytes:bytes.byteLength};},{route,method,data});http.push({role:a.username,route,method,status:r.status,code:r.body?.code,bytes:r.bytes});return r;}
function ok(r){assert.equal(r.status,200);assert.equal(r.body.code,200,r.body.message);return r.body.data;}
function deny(r){assert.equal(r.status,403,`Expected 403, got ${r.status}/${r.body?.code}`);}
async function main(){browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});try{
  const d=await login('director'),t=await login('guojun'),s=await login('supervisor'),other=await login('liubo'),lead=await login('director_arch'),participant=await login('director_base'),csTeacher=await login('jiangly');
  const version='ROLE-CS-'+stamp,items=[{indicatorCode:'R1',requirementCategory:'合成类别',indicatorDescription:'合成目录权限测试'}];
  await check('CS牵头主任可导入公共目录',async()=>{assert.equal(ok(await api(lead,`/api/v1/syllabus/plans/CS/${version}/indicators`,'PUT',items)).length,1);});
  await check('CS参与主任可读目录但不能导入和访问外室课程',async()=>{
    const managed=ok(await api(participant,'/api/v1/director/managed-majors'));assert.ok(managed.some(m=>m.majorCode==='CS'));
    assert.equal(ok(await api(participant,`/api/v1/syllabus/plans/CS/${version}/indicators`)).length,1);
    deny(await api(participant,`/api/v1/syllabus/plans/CS/${version}/indicators`,'PUT',items));
    const course=ok(await api(lead,'/api/v1/courses'))[0];deny(await api(participant,`/api/v1/courses/${course.id}`));return {directoryRead:200,directoryWrite:403,foreignCourseRead:403};
  });
  await check('实际授课教师与授权督导能读取CS目录',async()=>{for(const a of [csTeacher,s])assert.equal(ok(await api(a,`/api/v1/syllabus/plans/CS/${version}/indicators`)).length,1);});
  const resources=ok(await api(t,'/api/v1/resources'));const resource=resources.find(r=>r.isPublic&&r.resourceName.startsWith('PW-Doc'));assert.ok(resource);
  await check('主任和关联教师可下载本课程原件',async()=>{for(const a of [d,t]){const r=await api(a,`/api/v1/resources/${resource.id}/download`);assert.equal(r.status,200);assert.ok(r.bytes>100);}return {resourceId:resource.id};});
  await check('同室共享教师只读无修改下载权',async()=>{
    assert.equal(ok(await api(other,`/api/v1/resources/${resource.id}`)).id,resource.id);
    deny(await api(other,`/api/v1/resources/${resource.id}/download`));deny(await api(other,'/api/v1/resources','POST',{id:resource.id,courseId:resource.course.id,chapter:'合成越权',resourceName:'越权测试',isPublic:true}));return {read:200,download:403,write:403};
  });
  await check('督导资源只读不能编辑下载',async()=>{ok(await api(s,`/api/v1/resources/${resource.id}`));deny(await api(s,`/api/v1/resources/${resource.id}/download`));deny(await api(s,'/api/v1/resources','POST',{id:resource.id,courseId:resource.course.id,chapter:'合成越权',resourceName:'越权测试'}));});
  const studentId='ROLE-FACE-'+stamp,vector=Array.from({length:512},(_,i)=>i===0?1:0);
  await check('主任可注册并读取合成人脸档案',async()=>{ok(await api(d,'/api/face/register','POST',{studentId,name:'合成权限测试',className:'合成测试班',featureVector:vector}));assert.ok(ok(await api(d,'/api/face/all')).some(x=>x.studentId===studentId));});
  for(const a of [t,s]) await check(`${a.username}禁止读取人脸底库接口`,async()=>deny(await api(a,'/api/face/all')));
  await check('教师不能绕过学生接口通过人脸注册创建档案',async()=>{
    const r=await api(t,'/api/face/register','POST',{studentId:studentId+'-FORBIDDEN',name:'合成权限测试',className:'合成测试班',featureVector:vector});
    deny(r);
    assert.ok(!ok(await api(d,'/api/student/list')).some(x=>x.studentId===studentId+'-FORBIDDEN'));
  });
  await check('督导不能删除人脸底库记录',async()=>deny(await api(s,`/api/face/${studentId}`,'DELETE')));
  await check('越权删除未改变底库且主任可删除',async()=>{assert.ok(ok(await api(d,'/api/face/all')).some(x=>x.studentId===studentId));ok(await api(d,`/api/face/${studentId}`,'DELETE'));assert.ok(!ok(await api(d,'/api/face/all')).some(x=>x.studentId===studentId));});
}finally{fs.writeFileSync(path.join(evidence,'scope-results.json'),JSON.stringify({results,mocked:false},null,2));fs.writeFileSync(path.join(evidence,'scope-http.json'),JSON.stringify(http,null,2));await browser?.close();if(results.some(r=>r.status==='FAIL'))process.exitCode=1;}}
main().catch(e=>{console.error(e);process.exitCode=1;});
