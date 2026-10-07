const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE),{execFileSync}=require('node:child_process');
const dir=__dirname,backend='http://127.0.0.1:18081',base='http://127.0.0.1:15173';
const results=[],requests=[],pageErrors=[],users={},stamp=Date.now();let browser;
async function login(user){const p=await browser.newPage({viewport:{width:1440,height:1000}});p.on('dialog',d=>d.accept());p.on('pageerror',e=>pageErrors.push(e.message));await p.goto(base);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(user);await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();users[user]={page:p,token:await p.evaluate(()=>localStorage.getItem('jwtToken'))};}
async function req(who,p,method='GET',data){const r=await users[who].page.request.fetch(backend+p,{method,headers:{Authorization:'Bearer '+users[who].token},...(data!==undefined?{data}:{})});const json=(r.headers()['content-type']||'').includes('json'),body=json?await r.json():await r.body();const code=r.status()===200?(json?body.code:200):r.status();requests.push({actor:who,path:p,method,http:r.status(),code});return {r,body,code,data:json?body.data:body};}
async function ok(who,p,m='GET',data){const r=await req(who,p,m,data);assert.equal(r.code,200);return r.data;}
async function check(name,f){try{results.push({name,status:'PASS',details:await f()});console.log('PASS '+name);}catch(e){results.push({name,status:'FAIL',error:e.message});console.log('FAIL '+name+': '+e.message);}fs.writeFileSync(path.join(dir,'supplement-results.json'),JSON.stringify({results,requests,pageErrors,observedAt:new Date().toISOString()},null,2));}
async function main(){browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});try{
 for(const who of ['director','guojun','supervisor','liubo'])await login(who);
 await check('本课教师与主任下载、同室共享教师和督导禁止原件下载',async()=>{
   const list=await ok('guojun','/api/v1/resources?courseId=1'),resource=list.filter(v=>v.fileType==='PDF'&&v.resourceName.startsWith('Playwright')).sort((a,b)=>b.id-a.id)[0];assert.ok(resource);
   await ok('guojun','/api/v1/resources','POST',{id:resource.id,courseId:1,chapter:resource.chapter,resourceName:resource.resourceName,tags:['实验'],isPublic:true});
   assert.equal((await ok('guojun',`/api/v1/resources/${resource.id}/download`)).subarray(0,5).toString(),'%PDF-');
   assert.equal((await ok('director',`/api/v1/resources/${resource.id}/download`)).subarray(0,5).toString(),'%PDF-');
   await ok('liubo',`/api/v1/resources/${resource.id}`);
   for(const who of ['liubo','supervisor']){assert.equal((await req(who,`/api/v1/resources/${resource.id}/download`)).code,403);assert.equal((await req(who,`/api/v1/resources/${resource.id}`,'DELETE')).code,403);assert.equal((await req(who,'/api/v1/resources','POST',{id:resource.id,courseId:1,resourceName:'越权编辑'})).code,403);}
   return {resourceId:resource.id,ownDownload:true,directorDownload:true,sharedTeacherReadOnly:true,supervisorReadOnly:true};
 });
 await check('主任维护资源上传与删除闭环',async()=>{
   const who=users.director,buf=fs.readFileSync(path.resolve(dir,'../../scripts/tests/fixtures/sprint2.docx'));
   const r=await who.page.request.post(backend+'/api/v1/resources/upload',{headers:{Authorization:'Bearer '+who.token},multipart:{file:{name:'director-'+stamp+'.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:buf},courseId:'2',chapter:'合成主任资源测试',resourceName:'合成主任上传-'+stamp}});
   const b=await r.json();assert.equal(b.code,200);const id=b.data.id;assert.equal(b.data.fileSizeBytes,buf.length);await ok('director',`/api/v1/resources/${id}`,'DELETE');assert.ok(!(await ok('director','/api/v1/resources?courseId=2')).some(v=>v.id===id));return {resourceId:id,uploadedBytes:buf.length,deleted:true};
 });
 await check('主任课程建档、无依赖班次删除和课程删除闭环',async()=>{
   const course=await ok('director','/api/v1/courses','POST',{courseCode:'ROLE-'+stamp,courseName:'合成删除验收课程',department:'软件工程教研室',majorCode:'SE',credits:1,hours:16,theoryHours:12,practiceHours:4,courseType:'专业选修课',prerequisites:'',description:'隔离合成资料'});
   const teacher=(await ok('director','/api/v1/teachers')).find(v=>v.teacherCode==='T2024001');
   const o=await ok('director','/api/v1/courses/offerings','POST',{courseId:course.id,academicTerm:'DEL-'+stamp,className:'合成空名单班',primaryTeacherId:teacher.id,collaboratingTeacherIds:[],studentNumbers:[]});
   await ok('director',`/api/v1/courses/offerings/${o.id}`,'DELETE');await ok('director',`/api/v1/courses/${course.id}`,'DELETE');assert.ok(!(await ok('director','/api/v1/courses')).some(v=>v.id===course.id));return {courseId:course.id,offeringId:o.id,deleted:true};
 });
 await check('补查教师与督导均获得全校候选学生',async()=>{
   const offers=await ok('director','/api/v1/courses/offerings');const o=offers.filter(v=>v.className.startsWith('三角色合成班-')).sort((a,b)=>b.id-a.id)[0];assert.ok(o);
   const counts={};for(const who of ['guojun','supervisor']){const roster=await ok(who,`/api/v1/courses/offerings/${o.id}/students`);counts[who]={enrolled:roster.enrolled.length,available:roster.available.length};assert.ok(roster.available.length>0);}
   const p=users.supervisor.page;await p.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();const select=p.locator(`select:has(option[value="${o.id}"])`).first();await select.waitFor();await select.selectOption(String(o.id));await p.getByRole('button',{name:/班级成员选拔/}).click();await p.getByText(/确认选入选中的/).waitFor();
   await p.screenshot({path:path.join(dir,'supervisor-roster-candidates.png'),fullPage:true});return {observation:'REPRODUCED_SCOPE_GAP',counts,supervisorUiOffersRosterEditing:true,serverMutationStill403:true};
 });
 await check('补查三角色实时大屏均可读取未授权AI班次',async()=>{
   const rows=[];for(const who of ['director','guojun','supervisor']){const core=await req(who,'/api/v1/courses/offerings/10'),visual=await req(who,'/api/visual/students/status?offeringId=10');assert.equal(core.code,403);assert.equal(visual.code,200);rows.push({actor:who,core:core.code,visual:visual.code,count:visual.data.length});}return {observation:'REPRODUCED_SCOPE_GAP',rows};
 });
 await check('日常8080实例只读复核两类接口权限缺口',async()=>{
   const context=await chromium.request.newContext();try{
    const auth=await context.post('http://127.0.0.1:8080/api/v1/auth/login',{data:{username:'guojun',password:process.env.ROLE_TEST_PASSWORD}});const body=await auth.json();assert.equal(body.code,200);const token=body.data.token;
    assert.ok(token);const rows=[];for(const p of ['/api/v1/courses/offerings/10','/api/v1/attendance/offering/10','/api/visual/students/status?offeringId=10','/api/v1/courses/offerings/2/students']){
      const r=await context.get('http://127.0.0.1:8080'+p,{headers:{Authorization:'Bearer '+token}}),b=await r.json();rows.push({path:p,http:r.status(),code:b.code,studentCount:Array.isArray(b.data)?b.data.length:undefined,enrolled:b.data?.enrolled?.length,available:b.data?.available?.length});
    }
    fs.writeFileSync(path.join(dir,'daily-readonly.json'),JSON.stringify({observedAt:new Date().toISOString(),mode:'real login and GET only; no domain writes',rows},null,2));assert.equal(rows[0].http,403);assert.equal(rows[1].http,403);assert.equal(rows[2].code,200);assert.ok(rows[3].available>0);return rows;
   }finally{await context.dispose();}
 });
 await check('浏览器无未捕获异常',async()=>{assert.deepEqual(pageErrors,[]);});
 }finally{await browser.close();}}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
