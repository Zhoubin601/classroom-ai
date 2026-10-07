const fs=require('node:fs'), path=require('node:path'), assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const rootEvidence=path.resolve(process.env.ROLE_EVIDENCE_DIR||path.join(__dirname,'../../docs/role-connectivity-evidence-20261007-v1'));
const evidence=path.resolve(process.env.ROLE_SCHEDULING_EVIDENCE_DIR||rootEvidence);
fs.mkdirSync(evidence,{recursive:true});
const state=JSON.parse(fs.readFileSync(path.join(rootEvidence,'environment.json'),'utf8').replace(/^\uFEFF/,''));
const results=[],http=[],stamp=Date.now();let browser;
async function check(name,fn){try{const details=await fn();results.push({name,status:'PASS',details});console.log('PASS',name,JSON.stringify(details??{}));}catch(e){results.push({name,status:'FAIL',error:e.message});console.log('FAIL',name,e.message);}}
async function login(username){const c=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Shanghai'}),p=await c.newPage();p.setDefaultTimeout(15000);p.on('dialog',d=>d.accept());await p.goto(state.frontend);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.TEST_DEMO_PASSWORD||'123456');await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();return {p,username};}
async function api(a,route,method='GET',data){const r=await a.p.evaluate(async({route,method,data})=>{const r=await fetch(route,{method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),...(data!==undefined?{'Content-Type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});return {status:r.status,body:await r.json()};},{route,method,data});http.push({role:a.username,route,method,status:r.status,code:r.body.code});return r;}
function ok(r){assert.equal(r.status,200);assert.equal(r.body.code,200,r.body.message);return r.body.data;}
function reject(r,code){assert.ok(r.status===code||r.body.code===code,`Expected ${code}, got ${r.status}/${r.body.code}`);}
async function shot(a,name){await a.p.screenshot({path:path.join(evidence,name+'.png'),fullPage:true});}
async function main(){browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});try{
  const d=await login('director'),t=await login('guojun'),s=await login('supervisor'),co=await login('liubo');
  const base=ok(await api(d,'/api/v1/courses'))[0],teachers=ok(await api(d,'/api/v1/teachers')),students=ok(await api(d,'/api/student/list'));
  const primary=teachers.find(x=>x.teacherName==='郭军'),assistant=teachers.find(x=>x.teacherName==='刘博');assert.ok(primary&&assistant&&students.length>=4);
  let course,a,b,schedule;const term='ROLE-'+stamp;
  await check('主任课程建档及删除无关联课程',async()=>{
    const payload={courseCode:'RC'+stamp,courseName:'合成功能测试课程',department:base.department,majorCode:base.majorCode,courseType:base.courseType,credits:2,hours:32,theoryHours:24,practiceHours:8};
    course=ok(await api(d,'/api/v1/courses','POST',payload));assert.equal(course.majorCode,base.majorCode);
    const removable=ok(await api(d,'/api/v1/courses','POST',{...payload,courseCode:'RD'+stamp,courseName:'合成删除测试课程'}));ok(await api(d,`/api/v1/courses/${removable.id}`,'DELETE'));
    assert.ok(!ok(await api(d,'/api/v1/courses')).some(c=>c.id===removable.id));return {createdCourseId:course.id,deletedCourseId:removable.id};
  });
  await check('主任建班主讲协同与名单关联',async()=>{
    assert.ok(course);a=ok(await api(d,'/api/v1/courses/offerings','POST',{courseId:course.id,academicTerm:term,className:'合成联调班A',primaryTeacherId:primary.id,teacherIds:[primary.id,assistant.id],studentIds:students.slice(0,3).map(x=>x.id)}));
    assert.equal(a.teachers.length,2);assert.equal(a.studentCount,3);
    for(const actor of [t,co,s])assert.ok(ok(await api(actor,'/api/v1/courses/offerings')).some(x=>x.id===a.id));
    b=ok(await api(d,'/api/v1/courses/offerings','POST',{courseId:course.id,academicTerm:term,className:'合成联调班B',primaryTeacherId:primary.id,teacherIds:[primary.id],studentIds:[]}));return {offeringA:a.id,offeringB:b.id,teachers:2,studentCount:3,collaboratorRead:true};
  });
  await check('主任排课及跨教室教师冲突409',async()=>{
    assert.ok(a&&b);const data={offeringId:a.id,classroom:'合成教室-'+stamp,dayOfWeek:5,startWeek:1,endWeek:2,startPeriod:1,endPeriod:2};
    schedule=ok(await api(d,'/api/v1/schedules','POST',data));
    reject(await api(d,'/api/v1/schedules','POST',{...data,offeringId:b.id,classroom:'另一合成教室-'+stamp}),409);
    const edited=ok(await api(d,'/api/v1/schedules','POST',{...data,id:schedule.id,endWeek:3}));assert.equal(edited.endWeek,3);
    for(const actor of [t,co,s])assert.ok(ok(await api(actor,`/api/v1/schedules?offeringId=${a.id}`)).some(x=>x.id===schedule.id));
    await d.p.getByRole('button',{name:'开课排课统筹看板 (US-03)',exact:true}).click();await d.p.getByText('合成联调班A',{exact:false}).first().waitFor();await shot(d,'40-real-schedule');
    return {scheduleId:schedule.id,conflict:409,editedEndWeek:3};
  });
  await check('名单增删及教师无统筹权限',async()=>{
    assert.ok(a);assert.equal(ok(await api(d,`/api/v1/courses/offerings/${a.id}/students/add`,'POST',[students[3].studentId])).studentCount,4);
    assert.equal(ok(await api(d,`/api/v1/courses/offerings/${a.id}/students/remove/${students[3].studentId}`,'POST')).studentCount,3);
    reject(await api(t,`/api/v1/courses/offerings/${a.id}/students/add`,'POST',[students[3].studentId]),403);return {add:4,remove:3,teacherAdd:403};
  });
  await check('模拟推流正确区分本班与旁听并归档持久化',async()=>{
    await t.p.reload();await t.p.getByRole('button',{name:'退出登录',exact:true}).waitFor();await t.p.getByRole('button',{name:/课堂智能考勤大屏/}).click();await t.p.getByRole('heading',{name:/课堂智能考勤与态势监控大屏/}).waitFor();
    await t.p.locator('select').first().selectOption(String(a.id));
    const observed=t.p.waitForResponse(async r=>{if(!r.url().includes('/api/visual/overview')||r.status()!==200)return false;try{return (await r.json()).data?.currentPresent===3;}catch{return false;}},{timeout:20000});
    const [start]=await Promise.all([t.p.waitForResponse(r=>r.url().includes('/api/v1/attendance/start')),t.p.getByRole('button',{name:'演示模拟流',exact:true}).click()]);
    const session=ok({status:start.status(),body:await start.json()});await observed;
    const overview=ok(await api(t,`/api/visual/overview?offeringId=${a.id}`));assert.equal(overview.currentPresent,3);assert.equal(overview.auditingCount,1);assert.equal(overview.attendanceRate,100);
    await shot(t,'41-simulation-three-enrolled-one-auditor');
    const [finish]=await Promise.all([t.p.waitForResponse(r=>r.url().includes('/api/v1/attendance/finish')),t.p.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click()]);
    const saved=ok({status:finish.status(),body:await finish.json()});assert.equal(saved.actualCount,3);assert.equal(saved.attendanceRate,100);assert.equal(saved.status,'FINISHED');
    const rows=ok(await api(t,`/api/v1/attendance/offering/${a.id}`));assert.ok(rows.some(x=>x.id===session.id&&x.actualCount===3));
    await t.p.getByRole('button',{name:/考勤归档记录/}).click();await t.p.getByRole('heading',{name:/课程考勤历史归档记录/}).waitFor();await shot(t,'42-simulation-archive-three');return {sessionId:session.id,present:3,auditors:1,attendanceRate:100,mysqlPersisted:true};
  });
  await check('主任课程归档冻结名单排课教师历史人数',async()=>{
    const archived=ok(await api(d,`/api/v1/courses/offerings/${a.id}/archive`,'POST'));assert.equal(archived.isSnapshotFrozen,true);assert.equal(archived.snapshotStudentCount,3);
    reject(await api(d,`/api/v1/courses/offerings/${a.id}/students/add`,'POST',[students[3].studentId]),409);
    reject(await api(d,'/api/v1/schedules','POST',{offeringId:a.id,classroom:'Frozen',dayOfWeek:7,startWeek:4,endWeek:5,startPeriod:3,endPeriod:4}),409);
    const history=ok(await api(t,'/api/v1/courses/offerings/history?term='+term));assert.equal(history.cumulativePersonTimes,3);assert.ok(history.items.some(x=>x.offeringId===a.id||x.id===a.id));
    return {frozen:true,snapshot:3,totalPersonTimes:history.cumulativePersonTimes,rosterWrite:409,scheduleWrite:409};
  });
  await check('无专业授权督导也能挂载外部课程微格元数据',async()=>{
    const username='scopec'+stamp;ok(await api(d,'/api/v1/director/supervisors','POST',{username,password:process.env.TEST_DEMO_PASSWORD||'123456',realName:'合成空授权测试督导',authorizedMajors:''}));const zero=await login(username);
    assert.equal(ok(await api(zero,'/api/v1/courses/offerings')).length,0);reject(await api(zero,`/api/v1/courses/${course.id}`),403);
    const mounted=ok(await api(zero,'/api/v1/resources/micro-slices','POST',{courseId:course.id,videoTitle:'合成空授权越权测试',bopppsStage:'B',durationSeconds:10,sliceUrl:'https://example.invalid/test.mp4'}));
    assert.ok(ok(await api(zero,`/api/v1/resources/micro-slices/course/${course.id}`)).some(x=>x.id===mounted.id));ok(await api(zero,`/api/v1/resources/micro-slices/${mounted.id}`,'DELETE'));
    return {documentedExceptionConfirmed:true,normalCourseRead:403,microMountReadDelete:200,metadataOnly:true};
  });
}finally{fs.writeFileSync(path.join(evidence,'scheduling-results.json'),JSON.stringify({results,mocked:false},null,2));fs.writeFileSync(path.join(evidence,'scheduling-http.json'),JSON.stringify(http,null,2));await browser?.close();if(results.some(r=>r.status==='FAIL'))process.exitCode=1;}}
main().catch(e=>{console.error(e);process.exitCode=1;});
