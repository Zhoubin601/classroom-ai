const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const {execFileSync, execFile} = require('node:child_process');
const {promisify} = require('node:util');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
const dir = __dirname, base = 'http://127.0.0.1:15173', backend = 'http://127.0.0.1:18081';
const state = JSON.parse(fs.readFileSync(path.join(dir,'environment.json'),'utf8').replace(/^\uFEFF/,''));
assert.match(state.mysql,/^classroom-exp3-browser-[a-f0-9]{10}$/);
const results=[], requests=[], pageErrors=[], dialogs=[], actors={};
const stamp=Date.now(); let browser, offerings=[], students=[];
const dbArgs=q=>['exec','-e','MYSQL_PWD='+process.env.ROLE_TEST_MYSQL_PASSWORD,state.mysql,'mysql','-uroot','--default-character-set=utf8mb4','classroom_ai','-N','-e',q];
const sql=q=>execFileSync('docker',dbArgs(q),{encoding:'utf8'}).trim();
const records=()=>Number(sql('SELECT COUNT(*) FROM classroom_record'));
async function req(who,p,method='GET',data) {
  const r=await actors[who].page.request.fetch(backend+p,{method,headers:{Authorization:'Bearer '+actors[who].token},...(data!==undefined?{data}:{})});
  const bytes=await r.body(); const isJson=(r.headers()['content-type']||'').includes('json');
  const body=isJson?JSON.parse(bytes.toString()):bytes;
  const code=r.status()===200?(isJson?body.code:200):r.status();
  requests.push({actor:who,path:p,method,http:r.status(),code,contentType:r.headers()['content-type']});
  return {r,code,data:isJson?body.data:body};
}
async function ok(who,p,m='GET',data) {const r=await req(who,p,m,data);assert.equal(r.code,200,p);return r.data;}
async function deny(who,p,m='GET',data,code=403) {const r=await req(who,p,m,data);assert.equal(r.code,code,p);}
async function check(name,f) {
  try {results.push({name,status:'PASS',details:await f()});console.log('PASS '+name);}
  catch(e){results.push({name,status:'FAIL',error:e.stack});process.exitCode=1;console.log('FAIL '+name+': '+e.message);}
  fs.writeFileSync(path.join(dir,'fix-results.json'),JSON.stringify({observedAt:new Date().toISOString(),mode:'real Chrome + Vue + Spring Boot + isolated MySQL/Redis; no mocked APIs',results,requests,pageErrors,dialogs},null,2));
}
async function login(who) {
  const p=await browser.newPage({viewport:{width:1440,height:1000}});
  p.on('pageerror',e=>pageErrors.push({actor:who,message:e.message}));
  p.on('dialog',async d=>{dialogs.push({actor:who,message:d.message()});await d.accept();});
  await p.goto(base);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(who);
  await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
  await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();
  actors[who]={page:p,token:await p.evaluate(()=>localStorage.getItem('jwtToken'))};
}
async function dashboard(who,id) {
  const p=actors[who].page;await p.reload();
  await p.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();
  const select=p.locator(`select:has(option[value="${id}"])`).first();
  await select.waitFor();await select.selectOption(String(id));
  await p.waitForTimeout(400);return p;
}
const frame=(id,ids=students.slice(0,2).map(s=>s.studentId),rate=.5)=>({offeringId:id,sessionId:'FIX-'+stamp,lookupRate:rate,presentStudentIds:ids,studentPoses:Object.fromEntries(ids.map(s=>[s,'UP']))});
function pdf() {
  const stream='BT /F1 14 Tf 40 100 Td (Fix verification) Tj ET\n';
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`,'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  let body='%PDF-1.4\n';const offsets=[];
  objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(body));body+=`${i+1} 0 obj\n${o}\nendobj\n`;});
  const x=Buffer.byteLength(body);body+=`xref\n0 6\n0000000000 65535 f \n`+offsets.map(o=>String(o).padStart(10,'0')+' 00000 n \n').join('')+`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${x}\n%%EOF\n`;
  return Buffer.from(body);
}
async function main() {
 browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
 try {
  for(const who of ['director','guojun','supervisor','jiangly','liubo'])await login(who);
  const teacher=(await ok('director','/api/v1/teachers')).find(t=>t.teacherCode==='T2024001');
  const collaborator=(await ok('director','/api/v1/teachers')).find(t=>t.teacherCode==='T2024002');
  students=(await ok('director','/api/student/list')).slice(0,6);assert.equal(students.length,6);
  for(let i=0;i<4;i++)offerings.push(await ok('director','/api/v1/courses/offerings','POST',{courseId:2,academicTerm:'FIX-'+stamp,className:'修复合成同名班-'+stamp,primaryTeacherId:teacher.id,collaboratingTeacherIds:[collaborator.id],studentNumbers:i===3?[]:students.slice(0,5).map(s=>s.studentId)}));
  const [a,b,c,empty]=offerings.map(o=>o.id);
  await check('三角色所有实时入口越权 403 且数据库不变',async()=>{
    const before=records();
    for(const who of ['director','guojun','supervisor']) {
      for(const p of ['overview','trend','students/status'])await deny(who,`/api/visual/${p}?offeringId=10`);
      await deny(who,'/api/visual/report/stream','POST',frame(10));await deny(who,'/api/visual/reset?offeringId=10','POST');
    }
    assert.equal(records(),before);return {classroomRecordsUnchanged:true,roles:3,endpoints:5};
  });
  await check('缺失班次拒绝400，不通过同名班或全局活动会话猜测',async()=>{
    const before=records();for(const p of ['overview','trend','students/status'])await deny('guojun','/api/visual/'+p,'GET',undefined,400);
    await deny('guojun','/api/visual/reset','POST',undefined,400);
    await deny('guojun','/api/visual/report/stream','POST',{className:offerings[0].className,presentStudentIds:[students[0].studentId],lookupRate:.5},400);
    assert.equal(records(),before);return {missingOffering400:true,sameNameDoesNotGrantAccess:true};
  });
  await check('三角色与跨室协作教师可操作获授权未冻结班次',async()=>{
    for(const who of ['director','guojun','supervisor','jiangly']) {
      await ok(who,'/api/v1/attendance/start','POST',{offeringId:a});
      await ok(who,'/api/visual/report/stream','POST',frame(a));
      assert.equal((await ok(who,`/api/visual/overview?offeringId=${a}`)).currentPresent,2);
      assert.equal((await ok(who,`/api/visual/students/status?offeringId=${a}`)).length,5);
    }
    await deny('liubo',`/api/visual/students/status?offeringId=${a}`);return {authorizedRolesPass:true,unassignedTeacher403:true};
  });
  await check('同名双班概览、趋势、学生姿态及重置相互隔离',async()=>{
    await ok('guojun','/api/visual/report/stream','POST',frame(a,students.slice(0,2).map(s=>s.studentId),.3));
    await ok('guojun','/api/visual/report/stream','POST',frame(b,students.slice(0,4).map(s=>s.studentId),.8));
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${a}`)).currentPresent,2);
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${b}`)).currentPresent,4);
    assert.equal((await ok('guojun',`/api/visual/trend?offeringId=${a}`)).at(-1).lookupRate,30);
    assert.equal((await ok('guojun',`/api/visual/trend?offeringId=${b}`)).at(-1).lookupRate,80);
    await ok('guojun',`/api/visual/reset?offeringId=${a}`,'POST');
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${a}`)).currentPresent,0);
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${b}`)).currentPresent,4);
    assert.equal((await ok('guojun',`/api/visual/students/status?offeringId=${b}`)).filter(s=>s.present).length,4);
    return {twoOfferingIds:[a,b],sameClassName:true,resetOnlySelected:true};
  });
  await check('空名单真实人数为0；旁听编号不泄漏其他学生姓名头像',async()=>{
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${empty}`)).totalRegistered,0);
    assert.deepEqual(await ok('guojun',`/api/visual/students/status?offeringId=${empty}`),[]);
    await ok('guojun','/api/visual/report/stream','POST',frame(a,[students[5].studentId]));
    const status=await ok('guojun',`/api/visual/students/status?offeringId=${a}`);const audit=status.find(s=>s.isAuditing);
    assert.ok(audit);assert.ok(!audit.name.includes(students[5].name));assert.ok(!audit.avatarUrl);return {zeroRoster:true,auditingProfileHidden:true};
  });
  await check('教师督导仅本班名单，主任保留选入移出闭环',async()=>{
    for(const who of ['guojun','supervisor']) {
      const roster=await ok(who,`/api/v1/courses/offerings/${a}/students`);assert.equal(roster.enrolled.length,5);assert.deepEqual(roster.available,[]);
      const p=await dashboard(who,a);await p.getByRole('button',{name:/班级学生名单/}).click();
      await p.getByRole('heading',{name:/班级学生花名册/}).waitFor();
      assert.equal(await p.getByRole('button',{name:/从总档案库选入学生/}).count(),0);
      assert.equal(await p.getByRole('button',{name:'移出班级',exact:true}).count(),0);
      await p.screenshot({path:path.join(dir,who+'-roster-readonly.png'),fullPage:true});
      await deny(who,`/api/v1/courses/offerings/${a}/students/add`,'POST',[students[5].studentId]);
    }
    const roster=await ok('director',`/api/v1/courses/offerings/${a}/students`);assert.ok(roster.available.length>0);
    assert.equal((await ok('director',`/api/v1/courses/offerings/${a}/students/add`,'POST',[students[5].studentId])).studentCount,6);
    assert.equal((await ok('director',`/api/v1/courses/offerings/${a}/students/remove/${students[5].studentId}`,'POST')).studentCount,5);
    const p=await dashboard('director',a);await p.getByRole('button',{name:/班级成员选拔/}).click();
    await p.getByRole('button',{name:/从总档案库选入学生/}).waitFor();
    assert.ok(await p.getByRole('button',{name:/从总档案库选入学生/}).isVisible());
    await p.screenshot({path:path.join(dir,'director-roster-management.png'),fullPage:true});return {teacherAndSupervisorAvailable:0,directorAddRemovePass:true};
  });
  await check('实机模拟、下课归档和历史查询正常闭环',async()=>{
    const p=await dashboard('guojun',a);
    await p.getByRole('button',{name:'演示模拟流',exact:true}).click();
    await p.getByRole('button',{name:'暂停模拟',exact:true}).waitFor();
    await p.waitForTimeout(2600);
    assert.equal((await ok('guojun',`/api/visual/overview?offeringId=${a}`)).currentPresent,4);
    await p.screenshot({path:path.join(dir,'teacher-simulation-live.png'),fullPage:true});
    await p.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click();
    await p.waitForTimeout(900);assert.ok((await ok('guojun',`/api/v1/attendance/offering/${a}`)).some(s=>s.status==='FINISHED'&&s.actualCount===4));
    return {actualCount:4,archivedAttendance:true};
  });
  await check('旧页面启动时被归档：真实409后不启动定时推流',async()=>{
    const p=await dashboard('guojun',a);let posts=0;
    const listener=r=>{if(r.url().includes('/api/visual/report/stream')&&r.method()==='POST')posts++;};p.on('request',listener);
    await ok('director',`/api/v1/courses/offerings/${a}/archive`,'POST');
    const before=records();await p.getByRole('button',{name:'演示模拟流',exact:true}).click();await p.waitForTimeout(3500);
    assert.equal(posts,0);assert.equal(records(),before);assert.equal(await p.getByRole('button',{name:'暂停模拟',exact:true}).count(),0);
    assert.ok(dialogs.some(d=>d.actor==='guojun'&&d.message.includes('启动模拟失败')));
    await p.screenshot({path:path.join(dir,'failed-start-stopped.png'),fullPage:true});p.off('request',listener);return {startRejected409:true,streamPosts:0,recordsUnchanged:true};
  });
  await check('三角色冻结班只读，接口409无写入，界面禁用模拟和摄像头',async()=>{
    const before=records();for(const who of ['director','guojun','supervisor']) {
      await deny(who,'/api/visual/report/stream','POST',frame(a),409);await deny(who,`/api/visual/reset?offeringId=${a}`,'POST',undefined,409);
      assert.deepEqual((await ok(who,`/api/v1/courses/offerings/${a}/students`)).available,[]);
      const p=await dashboard(who,a);
      assert.ok(await p.getByRole('button',{name:'演示模拟流',exact:true}).isDisabled());
      assert.ok(await p.getByRole('button',{name:'打开摄像头开启考勤',exact:true}).isDisabled());
      assert.ok(await p.getByRole('button',{name:'结束考勤并归档下课',exact:true}).isDisabled());
      await p.screenshot({path:path.join(dir,who+'-frozen-readonly.png'),fullPage:true});
    }
    assert.equal(records(),before);return {roles:3,frozenWrite409:true,disabledUi:true};
  });
  await check('模拟运行中班次归档，下一帧409后自动停止',async()=>{
    const p=await dashboard('guojun',b);await p.getByRole('button',{name:'演示模拟流',exact:true}).click();await p.getByRole('button',{name:'暂停模拟',exact:true}).waitFor();
    await p.waitForTimeout(1800);await ok('director',`/api/v1/courses/offerings/${b}/archive`,'POST');
    const before=records();await p.waitForTimeout(3700);
    assert.equal(records(),before);assert.equal(await p.getByRole('button',{name:'暂停模拟',exact:true}).count(),0);
    assert.ok(dialogs.some(d=>d.message.includes('模拟推流已停止')));return {streamStoppedOn409:true,recordsUnchangedAfterFreeze:true};
  });
  await check('真实MySQL并发冻结与推流共用行锁，不使用旧实体绕过',async()=>{
    const lock='fix_visual_'+stamp;
    const blocker=promisify(execFile)('docker',dbArgs(`START TRANSACTION; SELECT id FROM t_course_offering WHERE id=${c} FOR UPDATE; SELECT GET_LOCK('${lock}',0); SELECT SLEEP(3); UPDATE t_course_offering SET is_snapshot_frozen=1 WHERE id=${c}; COMMIT; SELECT RELEASE_LOCK('${lock}');`));
    for(let i=0;i<20;i++){if(sql(`SELECT IS_USED_LOCK('${lock}')`)!=='NULL')break;await new Promise(r=>setTimeout(r,100));}
    assert.notEqual(sql(`SELECT IS_USED_LOCK('${lock}')`),'NULL');
    const before=records();await deny('guojun','/api/visual/report/stream','POST',frame(c),409);await blocker;
    assert.equal(records(),before);return {blockedUntilFreezeCommit:true,rejected409:true};
  });
  await check('真实PDF/DOCX/PPTX下载MIME和字节正确，教师督导禁止越权原件',async()=>{
    const fixtures=path.resolve(dir,'../../scripts/tests/fixtures');
    for(const [ext,bytes,mime] of [['pdf',pdf(),'application/pdf'],['docx',fs.readFileSync(path.join(fixtures,'sprint2.docx')),'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],['pptx',fs.readFileSync(path.join(fixtures,'sprint2.pptx')),'application/vnd.openxmlformats-officedocument.presentationml.presentation']]) {
      const up=await actors.guojun.page.request.post(backend+'/api/v1/resources/upload',{headers:{Authorization:'Bearer '+actors.guojun.token},multipart:{file:{name:'fix-'+stamp+'.'+ext,mimeType:mime,buffer:bytes},courseId:'2',chapter:'修复验证',resourceName:'FIX-'+ext+'-'+stamp,tags:'理论',isPublic:'true'}});
      const uploaded=await up.json();assert.equal(uploaded.code,200);const id=uploaded.data.id;
      for(const who of ['guojun','director']) {const r=await req(who,`/api/v1/resources/${id}/download`);assert.equal(r.code,200);assert.equal(r.r.headers()['content-type'].split(';')[0],mime);assert.ok(r.r.headers()['content-disposition'].startsWith('attachment;'));assert.deepEqual(r.data,bytes);}
      for(const who of ['supervisor','liubo'])await deny(who,`/api/v1/resources/${id}/download`);
    }
    return {types:['PDF','DOCX','PPTX'],bytesMatch:true,downloadScopeUnchanged:true};
  });
  await check('浏览器无未捕获异常',async()=>{assert.deepEqual(pageErrors,[]);});
 } finally {await browser.close();}
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
