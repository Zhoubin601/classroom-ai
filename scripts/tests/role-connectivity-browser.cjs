// Real browser + Vite proxy + isolated production backend. No response fixtures.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const evidence = path.resolve(process.env.ROLE_EVIDENCE_DIR || path.join(__dirname, '../../docs/role-connectivity-evidence-20261007-v1'));
const state = JSON.parse(fs.readFileSync(path.join(evidence, 'environment.json'), 'utf8').replace(/^\uFEFF/, ''));
const results = [], requests = [], errors = [];
const stamp = Date.now();
let browser;
async function check(name, fn, kind = 'requirement') {
  try { const details = await fn(); results.push({ name, kind, status: 'PASS', details }); console.log('PASS', name, JSON.stringify(details ?? {})); }
  catch(e) { results.push({ name, kind, status: 'FAIL', error: e.message }); console.log('FAIL', name, e.message); }
}
async function login(username) {
  const context = await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Shanghai'});
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('dialog', d => d.accept());
  page.on('pageerror', e => errors.push({role:username,message:e.message}));
  await page.goto(state.frontend);
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.TEST_DEMO_PASSWORD || '123456');
  await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
  return {page, username};
}
async function api(actor, route, method='GET', data) {
  const response = await actor.page.evaluate(async ({route, method, data}) => {
    const response = await fetch(route,{method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),...(data!==undefined?{'Content-Type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});
    const text = await response.text(); let body; try {body=JSON.parse(text)} catch {body=null}
    return {status:response.status,body,text:body?undefined:text};
  },{route,method,data});
  requests.push({role:actor.username,method,route,status:response.status,code:response.body?.code});
  return response;
}
function ok(response) { assert.equal(response.status,200); assert.equal(response.body.code,200,response.body.message); return response.body.data; }
function denied(response) { assert.equal(response.status,403,`Expected 403, got ${response.status}/${response.body?.code}`); }
function rejected(response, code=409) { assert.ok(response.status===code || response.body?.code===code,`Expected ${code}, got ${response.status}/${response.body?.code}`); }
async function shot(actor,name) { await actor.page.screenshot({path:path.join(evidence,name+'.png'),fullPage:true}); }
async function main() {
  assert.match(state.mysql,/^classroom-exp3-browser-[a-f0-9]{10}$/);
  browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
  try {
    const director=await login('director'), teacher=await login('guojun'), supervisor=await login('supervisor'), foreign=await login('jiangly');
    const own=ok(await api(teacher,'/api/v1/courses/offerings'));
    const sup=ok(await api(supervisor,'/api/v1/courses/offerings'));
    const dir=ok(await api(director,'/api/v1/courses/offerings'));
    const elsewhere=ok(await api(foreign,'/api/v1/courses/offerings'));
    const common=own.find(o=>!o.isSnapshotFrozen&&o.status!=='FINISHED'&&sup.some(s=>s.id===o.id)&&dir.some(d=>d.id===o.id)); assert.ok(common);
    const outside=elsewhere.find(o=>!own.some(t=>t.id===o.id)&&!dir.some(t=>t.id===o.id)); assert.ok(outside);
    fs.writeFileSync(path.join(evidence,'fixture-ids.json'),JSON.stringify({commonOffering:common.id,commonCourse:common.course.id,outsideOffering:outside.id,outsideCourse:outside.course.id,counts:{teacher:own.length,director:dir.length,supervisor:sup.length}},null,2));
    await check('匿名访问课程被401拦截',async()=>{ const r=await teacher.page.request.get(state.frontend+'/api/v1/courses');assert.equal(r.status(),401);return {http:401}; });
    for(const a of [teacher,supervisor]) {
      for(const [name,route,method,data] of [
        ['课程建档','/api/v1/courses','POST',{}],['课程删除',`/api/v1/courses/${common.course.id}`,'DELETE'],
        ['建班','/api/v1/courses/offerings','POST',{}],['排课','/api/v1/schedules','POST',{}],
        ['归档',`/api/v1/courses/offerings/${common.id}/archive`,'POST'],['公共目录导入','/api/v1/syllabus/plans/SE/UNAUTHORIZED/indicators','PUT',[]],
        ['主任锁定大纲','/api/v1/syllabus/1/lock','POST'],['主任审核评价','/api/v1/supervisions/1/review','POST',{approved:true}],
        ['督导管理','/api/v1/director/supervisors','GET'],['质量CSV导出','/api/v1/supervisions/analytics/export-report','GET'],
        ['学生底库','/api/student/list','GET']]) {
        await check(`${a.username}禁止${name}`,async()=>{denied(await api(a,route,method,data));return {http:403};});
      }
    }
    for(const a of [director,supervisor]) await check(`${a.username}禁止读取教师工作草稿`,async()=>{denied(await api(a,`/api/v1/courses/${common.course.id}/content/draft`));});
    await check('教师不能读取他人课程和班次',async()=>{denied(await api(teacher,`/api/v1/courses/${outside.course.id}`));denied(await api(teacher,`/api/v1/courses/offerings/${outside.id}`));});
    await check('主任不能读取外室课程和班次',async()=>{denied(await api(director,`/api/v1/courses/${outside.course.id}`));denied(await api(director,`/api/v1/courses/offerings/${outside.id}`));});
    for(const route of ['dashboard','coverage','alerts']) await check(`教师禁止质量${route}管理接口`,async()=>denied(await api(teacher,'/api/v1/supervisions/analytics/'+route)));
    await check('教师只能请求本人雷达统计',async()=>{ok(await api(teacher,'/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent('郭军')));denied(await api(teacher,'/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent('姜琳颖')));});
    await check('教师草稿保存发布到主任督导读取',async()=>{
      let draft=ok(await api(teacher,`/api/v1/courses/${common.course.id}/content/draft`));
      const identity=d=>({draftId:d.id,lockVersion:d.lockVersion,publishVersion:d.publishVersion});
      const content={description:'角色链路测试-'+stamp,assessmentMethod:'合成测试：考核方式',objectives:'合成测试：教学目标'};
      draft=ok(await api(teacher,`/api/v1/courses/${common.course.id}/content/draft`,'PUT',{...identity(draft),...content}));
      const published=ok(await api(teacher,`/api/v1/courses/${common.course.id}/content/publish`,'POST',{...identity(draft),...content}));
      assert.equal(published.status,'PUBLISHED');
      for(const a of [director,supervisor]) assert.equal(ok(await api(a,`/api/v1/courses/${common.course.id}/content/published`)).description,content.description);
      rejected(await api(teacher,`/api/v1/courses/${common.course.id}/content/publish`,'POST',{...identity(draft),...content}));
      await teacher.page.reload();await teacher.page.getByRole('button',{name:'退出登录',exact:true}).waitFor();await shot(teacher,'30-published-content');
      return {publishVersion:published.publishVersion,readerRoles:['director','supervisor'],staleDraft:409};
    });
    let limited;
    await check('主任建督导并即时收回专业授权',async()=>{
      const username='rolecheck'+stamp;
      const account=ok(await api(director,'/api/v1/director/supervisors','POST',{username,password:process.env.TEST_DEMO_PASSWORD||'123456',realName:'合成链路测试督导',authorizedMajors:common.course.majorCode}));
      limited=await login(username);
      const visible=ok(await api(limited,'/api/v1/courses/offerings'));assert.ok(visible.some(o=>o.id===common.id));
      const unauthorized=sup.find(o=>o.course.majorCode!==common.course.majorCode);assert.ok(unauthorized);
      denied(await api(limited,`/api/v1/courses/offerings/${unauthorized.id}`));
      denied(await api(director,`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:'NONEXISTENT'}));
      ok(await api(director,`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:''}));
      assert.equal(ok(await api(limited,'/api/v1/courses/offerings')).length,0);
      denied(await api(limited,`/api/v1/courses/offerings/${common.id}`));
      return {accountCreated:true,sameSessionRevoke:true,forbiddenMajor:403};
    });
    let evalId;
    await check('评价驳回修改重提审核日志闭环',async()=>{
      const content={offeringId:common.id,listenTopic:'角色闭环-'+stamp,scoreAttitude:10,scoreContent:10,scoreMethod:10,scoreEffect:10,highlights:'1. 组织有序\n2. 案例充分\n3. 互动及时',suggestions:'合成测试加强实践反馈',isDraft:true};
      let evaluation=ok(await api(supervisor,'/api/v1/supervisions','POST',content));evalId=evaluation.id;assert.equal(evaluation.status,'DRAFT');
      evaluation=ok(await api(supervisor,'/api/v1/supervisions','POST',{...content,id:evalId,isDraft:false}));assert.equal(evaluation.status,'PENDING_REVIEW');
      evaluation=ok(await api(director,`/api/v1/supervisions/${evalId}/review`,'POST',{approved:false,note:'合成测试驳回'}));assert.equal(evaluation.status,'REJECTED');
      evaluation=ok(await api(supervisor,'/api/v1/supervisions','POST',{...content,id:evalId,isDraft:false}));assert.equal(evaluation.status,'PENDING_REVIEW');
      evaluation=ok(await api(director,`/api/v1/supervisions/${evalId}/review`,'POST',{approved:true,note:'合成测试通过'}));assert.equal(evaluation.status,'APPROVED_PENDING');
      assert.ok(new Date(evaluation.publishTime).getTime()>Date.now()+23*60*60*1000);
      const audit=ok(await api(director,`/api/v1/supervisions/${evalId}/audit`));assert.ok(audit.length>=2);
      const before=ok(await api(teacher,`/api/v1/supervisions?offeringId=${common.id}`));assert.ok(!before.some(e=>e.id===evalId));
      execFileSync('docker',['exec','-e','MYSQL_PWD=root',state.mysql,'mysql','-uroot','classroom_ai','-e',`UPDATE t_supervision_evaluation SET publish_time=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE id=${evalId}`]);
      const after=ok(await api(teacher,`/api/v1/supervisions?offeringId=${common.id}`));const anon=after.find(e=>e.id===evalId);assert.ok(anon);assert.equal(anon.supervisorName,'匿名督导');assert.equal(anon.supervisorUserId,null);assert.ok(!anon.reviewNote && !anon.reviewedBy);
      denied(await api(teacher,`/api/v1/supervisions/${evalId}/audit`));
      return {id:evalId,auditEntries:audit.length,delayHours:24,publicationClockAdvancedOnlyInIsolatedMysql:true,anonymous:true};
    });
    await check('教师和主任不能提交督导评价',async()=>{for(const a of [teacher,director]) denied(await api(a,'/api/v1/supervisions','POST',{offeringId:common.id,isDraft:true}));});
    await check('覆盖明细追溯到真实评价',async()=>{for(const a of [director,supervisor]) {const detail=ok(await api(a,'/api/v1/supervisions/analytics/coverage?term='+encodeURIComponent(common.academicTerm)));assert.ok(detail.some(d=>d.courseId===common.course.id&&d.evaluationIds.includes(evalId)));}return {evaluationId:evalId};});
    await check('低分预警与雷达词云来自已发布评价',async()=>{
      const alerts=ok(await api(director,'/api/v1/supervisions/analytics/alerts'));assert.ok(alerts.some(a=>a.courseId===common.course.id&&a.alertLevel==='RED'));
      const radar=ok(await api(teacher,'/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent('郭军')));assert.ok(radar.evaluationCount>0);assert.ok(radar.suggestionList.includes('合成测试加强实践反馈'));assert.ok(radar.wordCloud.length>0);
      return {redAlert:true,evaluationCount:radar.evaluationCount,wordCloudItems:radar.wordCloud.length};
    });
    await check('主任CSV包含外室开课范围例外',async()=>{
      const r=await api(director,'/api/v1/supervisions/analytics/export-report');assert.equal(r.status,200);assert.ok(r.text.includes(outside.course.courseCode));return {http:200,outsideCourseIncluded:true};
    },'documented-scope-exception');
    await check('主任学生底库全量读取范围例外',async()=>{const students=ok(await api(director,'/api/student/list'));assert.ok(students.length>0);return {count:students.length,noDepartmentFilter:true};},'documented-scope-exception');
    for(const a of [teacher,director,supervisor]) {
      await check(`${a.username}微格跨课程挂载读取删除范围例外`,async()=>{
        const courseId=a===teacher||a===director?outside.course.id:common.course.id;
        const r=ok(await api(a,'/api/v1/resources/micro-slices','POST',{courseId,videoTitle:'合成范围测试-'+stamp,bopppsStage:'B',durationSeconds:30,sliceUrl:'https://example.invalid/synthetic.mp4',sourceAgent:'role-connectivity-test'}));
        const list=ok(await api(a,`/api/v1/resources/micro-slices/course/${courseId}`));assert.ok(list.some(s=>s.id===r.id));ok(await api(a,`/api/v1/resources/micro-slices/${r.id}`,'DELETE'));return {http:200,courseId,metadataOnly:true};
      },'documented-scope-exception');
    }
    await check('真实浏览器演示模拟流到考勤归档',async()=>{
      await teacher.page.getByRole('button',{name:/课堂智能考勤大屏/}).click();
      await teacher.page.getByRole('heading',{name:/课堂智能考勤与态势监控大屏/}).waitFor();
      await teacher.page.locator('select').first().selectOption(String(common.id));
      const [start]=await Promise.all([teacher.page.waitForResponse(r=>r.url().includes('/api/v1/attendance/start')),teacher.page.getByRole('button',{name:'演示模拟流',exact:true}).click()]);
      const session=ok({status:start.status(),body:await start.json()});
      await teacher.page.waitForResponse(r=>r.url().includes('/api/visual/report/stream')&&r.status()===200,{timeout:15000});
      await shot(teacher,'31-attendance-simulation');
      const [finish]=await Promise.all([teacher.page.waitForResponse(r=>r.url().includes('/api/v1/attendance/finish')),teacher.page.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click()]);
      const saved=ok({status:finish.status(),body:await finish.json()});assert.equal(saved.status,'FINISHED');assert.equal(saved.id,session.id);
      assert.ok(ok(await api(teacher,`/api/v1/attendance/offering/${common.id}`)).some(s=>s.id===session.id&&s.status==='FINISHED'));
      await teacher.page.getByRole('button',{name:/考勤归档记录/}).click();await teacher.page.getByRole('heading',{name:/课程考勤历史归档记录/}).waitFor();await shot(teacher,'32-attendance-archive');
      return {sessionId:session.id,status:saved.status,actual:saved.actualCount,cameraHardwareTested:false};
    });
    // These checks express the chart's boundaries. Unexpected success is a failure.
    // Seed via the legitimate teacher so fixing the exploit cannot skip follow-up checks.
    const foreignSession=ok(await api(foreign,'/api/v1/attendance/start','POST',{offeringId:outside.id})).id;
    await check('教师不能启动未关联班次考勤',async()=>{
      const r=await api(teacher,'/api/v1/attendance/start','POST',{offeringId:outside.id,operatorRole:'TEACHER',operatorName:'合成越权测试'});
      denied(r);
    });
    await check('主任不能读取外室班次考勤记录',async()=>denied(await api(director,`/api/v1/attendance/offering/${outside.id}`)));
    await check('教师不能看到外班当前考勤会话',async()=>{const r=await api(teacher,'/api/v1/attendance/current');if(r.status===403)return;const s=ok(r);assert.ok(!s||own.some(o=>o.id===s.offering.id),`Leaked outside offering ${s?.offering?.id}`);});
      await check('督导撤销授权后不能更新外班考勤',async()=>{assert.ok(limited);denied(await api(limited,`/api/v1/attendance/live-update?sessionId=${foreignSession}&actualCount=1&lookupRate=50`,'POST'));});
      await check('主任不能结束外室班次考勤',async()=>denied(await api(director,'/api/v1/attendance/finish','POST',{sessionId:foreignSession,actualCount:1,avgLookupRate:50})));
    await check('考勤操作人身份由服务端可信绑定',async()=>{
      const r=ok(await api(teacher,'/api/v1/attendance/start','POST',{offeringId:common.id,operatorRole:'DIRECTOR',operatorName:'伪造主任'}));assert.equal(r.operatorRole,'TEACHER','Teacher impersonated DIRECTOR');
    });
    await check('归档班次禁止重新启动考勤',async()=>{
      const archived=ok(await api(director,`/api/v1/courses/offerings/${common.id}/archive`,'POST'));assert.equal(archived.isSnapshotFrozen,true);
      rejected(await api(teacher,'/api/v1/attendance/start','POST',{offeringId:common.id}),409);
    });
    await check('冻结班次禁止考勤实时更新',async()=>{
      const sessions=ok(await api(teacher,`/api/v1/attendance/offering/${common.id}`));const active=sessions.find(s=>s.status==='ACTIVE');assert.ok(active,'Expected isolated pre-existing active session');
      rejected(await api(teacher,`/api/v1/attendance/live-update?sessionId=${active.id}&actualCount=2&lookupRate=60`,'POST'),409);
    });
    await check('冻结班次不能在考勤页面开启模拟流',async()=>{
      await teacher.page.reload();await teacher.page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
      const [response]=await Promise.all([teacher.page.waitForResponse(r=>r.url().includes('/api/v1/courses/offerings')&&r.request().method()==='GET'),teacher.page.getByRole('button',{name:/课堂智能考勤大屏/}).click()]);
      const listed=ok({status:response.status(),body:await response.json()});
      await teacher.page.getByRole('heading',{name:/课堂智能考勤与态势监控大屏/}).waitFor();
      const option=teacher.page.locator(`select option[value="${common.id}"]`);
      if(!listed.some(o=>o.id===common.id))return {hidden:true,confirmedByLoadedOfferingList:true};
      await option.waitFor({state:'attached'});
      await teacher.page.locator('select').first().selectOption(String(common.id));await shot(teacher,'33-frozen-attendance');assert.ok(await teacher.page.getByRole('button',{name:'演示模拟流',exact:true}).isDisabled(),'Frozen class simulation button remains enabled');
    });
  } finally {
    fs.writeFileSync(path.join(evidence,'boundary-results.json'),JSON.stringify({browserVersion:browser?.version(),frontend:state.frontend,backend:state.backend,mocked:false,results,pageErrors:errors},null,2));
    fs.writeFileSync(path.join(evidence,'boundary-http.json'),JSON.stringify(requests,null,2));
    await browser?.close();
    if (results.some(r => r.status === 'FAIL')) process.exitCode = 1;
  }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
