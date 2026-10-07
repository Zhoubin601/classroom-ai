// Real Chromium, current built backend, MySQL and Redis; no HTTP mocks.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn,execFileSync}=require('node:child_process');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'../..'),evidence=path.resolve(process.env.ROLE_EVIDENCE_DIR);
const state=JSON.parse(fs.readFileSync(path.join(evidence,'environment.json'),'utf8').replace(/^\uFEFF/,''));
const results=[],http=[],stamp=Date.now();let browser,dev;
async function check(name,fn){try{const details=await fn();results.push({name,status:'PASS',details});console.log('PASS',name,JSON.stringify(details??{}));}catch(e){results.push({name,status:'FAIL',error:e.message});console.log('FAIL',name,e.message);}}
async function login(username){const c=await browser.newContext(),p=await c.newPage();p.setDefaultTimeout(15000);await p.goto(state.frontend);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.TEST_DEMO_PASSWORD||'123456');await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();return {p,username};}
async function api(a,route,method='GET',data){const r=await a.p.evaluate(async({route,method,data})=>{const r=await fetch(route,{method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),...(data!==undefined?{'Content-Type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});return {status:r.status,body:await r.json()};},{route,method,data});http.push({role:a.username,route,method,status:r.status,code:r.body.code});return r;}
function ok(r){assert.equal(r.status,200,r.body?.message);assert.equal(r.body.code,200,r.body.message);return r.body.data;}
function reject(r,code){assert.equal(r.status,code,r.body?.message);}
async function main(){browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});try{
  const d=await login('director'),t=await login('guojun'),s=await login('supervisor'),co=await login('liubo'),foreign=await login('jiangly');
  const fixture=JSON.parse(fs.readFileSync(path.join(evidence,'fixture-ids.json'))),base=ok(await api(d,'/api/v1/courses')).find(c=>c.id===fixture.commonCourse);
  const teachers=ok(await api(d,'/api/v1/teachers')),ids=['郭军','刘博'].map(n=>teachers.find(x=>x.teacherName===n).id);
  const course=ok(await api(d,'/api/v1/courses','POST',{courseCode:'RF'+stamp,courseName:'合成修复并发测试',department:base.department,majorCode:base.majorCode,courseType:base.courseType,credits:2,hours:32}));
  async function create(n){return ok(await api(d,'/api/v1/courses/offerings','POST',{courseId:course.id,academicTerm:'FIX-'+stamp,className:'合成修复班'+n,primaryTeacherId:ids[0],teacherIds:ids,studentIds:[]}));}
  const a=await create('A');
  for(const actor of [d,t,s,co])await check(`${actor.username}范围内考勤启动更新结束及真实身份`,async()=>{
    const session=ok(await api(actor,'/api/v1/attendance/start','POST',{offeringId:a.id,operatorRole:'FORGED',operatorName:'伪造身份'}));
    const me=ok(await api(actor,'/api/v1/auth/me'));assert.equal(session.operatorRole,me.role);assert.equal(session.operatorName,me.realName);
    ok(await api(actor,`/api/v1/attendance/live-update?sessionId=${session.id}&actualCount=1&lookupRate=80`,'POST'));
    const finished=ok(await api(actor,'/api/v1/attendance/finish','POST',{sessionId:session.id,actualCount:1,avgLookupRate:80,operatorRole:'FORGED',operatorName:'伪造身份'}));
    assert.equal(finished.status,'FINISHED');assert.equal(finished.operatorRole,me.role);assert.equal(finished.operatorName,me.realName);
    reject(await api(actor,`/api/v1/attendance/live-update?sessionId=${session.id}&actualCount=2&lookupRate=80`,'POST'),409);
    return {sessionId:session.id,trustedRole:me.role,finishedWrite:409};
  });
  await check('并发四次启动在MySQL中只产生一个活动会话',async()=>{
    const responses=await Promise.all(Array.from({length:4},()=>api(t,'/api/v1/attendance/start','POST',{offeringId:a.id})));
    assert.equal(new Set(responses.map(r=>ok(r).id)).size,1);
    assert.equal(ok(await api(t,`/api/v1/attendance/offering/${a.id}`)).filter(x=>x.status==='ACTIVE').length,1);
    return {concurrentRequests:4,activeRows:1};
  });
  await check('班次归档与启动并发后不允许继续写入',async()=>{
    const responses=await Promise.all([api(d,`/api/v1/courses/offerings/${a.id}/archive`,'POST'),api(t,'/api/v1/attendance/start','POST',{offeringId:a.id})]);
    assert.equal(ok(responses[0]).isSnapshotFrozen,true);assert.ok([200,409].includes(responses[1].status));
    const rows=ok(await api(t,`/api/v1/attendance/offering/${a.id}`)),active=rows.find(x=>x.status==='ACTIVE');assert.ok(active);
    reject(await api(t,'/api/v1/attendance/start','POST',{offeringId:a.id}),409);
    reject(await api(t,'/api/v1/attendance/finish','POST',{sessionId:active.id,actualCount:9}),409);
    reject(await api(t,`/api/v1/attendance/live-update?sessionId=${active.id}&actualCount=9&lookupRate=90`,'POST'),409);
    assert.deepEqual(ok(await api(t,`/api/v1/attendance/offering/${a.id}`)),rows);
    return {archive:200,concurrentStart:responses[1].status,afterFreezeWrites:409,rowsUnchanged:true};
  });
  const b=await create('B');
  await check('协同教师页面可选关联班次且冻结班次按钮禁用',async()=>{
    await co.p.reload();await co.p.getByRole('button',{name:'退出登录',exact:true}).waitFor();await co.p.getByRole('button',{name:/课堂智能考勤大屏/}).click();await co.p.getByRole('heading',{name:/课堂智能考勤与态势监控大屏/}).waitFor();
    await co.p.locator('select').first().selectOption(String(b.id));assert.ok(await co.p.getByRole('button',{name:'演示模拟流',exact:true}).isEnabled());
    await co.p.locator('select').first().selectOption(String(a.id));assert.ok(await co.p.getByRole('button',{name:'演示模拟流',exact:true}).isDisabled());assert.ok(await co.p.getByRole('button',{name:'打开摄像头开启考勤',exact:true}).isDisabled());assert.ok(await co.p.getByRole('button',{name:'结束考勤并归档下课',exact:true}).isDisabled());
    await co.p.screenshot({path:path.join(evidence,'43-frozen-collaborator-controls.png'),fullPage:true});
    return {collaboratorSelectable:true,frozenControlsDisabled:true};
  });
  await check('督导同一会话授权撤销即时阻断考勤读写',async()=>{
    const username='fixsup'+stamp,account=ok(await api(d,'/api/v1/director/supervisors','POST',{username,password:process.env.TEST_DEMO_PASSWORD||'123456',realName:'合成撤权督导',authorizedMajors:base.majorCode})),limited=await login(username);
    const session=ok(await api(limited,'/api/v1/attendance/start','POST',{offeringId:b.id}));
    ok(await api(d,`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:''}));
    reject(await api(limited,`/api/v1/attendance/offering/${b.id}`),403);
    reject(await api(limited,`/api/v1/attendance/live-update?sessionId=${session.id}&actualCount=9&lookupRate=90`,'POST'),403);
    reject(await api(limited,'/api/v1/attendance/finish','POST',{sessionId:session.id,actualCount:9}),403);
    assert.equal(ok(await api(limited,'/api/v1/attendance/current')),null);
    return {sameLoginSession:true,readUpdateFinish:403,current:null};
  });
  await check('视觉大屏所有入口拦截未关联班次及缺失班次',async()=>{
    for(const route of ['/overview','/trend','/students/status']){reject(await api(t,'/api/visual'+route+'?offeringId='+fixture.outsideOffering),403);reject(await api(t,'/api/visual'+route),400);}
    reject(await api(t,'/api/visual/reset?offeringId='+fixture.outsideOffering,'POST'),403);
    reject(await api(t,'/api/visual/report/stream','POST',{offeringId:fixture.outsideOffering,detectedPersonCount:9}),403);
    reject(await api(t,'/api/visual/report/stream','POST',{detectedPersonCount:9}),400);
    return {foreign:403,missing:400};
  });
  await check('冻结班次拒绝视觉流持久化',async()=>{reject(await api(t,'/api/visual/report/stream','POST',{offeringId:a.id,detectedPersonCount:9}),409);});
  await check('跨班趋势不泄露且本班重置不清空外班数据',async()=>{
    ok(await api(foreign,'/api/visual/report/stream','POST',{offeringId:fixture.outsideOffering,detectedPersonCount:9,lookupCount:7,lookupRate:0.7,presentStudentIds:[]}));
    const before=ok(await api(foreign,'/api/visual/trend?offeringId='+fixture.outsideOffering));assert.ok(before.some(x=>x.lookupRate>0));
    assert.ok(ok(await api(t,'/api/visual/trend?offeringId='+b.id)).every(x=>x.lookupRate===0&&x.presentCount===0));
    ok(await api(t,'/api/visual/reset?offeringId='+b.id,'POST'));
    assert.deepEqual(ok(await api(foreign,'/api/visual/trend?offeringId='+fixture.outsideOffering)),before);
    ok(await api(t,'/api/visual/report/stream','POST',{offeringId:b.id,detectedPersonCount:2,lookupRate:0.3,presentStudentIds:[]}));
    assert.ok(ok(await api(t,'/api/visual/trend?offeringId='+b.id)).every(x=>x.lookupRate===30));
    assert.deepEqual(ok(await api(foreign,'/api/visual/trend?offeringId='+fixture.outsideOffering)),before);
    ok(await api(t,'/api/visual/reset?offeringId='+b.id,'POST'));
    assert.deepEqual(ok(await api(foreign,'/api/visual/trend?offeringId='+fixture.outsideOffering)),before);
    return {outsideTrendIntact:true,localTrendEmpty:true};
  });
  const log=fs.openSync(path.join(evidence,'vite-dev-adapter.log'),'a');
  dev=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','vite.config.ts','--host','127.0.0.1','--port','15174','--strictPort'],{cwd:path.join(root,'frontend'),env:{...process.env,CLASSROOM_API_PROXY:state.backend},windowsHide:true,stdio:['ignore',log,log]});
  fs.closeSync(log);let ready=false;for(let i=0;i<60;i++){if(dev.exitCode!==null)throw Error('Vite adapter failed');try{if((await fetch('http://127.0.0.1:15174')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,250));}assert.ok(ready);
  await check('真实Vite人脸注册与验证入口匿名401教师督导403',async()=>{
    for(const route of ['/register-webcam','/launch-register','/verify-webcam','/launch-verify']){
      const url='http://127.0.0.1:15174/api/face'+route;
      assert.equal((await t.p.request.post(url,{data:{}})).status(),401);
      for(const actor of [t,s]){const token=await actor.p.evaluate(()=>localStorage.getItem('jwtToken'));assert.equal((await actor.p.request.post(url,{headers:{Authorization:'Bearer '+token},data:{}})).status(),403);}
    }
    const token=await d.p.evaluate(()=>localStorage.getItem('jwtToken'));
    for(const route of ['/register-webcam','/launch-register','/verify-webcam'])assert.equal((await d.p.request.post('http://127.0.0.1:15174/api/face'+route,{headers:{Authorization:'Bearer '+token},data:{}})).status(),400);
    return {anonymous:401,teacherSupervisor:403,directorReachesValidation:400,noHardwareSpawned:true};
  });
  await check('Python请求携带主任身份访问真实底库服务',async()=>{
    const token=await d.p.evaluate(()=>localStorage.getItem('jwtToken'));
    const code="import json,sys,urllib.request; from vision.backend_auth import authenticated_request; r=urllib.request.urlopen(authenticated_request(sys.argv[1]),timeout=5); print(json.dumps({'http':r.status,'code':json.load(r)['code']}))";
    const response=JSON.parse(execFileSync('python',['-B','-c',code,state.backend+'/api/face/all'],{cwd:root,env:{...process.env,CLASSROOM_FACE_AUTHORIZATION:'Bearer '+token,CLASSROOM_FACE_COOKIE:''},windowsHide:true,encoding:'utf8'}));
    assert.deepEqual(response,{http:200,code:200});return response;
  });
}finally{dev?.kill();fs.writeFileSync(path.join(evidence,'fix-results.json'),JSON.stringify({results,mocked:false,browserVersion:browser?.version()},null,2));fs.writeFileSync(path.join(evidence,'fix-http.json'),JSON.stringify(http,null,2));await browser?.close();if(results.some(r=>r.status==='FAIL'))process.exitCode=1;}}
main().catch(e=>{console.error(e);process.exitCode=1;});
