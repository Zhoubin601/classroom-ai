const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
const {randomBytes}=require('node:crypto');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
const root=__dirname, base='http://127.0.0.1:15173', backend='http://127.0.0.1:18081';
const state=JSON.parse(fs.readFileSync(path.join(root,'environment.json'),'utf8').replace(/^\uFEFF/,''));
assert.match(state.mysql,/^classroom-exp3-browser-[a-f0-9]{10}$/);
const evidence=path.join(root,'boundaries-attempt2');fs.mkdirSync(evidence,{recursive:true});
const results=[],requests=[],pageErrors=[],dialogs=[],actors={};
const stamp=Date.now(),term='E2E-'+stamp;
let browser,fixture={},activePage;
function sql(query){return execFileSync('docker',['exec','-e','MYSQL_PWD='+process.env.ROLE_TEST_MYSQL_PASSWORD,state.mysql,'mysql','-uroot','--default-character-set=utf8mb4','classroom_ai','-N','-e',query],{encoding:'utf8'}).trim();}
async function request(actor,p,method='GET',data){
  const c=actor?actors[actor].page.request:browser.contexts()[0].request;
  const r=await c.fetch(backend+p,{method,headers:actor?{Authorization:'Bearer '+actors[actor].token}:{},...(data!==undefined?{data}:{})});
  const type=r.headers()['content-type']||'';let body;
  if(type.includes('json'))body=await r.json();else body=await r.text();
  const code=r.status()!==200?r.status():(body&&typeof body==='object'?body.code:200);
  requests.push({actor:actor||'anonymous',path:p,method,http:r.status(),code,message:typeof body==='object'?body.message:undefined});
  return {response:r,body,code,data:body&&typeof body==='object'?body.data:body};
}
async function good(actor,p,method='GET',data){const r=await request(actor,p,method,data);assert.equal(r.code,200,`${actor} ${method} ${p}: ${r.body?.message}`);return r.data;}
async function deny(actor,p,method='GET',data,code=403){const r=await request(actor,p,method,data);assert.equal(r.code,code,`${actor} ${method} ${p}: got ${r.code}`);return {http:r.response.status(),code:r.code,message:r.body?.message};}
async function check(name,fn){const at=Date.now();try{const details=await fn();results.push({name,status:'PASS',details,ms:Date.now()-at});console.log('PASS '+name);}catch(e){results.push({name,status:'FAIL',error:e.message,ms:Date.now()-at});console.log('FAIL '+name+': '+e.message);if(activePage&&!activePage.isClosed())await activePage.screenshot({path:path.join(evidence,`failure-${results.length}.png`),fullPage:true}).catch(()=>{});}fs.writeFileSync(path.join(evidence,'results.json'),JSON.stringify({observedAt:new Date().toISOString(),mode:'Real Chrome / Vue / Spring Boot / isolated MySQL and Redis; no API mocks',results,requests,pageErrors,dialogs},null,2));}
async function login(name,password=process.env.ROLE_TEST_PASSWORD){
  assert.ok(password,'ROLE_TEST_PASSWORD required');
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
  page.on('dialog',async d=>{dialogs.push({actor:name,type:d.type(),message:d.message()});await d.accept();});
  page.on('pageerror',e=>pageErrors.push({actor:name,message:e.message}));
  await page.goto(base);await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(name);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(password);
  await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await page.getByRole('button',{name:'退出登录',exact:true}).waitFor({timeout:20000});
  const token=await page.evaluate(()=>localStorage.getItem('jwtToken'));actors[name]={page,token};
  actors[name].user=await good(name,'/api/v1/auth/me');return page;
}
async function shot(page,name){await page.screenshot({path:path.join(evidence,name+'.png'),fullPage:true,animations:'disabled'});}
const evalPayload=()=>({offeringId:fixture.offering.id,listenTopic:'三角色实机验收-'+stamp,evaluateDate:'2026-10-07',scoreAttitude:15,scoreContent:15,scoreMethod:15,scoreEffect:15,highlights:'合成亮点一\n合成亮点二\n合成亮点三',suggestions:'合成建议：增加课堂练习',isDraft:false,supervisorName:'伪造身份'});
async function main(){
 browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
 try{
  for(const name of ['director','guojun','supervisor','liubo','jiangly','director_base','director_arch'])await login(name);
  const d=actors.director.page,t=actors.guojun.page,s=actors.supervisor.page;activePage=t;
  await check('匿名请求被拦截',async()=>{for(const p of ['/api/v1/courses','/api/v1/attendance/current','/api/v1/resources/micro-slices/course/1','/api/student/list','/api/visual/overview?offeringId=1'])await deny(null,p,'GET',undefined,401);});
  await check('课程与班次列表遵守三角色范围',async()=>{
    for(const who of ['director','guojun','supervisor']){
      const courses=await good(who,'/api/v1/courses'),offers=await good(who,'/api/v1/courses/offerings');assert.ok(courses.length&&offers.length);
      if(who==='director')assert.ok(courses.every(c=>c.department===actors[who].user.department)&&offers.every(o=>o.course.department===actors[who].user.department));
      if(who==='guojun')assert.ok(offers.every(o=>o.teacherCode===actors[who].user.teacherCode||(o.teachers||[]).some(v=>v.teacherCode===actors[who].user.teacherCode)));
      if(who==='supervisor')assert.ok(courses.every(c=>['SE','CS'].includes(c.majorCode))&&offers.every(o=>['SE','CS'].includes(o.majorCode||o.course.majorCode)));
    }
    return {directorCourses:(await good('director','/api/v1/courses')).length,teacherOfferings:(await good('guojun','/api/v1/courses/offerings')).length,supervisorOfferings:(await good('supervisor','/api/v1/courses/offerings')).length};
  });
  await check('绕过列表直接访问外部课程与班次被拒绝',async()=>{
    for(const who of ['director','guojun','supervisor'])for(const p of ['/api/v1/courses/10','/api/v1/courses/offerings/10','/api/v1/attendance/offering/10','/api/v1/attendance/current?offeringId=10'])await deny(who,p);
  });
  await check('教师督导不能建档导入排课或调整授权',async()=>{
    for(const who of ['guojun','supervisor']){
      await deny(who,'/api/v1/courses','POST',{});await deny(who,'/api/v1/courses/2','DELETE');await deny(who,'/api/v1/courses/import/template');
      await deny(who,'/api/v1/courses/offerings','POST',{});await deny(who,'/api/v1/schedules','POST',{});
      await deny(who,'/api/v1/director/supervisors');await deny(who,'/api/v1/director/supervisors/2/majors','PUT',{authorizedMajors:'AI'});
    }
  });
  await check('主任创建班次与跨室协作关系真实入库',async()=>{
    const teachers=await good('director','/api/v1/teachers'),students=await good('director','/api/student/list');
    fixture.teacher=teachers.find(v=>v.teacherCode==='T2024001');fixture.collaborator=teachers.find(v=>v.teacherCode==='T2024002');
    assert.ok(fixture.teacher&&fixture.collaborator);fixture.students=students.slice(0,6);assert.equal(fixture.students.length,6);
    fixture.offering=await good('director','/api/v1/courses/offerings','POST',{courseId:2,academicTerm:term,className:'三角色合成班-'+stamp,primaryTeacherId:fixture.teacher.id,collaboratingTeacherIds:[fixture.collaborator.id],studentNumbers:fixture.students.slice(0,5).map(v=>v.studentId)});
    assert.equal(fixture.offering.studentCount,5);const collaborator=await good('jiangly','/api/v1/courses/offerings');assert.ok(collaborator.some(o=>o.id===fixture.offering.id));
    return {offeringId:fixture.offering.id,teacherIds:[fixture.teacher.id,fixture.collaborator.id],studentCount:5};
  });
  await check('主任排课冲突与删除操作闭环',async()=>{
    const payload={offeringId:fixture.offering.id,classroom:'E2E-A447',startWeek:1,endWeek:16,dayOfWeek:3,startPeriod:3,endPeriod:4};
    const sched=await good('director','/api/v1/schedules','POST',payload);
    await deny('director','/api/v1/schedules','POST',payload,409);
    await deny('guojun',`/api/v1/schedules/${sched.id}`,'DELETE');await deny('supervisor',`/api/v1/schedules/${sched.id}`,'DELETE');
    await good('director',`/api/v1/schedules/${sched.id}`,'DELETE');
    fixture.schedule=await good('director','/api/v1/schedules','POST',payload);return {scheduleId:fixture.schedule.id,conflictHttp:409};
  });
  await check('主任花名册添加移出与人数同步',async()=>{
    const id=fixture.offering.id,num=fixture.students[5].studentId;
    assert.equal((await good('director',`/api/v1/courses/offerings/${id}/students/add`,'POST',[num])).studentCount,6);
    assert.equal((await good('director',`/api/v1/courses/offerings/${id}/students/remove/${num}`,'POST')).studentCount,5);
    await deny('guojun',`/api/v1/courses/offerings/${id}/students/add`,'POST',[num]);await deny('supervisor',`/api/v1/courses/offerings/${id}/students/add`,'POST',[num]);
    assert.equal((await good('director',`/api/v1/courses/offerings/${id}/students`)).enrolled.length,5);
  });
  await check('教师正式发布、草稿隔离与再次发布实机闭环',async()=>{
    await t.reload();const select=t.locator(`select:has(option[value="${fixture.offering.id}"])`).first();await select.selectOption(String(fixture.offering.id));
    const save=t.getByRole('button',{name:'暂存草稿',exact:true});await save.waitFor();
    await t.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='暂存草稿'&&!b.disabled));
    const intro=t.getByPlaceholder('请输入课程背景、学科定位、主要授课内容概括...');
    await intro.fill('合成发布简介-'+stamp);await t.getByPlaceholder('例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%').fill('合成考核说明');
    await t.getByPlaceholder('明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标...').fill('合成课程教学目标');
    const [publishedResponse]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/courses/2/content/publish')&&r.request().method()==='POST'),t.getByRole('button',{name:'正式发布',exact:true}).click()]);
    assert.equal((await publishedResponse.json()).code,200);const original=await good('supervisor','/api/v1/courses/2/content/published');assert.equal(original.description,'合成发布简介-'+stamp);
    await t.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='暂存草稿'&&!b.disabled));
    await intro.fill('合成未发布修改-'+stamp);
    const [saved]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/courses/2/content/draft')&&r.request().method()==='PUT'),save.click()]);assert.equal((await saved.json()).code,200);
    assert.equal((await good('supervisor','/api/v1/courses/2/content/published')).description,original.description);
    for(const who of ['director','supervisor']){await deny(who,'/api/v1/courses/2/content/draft');await deny(who,'/api/v1/courses/2/content/publish','POST',{});}
    const [republished]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/courses/2/content/publish')&&r.request().method()==='POST'),t.getByRole('button',{name:'正式发布',exact:true}).click()]);
    assert.equal((await republished.json()).code,200);const latest=await good('supervisor','/api/v1/courses/2/content/published');assert.equal(latest.publishVersion,original.publishVersion+1);assert.equal(latest.description,'合成未发布修改-'+stamp);await shot(t,'teacher-published-content');
    return {firstPublishVersion:original.publishVersion,latestPublishVersion:latest.publishVersion};
  });
  await check('培养方案牵头写入、参与读取、教师督导禁止导入',async()=>{
    const catalog=[{indicatorCode:'ROLE-1',requirementCategory:'工程知识',indicatorDescription:'合成目录指标'}],v='ROLE-'+stamp;
    fixture.plan=v;await good('director',`/api/v1/syllabus/plans/SE/${v}/indicators`,'PUT',catalog);
    for(const who of ['guojun','supervisor'])await deny(who,`/api/v1/syllabus/plans/SE/${v}/indicators`,'PUT',catalog);
    const cs='CS-ROLE-'+stamp;await good('director_arch',`/api/v1/syllabus/plans/CS/${cs}/indicators`,'PUT',catalog);
    assert.equal((await good('director_base',`/api/v1/syllabus/plans/CS/${cs}/indicators`)).length,1);
    await deny('director_base',`/api/v1/syllabus/plans/CS/${cs}/indicators`,'PUT',catalog);
    await deny('director',`/api/v1/syllabus/plans/AI/${v}/indicators`,'PUT',catalog);
  });
  await check('课程映射维护、重复校验与主任审查锁定',async()=>{
    fixture.syllabus=await good('guojun','/api/v1/syllabus/course/2/from-plan','POST',{syllabusVersion:fixture.plan,planVersion:fixture.plan});
    let indicators=await good('guojun','/api/v1/syllabus/course/2/indicators');assert.equal(indicators.length,1);
    const mapping={indicatorCode:'ROLE-1',requirementCategory:'工程知识',indicatorDescription:'合成课程指标',supportWeight:'H',targetGoal:'课程目标2'};
    const added=await good('guojun','/api/v1/syllabus/course/2/indicators','POST',mapping);
    await deny('guojun','/api/v1/syllabus/course/2/indicators','POST',mapping,400);
    await good('guojun',`/api/v1/syllabus/indicators/${added.id}`,'PUT',{...mapping,supportWeight:'L'});
    await good('guojun',`/api/v1/syllabus/indicators/${added.id}`,'DELETE');
    await deny('supervisor','/api/v1/syllabus/course/2/indicators','POST',mapping);
    const lock=`/api/v1/syllabus/${fixture.syllabus.id}/lock?lockedBy=spoofed`;
    await deny('guojun',lock,'POST');assert.equal((await good('director',lock,'POST')).status,'LOCKED');
    await deny('guojun','/api/v1/syllabus/course/2/indicators','POST',mapping,409);
    assert.equal((await good('supervisor','/api/v1/syllabus/course/2/latest')).status,'LOCKED');
    await deny('director',`/api/v1/syllabus/plans/SE/${fixture.plan}/indicators`,'PUT',[{indicatorCode:'ROLE-1',requirementCategory:'工程知识',indicatorDescription:'尝试覆盖引用目录'}],409);
    return {syllabusId:fixture.syllabus.id,status:'LOCKED'};
  });
  await check('主任督导建档与授权不扩张专业范围',async()=>{
    const who='role_sup_'+stamp,pwd=randomBytes(12).toString('hex');fixture.secondSupervisor=who;
    const account=await good('director','/api/v1/director/supervisors','POST',{username:who,password:pwd,realName:'合成验收督导',authorizedMajors:'SE'});
    await login(who,pwd);fixture.supervisorId=account.id;assert.equal(actors[who].user.authorizedMajors,'SE');
    await deny('director',`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:'AI'});
    await good('director_arch',`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:'CS'});
    const user=await good(who,'/api/v1/auth/me');assert.ok(user.authorizedMajors.includes('CS')&&user.authorizedMajors.includes('SE'));
    await good('director',`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:''});
    assert.equal((await good(who,'/api/v1/auth/me')).authorizedMajors,'CS');
    await deny(who,'/api/v1/courses/2');await good('director',`/api/v1/director/supervisors/${account.id}/majors`,'PUT',{authorizedMajors:'SE'});
    return {createdUserId:account.id,oldTokenReadsCurrentGrant:true};
  });
  await check('评价暂存、驳回、原督导重提与审核日志闭环',async()=>{
    fixture.evaluation=await good('supervisor','/api/v1/supervisions','POST',{...evalPayload(),isDraft:true});
    assert.equal(fixture.evaluation.status,'DRAFT');assert.equal(fixture.evaluation.supervisorName,actors.supervisor.user.realName);
    await deny(fixture.secondSupervisor,'/api/v1/supervisions','POST',{...evalPayload(),id:fixture.evaluation.id,isDraft:true});
    fixture.evaluation=await good('supervisor','/api/v1/supervisions','POST',{...evalPayload(),id:fixture.evaluation.id});
    assert.equal(fixture.evaluation.status,'PENDING_REVIEW');
    await deny('guojun','/api/v1/supervisions','POST',evalPayload());await deny('supervisor',`/api/v1/supervisions/${fixture.evaluation.id}/review`,'POST',{approved:true});
    await deny('director_arch',`/api/v1/supervisions/${fixture.evaluation.id}/review`,'POST',{approved:true});
    const rejected=await good('director',`/api/v1/supervisions/${fixture.evaluation.id}/review`,'POST',{approved:false,note:'合成审核批注：补充课堂例证'});assert.equal(rejected.status,'REJECTED');
    assert.equal((await good('supervisor','/api/v1/supervisions','POST',{...evalPayload(),id:fixture.evaluation.id})).status,'PENDING_REVIEW');
    fixture.approved=await good('director',`/api/v1/supervisions/${fixture.evaluation.id}/review`,'POST',{approved:true,note:'合成审核批注'});
    assert.equal(fixture.approved.status,'APPROVED_PENDING');
    const logs=await good('director',`/api/v1/supervisions/${fixture.evaluation.id}/audit`);assert.ok(logs.some(l=>l.action==='REJECT')&&logs.some(l=>l.action==='APPROVE'));
    await deny('guojun',`/api/v1/supervisions/${fixture.evaluation.id}/audit`);return {evaluationId:fixture.evaluation.id,auditActions:logs.map(l=>l.action)};
  });
  await check('审核24小时延迟及教师匿名反馈雷达词云',async()=>{
    const id=fixture.evaluation.id;assert.ok(!((await good('guojun',`/api/v1/supervisions?offeringId=${fixture.offering.id}`)).some(e=>e.id===id)));
    const due=new Date(fixture.approved.publishTime+'+08:00').getTime();assert.ok(due>Date.now()+23*3600000);
    assert.ok(Number.isSafeInteger(id));sql(`UPDATE t_supervision_evaluation SET publish_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=${id}`);
    const feedback=(await good('guojun',`/api/v1/supervisions?offeringId=${fixture.offering.id}`)).find(e=>e.id===id);assert.ok(feedback);
    assert.equal(feedback.supervisorName,'匿名督导');assert.equal(feedback.supervisorUserId,null);assert.equal(feedback.reviewedBy,null);assert.equal(feedback.reviewNote,null);
    const radar=await good('guojun','/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent(actors.guojun.user.realName));
    assert.ok(radar.highlightList.some(v=>v.includes('合成亮点一'))&&radar.wordCloud.some(v=>v.name==='合成亮点一'));
    await deny('guojun','/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent(actors.liubo.user.realName));
    await t.reload();await t.getByText(/合成亮点一/).first().waitFor();await shot(t,'anonymous-feedback-and-word-cloud');
    return {publishTimeAdvancedOnlyInDisposableDb:true,anonymous:true,radarEvaluationCount:radar.evaluationCount};
  });
  await check('评价草稿与驳回稿删除、已提交评价禁止删除',async()=>{
    const draft=await good('supervisor','/api/v1/supervisions','POST',{...evalPayload(),isDraft:true});await good('supervisor',`/api/v1/supervisions/${draft.id}`,'DELETE');
    const e=await good('supervisor','/api/v1/supervisions','POST',evalPayload());await good('director',`/api/v1/supervisions/${e.id}/review`,'POST',{approved:false,note:'合成驳回删除测试'});await good('supervisor',`/api/v1/supervisions/${e.id}`,'DELETE');
    await deny('supervisor',`/api/v1/supervisions/${fixture.evaluation.id}`,'DELETE',undefined,409);
  });
  await check('主任督导覆盖明细与低分预警、教师拒绝管理接口',async()=>{
    for(const who of ['director','supervisor']){
      const rows=await good(who,'/api/v1/supervisions/analytics/coverage?term='+term);assert.ok(rows.some(r=>r.courseId===2&&r.evaluationIds.includes(fixture.evaluation.id)));
      assert.ok((await good(who,'/api/v1/supervisions/analytics/alerts')).some(r=>r.courseId===2&&r.alertType==='LOW_SCORE_WARNING'));
      const dashboard=await good(who,'/api/v1/supervisions/analytics/dashboard?term='+term);assert.ok(dashboard.coverageRate>0);
    }
    for(const p of ['dashboard','coverage','alerts'])await deny('guojun','/api/v1/supervisions/analytics/'+p);
  });
  await check('三角色真实考勤开始更新结束与服务端身份',async()=>{
    for(const who of ['director','guojun','supervisor','jiangly']){
      const session=await good(who,'/api/v1/attendance/start','POST',{offeringId:fixture.offering.id,operatorName:'伪造名字',operatorRole:'ADMIN'});assert.equal(session.operatorRole,actors[who].user.role);assert.equal(session.operatorName,actors[who].user.realName);assert.equal(session.expectedCount,5);
      const current=await good(who,'/api/v1/attendance/current?offeringId='+fixture.offering.id);assert.equal(current.id,session.id);
      await deny('liubo','/api/v1/attendance/live-update?sessionId='+session.id+'&actualCount=99','POST');await deny('liubo','/api/v1/attendance/finish','POST',{sessionId:session.id,actualCount:99});
      const live=await good(who,`/api/v1/attendance/live-update?sessionId=${session.id}&actualCount=4&lookupRate=75`,'POST');assert.equal(live.attendanceRate,80);
      const finish=await good(who,'/api/v1/attendance/finish','POST',{sessionId:session.id,actualCount:4,avgLookupRate:75,operatorRole:'ADMIN'});assert.equal(finish.status,'FINISHED');assert.equal(finish.operatorRole,actors[who].user.role);
      assert.equal(await good(who,'/api/v1/attendance/current?offeringId='+fixture.offering.id),null);
    }
    return {offeringId:fixture.offering.id,roles:['DIRECTOR','TEACHER','SUPERVISOR'],collaborator:true,expectedCount:5,actualCount:4,attendanceRate:80};
  });
  await check('真实浏览器演示流、Redis大屏与MySQL归档闭环',async()=>{
    activePage=t;await t.reload();await t.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();
    const select=t.locator(`select:has(option[value="${fixture.offering.id}"])`).first();await select.selectOption(String(fixture.offering.id));
    const [start]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/attendance/start')&&r.request().method()==='POST'),t.getByRole('button',{name:'演示模拟流',exact:true}).click()]);assert.equal((await start.json()).code,200);
    const session=(await start.json()).data;
    await t.waitForResponse(r=>r.url().endsWith('/visual/report/stream')&&r.request().method()==='POST'&&r.status()===200,{timeout:15000});
    const overview=await good('guojun','/api/visual/overview?offeringId='+fixture.offering.id);assert.equal(overview.currentPresent,4);assert.equal(overview.auditingCount,1);assert.equal(overview.attendanceRate,80);
    await t.waitForFunction(()=>document.body.innerText.includes('80%')||document.body.innerText.includes('80.0%'),{},{timeout:15000});await shot(t,'attendance-real-simulation');
    const [finished]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/attendance/finish')&&r.request().method()==='POST'),t.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click()]);assert.equal((await finished.json()).code,200);
    const saved=(await good('guojun','/api/v1/attendance/offering/'+fixture.offering.id)).find(v=>v.id===session.id);assert.equal(saved.status,'FINISHED');assert.equal(saved.actualCount,4);assert.equal(saved.attendanceRate,80);
    await t.getByRole('button',{name:/考勤归档记录/}).click();await t.getByText(/80/).first().waitFor();await shot(t,'attendance-persisted-history');return {sessionId:saved.id,currentPresent:4,auditingCount:1,attendanceRate:80};
  });
  await check('冻结教学班拒绝三角色考勤写入',async()=>{
    const archived=await good('director',`/api/v1/courses/offerings/${fixture.offering.id}/archive`,'POST');assert.equal(archived.isSnapshotFrozen,true);assert.equal(archived.snapshotStudentCount,5);
    for(const who of ['director','guojun','supervisor'])await deny(who,'/api/v1/attendance/start','POST',{offeringId:fixture.offering.id},409);
    const history=await good('guojun','/api/v1/courses/offerings/history?term='+term);assert.equal(history.cumulativePersonTimes,5);
    await deny('director',`/api/v1/courses/offerings/${fixture.offering.id}/students/add`,'POST',[fixture.students[5].studentId],409);
  });
  await check('冻结班次不应在大屏继续启用模拟写入',async()=>{
    activePage=t;await t.reload();await t.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();const select=t.locator(`select:has(option[value="${fixture.offering.id}"])`).first();
    await select.waitFor();await select.selectOption(String(fixture.offering.id));await shot(t,'frozen-offering-controls');
    const enabled=await t.getByRole('button',{name:'演示模拟流',exact:true}).isEnabled();
    if(enabled){const [start,stream]=await Promise.all([t.waitForResponse(r=>r.url().endsWith('/attendance/start')&&r.request().method()==='POST'),t.waitForResponse(r=>r.url().endsWith('/visual/report/stream')&&r.request().method()==='POST',{timeout:12000}),t.getByRole('button',{name:'演示模拟流',exact:true}).click()]);
      const a=await start.json(),b=await stream.json();requests.push({actor:'guojun',path:'frozen-offering UI simulation',method:'POST',attendanceHttp:start.status(),attendanceCode:a.code,visualHttp:stream.status(),visualCode:b.code});await shot(t,'frozen-simulation-running');await t.getByRole('button',{name:'暂停模拟',exact:true}).click();assert.fail(`冻结班次仍可启动模拟：attendance HTTP ${start.status()} / code ${a.code}；visual HTTP ${stream.status()} / code ${b.code}`);
    }
  });
  await check('质量CSV导出仅主任可用但实际覆盖跨室班次（图中例外）',async()=>{
    const report=await good('director','/api/v1/supervisions/analytics/export-report');assert.ok(report.includes('AI3001')&&report.includes('CS2002'));
    for(const who of ['guojun','supervisor'])await deny(who,'/api/v1/supervisions/analytics/export-report');return {directorCsvIncludesOtherDepartments:true};
  });
  await check('学生底库仅主任可用但实际未隔离教研室（图中例外）',async()=>{
    const a=await good('director','/api/student/list'),b=await good('director_base','/api/student/list');assert.deepEqual(a.map(v=>v.id).sort(),b.map(v=>v.id).sort());
    for(const who of ['guojun','supervisor'])await deny(who,'/api/student/list');return {sameGlobalStudentCount:a.length};
  });
  await check('微格跨课程读取挂载删除对三角色开放（图中例外）',async()=>{
    for(const who of ['director','guojun','supervisor']){
      const clip=await good(who,'/api/v1/resources/micro-slices','POST',{courseId:10,videoTitle:'合成越界切片-'+stamp,bopppsStage:'P',durationSeconds:20,sliceUrl:'/synthetic/no-video.mp4'});
      assert.ok((await good(who,'/api/v1/resources/micro-slices/course/10')).some(v=>v.id===clip.id));await good(who,`/api/v1/resources/micro-slices/${clip.id}`,'DELETE');
    }return {foreignCourseId:10,metadataOnly:true,videoPlaybackNotTested:true};
  });
  await check('实时大屏读取必须与考勤会话使用同一班次范围',async()=>{
    await deny('guojun','/api/v1/attendance/offering/10');
    const overview=await request('guojun','/api/visual/overview?offeringId=10'),students=await request('guojun','/api/visual/students/status?offeringId=10');
    assert.equal(overview.code,403,`未授权AI班次大屏overview返回${overview.code}，学生状态返回${students.code}，明细${Array.isArray(students.data)?students.data.length:0}条`);
  });
  await check('实时推流必须拒绝未授权班次写入',async()=>{
    const before=Number(sql('SELECT COUNT(*) FROM classroom_record'));
    const r=await request('guojun','/api/visual/report/stream','POST',{offeringId:10,sessionId:'scope-probe-'+stamp,courseName:'合成越权测试',className:'合成越权班',detectedPersonCount:0,lookupCount:0,lookdownCount:0,lookupRate:0,presentStudentIds:[],studentPoses:{}});
    const after=Number(sql('SELECT COUNT(*) FROM classroom_record'));assert.equal(r.code,403,`非授权班次推流返回${r.code}，持久记录增量${after-before}`);
  });
  await check('实时重置必须拒绝未授权班次写入',async()=>{await deny('guojun','/api/visual/reset?offeringId=10','POST');});
  await check('教师督导名册读取不应暴露全校候选学生',async()=>{
    for(const who of ['guojun','supervisor']){const roster=await good(who,`/api/v1/courses/offerings/${fixture.offering.id}/students`);assert.equal((roster.available||[]).length,0,`${who}可读取${roster.enrolled.length}名本班学生及${roster.available.length}名全校候选学生`);}
  });
  await check('浏览器没有未捕获JavaScript异常',async()=>{assert.deepEqual(pageErrors,[]);});
 }finally{if(browser)await browser.close();}
 console.log(JSON.stringify({passed:results.filter(v=>v.status==='PASS').length,failed:results.filter(v=>v.status==='FAIL').length}));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
