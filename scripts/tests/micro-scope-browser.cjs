// Real logged-in Chrome -> Vite proxy -> isolated production backend; no mocked HTTP responses.
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const {execFileSync} = require('node:child_process');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
const evidence = path.resolve(process.env.MICRO_SCOPE_EVIDENCE_DIR);
const state = JSON.parse(fs.readFileSync(path.join(evidence, 'environment.json'), 'utf8').replace(/^\uFEFF/, ''));
assert.match(state.mysql, /^classroom-exp3-browser-[a-f0-9]{10}$/);
const base = 'http://127.0.0.1:15173', actors = {}, results = [], requests = [], pageErrors = [];
const stamp = Date.now(), stage = 'scope-' + stamp;
let browser, ownClip, ownCourse, foreignClip;
function sql(query) { return execFileSync('docker', ['exec','-e','MYSQL_PWD='+process.env.ROLE_TEST_MYSQL_PASSWORD,state.mysql,'mysql','-uroot','--default-character-set=utf8mb4','classroom_ai','-N','-e',query], {encoding:'utf8'}).trim(); }
function count() { return Number(sql('SELECT COUNT(*) FROM t_micro_teaching_slice')); }
async function api(who, route, method='GET', data) {
  const r = await actors[who].evaluate(async ({route,method,data}) => {
    const response = await fetch(route, {method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),...(data!==undefined?{'Content-Type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});
    const text=await response.text();let body;try{body=JSON.parse(text);}catch{body=text;}
    return {http:response.status,body};
  }, {route,method,data});
  requests.push({actor:who,route,method,http:r.http,code:r.body?.code});return r;
}
async function ok(who,route,method='GET',data) { const r=await api(who,route,method,data);assert.equal(r.http,200,route);if(typeof r.body==='object'){assert.equal(r.body.code,200,r.body.message);return r.body.data;}return r.body; }
async function deny(who,route,method='GET',data) { const r=await api(who,route,method,data);assert.equal(r.http,403,`${who} ${route}`);assert.equal(r.body.code,403); }
const payload=courseId=>({courseId,videoTitle:'合成微格权限-'+stamp,bopppsStage:stage,durationSeconds:20,sliceUrl:'/synthetic/not-played.mp4',sourceAgent:'micro-scope-test'});
async function login(who) {
  const context=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Shanghai'});
  const page=await context.newPage();page.on('pageerror',e=>pageErrors.push({actor:who,message:e.message}));page.on('dialog',d=>d.accept());
  await page.goto(base);await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(who);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
  await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();actors[who]=page;
}
async function check(name,fn) {
  try { results.push({name,status:'PASS',details:await fn()});console.log('PASS '+name); }
  catch(e) { results.push({name,status:'FAIL',error:e.stack});console.log('FAIL '+name+': '+e.message);process.exitCode=1; }
  fs.writeFileSync(path.join(evidence,'browser-results.json'), JSON.stringify({observedAt:new Date().toISOString(),mode:'Real authenticated Chrome fetch through Vite proxy, Spring Boot and isolated MySQL; micro-slice metadata endpoints, no video playback',results,requests,pageErrors},null,2));
}
async function main() {
  browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
  fs.writeFileSync(path.join(evidence,'browser-version.json'),JSON.stringify({chromeVersion:browser.version()},null,2));
  try {
    for(const who of ['director','guojun','supervisor','jiangly','director_base']) await login(who);
    const teacherCourses=await ok('guojun','/api/v1/courses'),directorCourses=await ok('director','/api/v1/courses'),supervisorCourses=await ok('supervisor','/api/v1/courses');
    ownCourse=teacherCourses.find(c=>directorCourses.some(d=>d.id===c.id)&&supervisorCourses.some(s=>s.id===c.id));assert.ok(ownCourse);
    for(const who of ['director','guojun','supervisor']) assert.ok(!(await ok(who,'/api/v1/courses')).some(c=>c.id===10));
    foreignClip=Number(sql(`INSERT INTO t_micro_teaching_slice(course_id,video_title,boppps_stage,duration_seconds,slice_url,source_agent) VALUES(10,'synthetic-${stamp}','${stage}',20,'/synthetic/foreign-unplayed.mp4','test'); SELECT LAST_INSERT_ID();`));assert.ok(foreignClip>0);
    await check('匿名查询、按环节查询、挂载、删除均401',async()=>{
      const req=actors.director.request;
      for(const [route,method,data] of [[`/course/${ownCourse.id}`,'GET'],[`/stage/${stage}`,'GET'],['','POST',payload(ownCourse.id)],[`/${foreignClip}`,'DELETE']]) {
        const r=await req.fetch(base+'/api/v1/resources/micro-slices'+route,{method,...(data?{data}:{})});assert.equal(r.status(),401);
      }
    });
    await check('主任本室与教师本人课程可以挂载，真实数据库新增',async()=>{
      const before=count();ownClip=await ok('director','/api/v1/resources/micro-slices','POST',payload(ownCourse.id));
      const clip=await ok('guojun','/api/v1/resources/micro-slices','POST',payload(ownCourse.id));assert.equal(count(),before+2);
      await ok('guojun',`/api/v1/resources/micro-slices/${clip.id}`,'DELETE');assert.equal(count(),before+1);return {ownCourseId:ownCourse.id,clipId:ownClip.id};
    });
    await check('三角色授权课程读取成功',async()=>{
      for(const who of ['director','guojun','supervisor']) assert.ok((await ok(who,`/api/v1/resources/micro-slices/course/${ownCourse.id}`)).some(c=>c.id===ownClip.id));
    });
    await check('三角色直接访问外课和切片ID均403，数据库不变',async()=>{
      const before=count();
      for(const who of ['director','guojun','supervisor']) {
        await deny(who,'/api/v1/resources/micro-slices/course/10');await deny(who,'/api/v1/resources/micro-slices','POST',payload(10));
        await deny(who,`/api/v1/resources/micro-slices/${foreignClip}?courseId=${ownCourse.id}`,'DELETE');
      }
      assert.equal(count(),before);return {unauthorizedCourseId:10,foreignSliceStillExists:Number(sql(`SELECT COUNT(*) FROM t_micro_teaching_slice WHERE id=${foreignClip}`))===1};
    });
    await check('督导对已授权课程仅可读，不可挂载或删除',async()=>{
      const before=count();await deny('supervisor','/api/v1/resources/micro-slices','POST',payload(ownCourse.id));
      await deny('supervisor',`/api/v1/resources/micro-slices/${ownClip.id}`,'DELETE');assert.equal(count(),before);
    });
    await check('按教学环节查询不泄漏外课切片',async()=>{
      for(const who of ['director','guojun','supervisor']) {
        const readable=new Set((await ok(who,'/api/v1/courses')).map(c=>c.id));
        const rows=await ok(who,`/api/v1/resources/micro-slices/stage/${stage}`);
        assert.ok(rows.some(r=>r.id===ownClip.id));assert.ok(!rows.some(r=>r.id===foreignClip));assert.ok(rows.every(r=>readable.has(r.course.id)));
      }
    });
    await check('跨教研室协同教师可维护本人关联课程',async()=>{
      const teachers=await ok('director','/api/v1/teachers');
      const primary=teachers.find(t=>t.teacherCode==='T2024001'),collaborator=teachers.find(t=>t.teacherCode==='T2024002');assert.ok(primary&&collaborator);
      await ok('director','/api/v1/courses/offerings','POST',{courseId:ownCourse.id,academicTerm:'MICRO-'+stamp,className:'合成微格协同-'+stamp,primaryTeacherId:primary.id,collaboratingTeacherIds:[collaborator.id],studentNumbers:[]});
      const clip=await ok('jiangly','/api/v1/resources/micro-slices','POST',payload(ownCourse.id));
      assert.ok((await ok('jiangly',`/api/v1/resources/micro-slices/course/${ownCourse.id}`)).some(s=>s.id===clip.id));
      await ok('jiangly',`/api/v1/resources/micro-slices/${clip.id}`,'DELETE');
    });
    await check('收回专业授权后旧登录立即不可读，环节列表为空',async()=>{
      const who='micro_scope_'+stamp;
      const user=await ok('director','/api/v1/director/supervisors','POST',{username:who,password:process.env.ROLE_TEST_PASSWORD,realName:'合成微格督导',authorizedMajors:ownCourse.majorCode});
      await login(who);assert.ok((await ok(who,`/api/v1/resources/micro-slices/course/${ownCourse.id}`)).some(s=>s.id===ownClip.id));
      await ok('director',`/api/v1/director/supervisors/${user.id}/majors`,'PUT',{authorizedMajors:''});
      await deny(who,`/api/v1/resources/micro-slices/course/${ownCourse.id}`);assert.deepEqual(await ok(who,`/api/v1/resources/micro-slices/stage/${stage}`),[]);
    });
    await check('Demo主任CSV与学生底库保留全量，教师督导仍禁止专用接口',async()=>{
      const csv=await ok('director','/api/v1/supervisions/analytics/export-report');assert.ok(csv.includes(sql('SELECT course_code FROM t_course WHERE id=10')));
      const all=await ok('director','/api/student/list'),other=await ok('director_base','/api/student/list');assert.deepEqual(all.map(s=>s.id).sort(),other.map(s=>s.id).sort());
      for(const who of ['guojun','supervisor']) {await deny(who,'/api/v1/supervisions/analytics/export-report');await deny(who,'/api/student/list');}
      return {csvIncludesOtherDepartments:true,globalStudentCount:all.length};
    });
    await check('主任删除本室切片，教师删除本人课程切片',async()=>{
      await ok('director',`/api/v1/resources/micro-slices/${ownClip.id}`,'DELETE');
      const clip=await ok('guojun','/api/v1/resources/micro-slices','POST',payload(ownCourse.id));
      await ok('guojun',`/api/v1/resources/micro-slices/${clip.id}`,'DELETE');assert.equal(Number(sql(`SELECT COUNT(*) FROM t_micro_teaching_slice WHERE id IN (${ownClip.id},${clip.id})`)),0);
    });
    for(const who of ['director','guojun','supervisor']) await actors[who].screenshot({path:path.join(evidence,who+'-authenticated.png'),fullPage:true});
    await check('浏览器无未捕获异常',async()=>assert.deepEqual(pageErrors,[]));
  } finally { await browser.close(); }
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
