// Read-only UI/API acceptance for labelled seed data. Credentials/tokens remain in RAM.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),mark='DEMO-DATA-20261012-v1';
const evidence=path.resolve(process.env.DATA_EVIDENCE_DIR||path.join(root,'docs/20261012-data-completeness-evidence-v1/isolated-browser'));
const backend=process.env.DATA_BACKEND_URL||'http://127.0.0.1:18099',frontend=process.env.DATA_FRONTEND_URL||'http://127.0.0.1:15189';
const source=fs.readFileSync(path.join(root,'backend/src/main/java/com/classroom/ai/runner/TeachingDataInitializer.java'),'utf8');
const password=process.env.DATA_DEMO_PASSWORD||source.match(/passwordEncoder\.encode\("([^"\n]+)"\)/)[1];
const tokens={},proof=[],requests=[];let browser;
async function api(role,route,method='GET',body){
 const r=await fetch(backend+route,{method,headers:{...(tokens[role]?{Authorization:'Bearer '+tokens[role]}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
 const bytes=Buffer.from(await r.arrayBuffer());let data;try{data=JSON.parse(bytes.toString())}catch{}
 if(!route.endsWith('/login'))requests.push({role,route:route.replace(/\/preview\/.*/,'/preview/[REDACTED]'),status:r.status,code:data?.code,bytes:bytes.length});
 return {status:r.status,data:data?.data,code:data?.code,bytes};
}
async function login(role){const r=await api(role,'/api/v1/auth/login','POST',{username:role,password});assert.equal(r.code,200,'Login failed for '+role);tokens[role]=r.data.token;}
async function check(name,fn){await fn();proof.push(name);}
async function main(){
 fs.mkdirSync(evidence,{recursive:true});
 for(const role of ['guojun','jiangly','dongxm','director','supervisor','chenlx'])await login(role);
 const offerings=(await api('supervisor','/api/v1/courses/offerings')).data;
 const demo=offerings.filter(o=>o.className.includes(mark));assert.equal(demo.length,7);
 const byMajor={};for(const o of demo)if(o.status==='IN_PROGRESS')byMajor[o.majorCode]=o;
 await check('五专业、三学期、三开课状态和12人选课名单',async()=>{
  assert.deepEqual(Object.keys(byMajor).sort(),['AI','CS','DS','SE','SEC']);
  for(const o of demo){const r=await api('supervisor',`/api/v1/courses/offerings/${o.id}`);assert.equal(r.code,200);assert.equal(r.data.studentNumbers.length,12);assert.equal(o.studentCount,12);assert.ok(r.data.studentNumbers.every(n=>n.startsWith('DEMO-DATA-')));assert.equal(o.majorId,o.course.majorId);}
 });
 await check('历史归档人数、冻结快照和联合授课',async()=>{
  const frozen=demo.find(o=>o.isSnapshotFrozen);assert.ok(frozen);assert.equal(frozen.snapshotStudentCount,12);assert.equal(JSON.parse(frozen.historySnapshot).studentCount,12);
  const history=await api('guojun','/api/v1/courses/offerings/history?term='+encodeURIComponent('2025-2026春季'));assert.equal(history.code,200);assert.ok(history.data.items.some(o=>o.offeringId===frozen.id&&o.studentCount===12&&o.isSnapshotFrozen));
  assert.equal(byMajor.SE.teachers.length,2);assert.ok(byMajor.SE.teachers.some(t=>t.roleInOffering==='ASSISTANT'));
 });
 await check('历史考勤应到、实到、缺勤与出勤率一致',async()=>{
  for(const o of demo.filter(o=>o.status!=='PENDING')){const r=await api('supervisor',`/api/v1/attendance/offering/${o.id}`);assert.equal(r.code,200);assert.equal(r.data.length,2);for(const s of r.data){assert.equal(s.status,'FINISHED');assert.equal(s.expectedCount,12);assert.equal(s.absentStudentIds.split(',').length,12-s.actualCount);assert.ok(Math.abs(s.attendanceRate-s.actualCount*100/12)<.11);assert.ok(s.endTime>s.startTime);}}
 });
 const evaluations=(await api('supervisor','/api/v1/supervisions')).data.filter(e=>e.listenTopic.includes(mark));
 await check('评价状态、权重快照、24小时延迟和教师脱敏',async()=>{
  assert.equal(evaluations.length,5);assert.equal(evaluations.filter(e=>e.status==='PUBLISHED').length,2);
  const pending=evaluations.find(e=>e.status==='APPROVED_PENDING');assert.equal((await api('dongxm',`/api/v1/supervisions/${pending.id}`)).status,403);
  const released=evaluations.find(e=>e.offering.id===byMajor.SE.id);const seen=await api('guojun',`/api/v1/supervisions/${released.id}`);assert.equal(seen.code,200);assert.equal(seen.data.supervisorUserId,null);assert.equal(seen.data.reviewedBy,null);assert.equal(seen.data.supervisorName,'匿名督导');
  assert.equal(released.totalScore,released.scoreAttitude+released.scoreContent+released.scoreMethod+released.scoreEffect);
 });
 await check('质量统计、雷达、问题分类和行动反馈历史',async()=>{
  const radar=await api('guojun','/api/v1/sprint3/radar?term='+encodeURIComponent('2026-2027秋季'));assert.equal(radar.code,200);assert.ok(radar.data.axes.some(a=>a.sampleCount>0));
  const suggestions=await api('guojun','/api/v1/sprint3/suggestions/mine');assert.equal(suggestions.code,200);const s=suggestions.data.find(s=>s.text.includes('该评语为脚本合成'));assert.ok(s);assert.ok(s.feedback.actionText.includes(mark));assert.equal(s.history.length,1);
  const quality=await api('director','/api/v1/sprint3/quality?term='+encodeURIComponent('2026-2027秋季'));assert.equal(quality.code,200);assert.ok(quality.data.rows.some(r=>r.courseCode==='CS1001'&&r.validTotalCount>0));
  const problems=await api('supervisor','/api/v1/sprint3/problems?term='+encodeURIComponent('2026-2027秋季'));assert.equal(problems.code,200);assert.ok(problems.data.groups.length>0);
 });
 const resources=(await api('guojun','/api/v1/resources?courseId='+byMajor.SE.course.id)).data;
 const resource=resources.find(r=>r.fileUrl.endsWith(mark+'-SE-v2.pdf'));assert.ok(resource);
 await check('真实课件、水印预览、两版历史及共享授权',async()=>{
  const history=await api('guojun',`/api/v1/resources/${resource.id}/history`);assert.equal(history.code,200);assert.equal(history.data.versions.length,2);
  const download=await api('guojun',`/api/v1/resources/${resource.id}/download`);assert.equal(download.status,200);assert.ok(download.bytes.subarray(0,5).toString()==='%PDF-');
  const ticket=await api('guojun',`/api/v1/resources/${resource.id}/preview-ticket`,'POST');assert.equal(ticket.code,200);
  const preview=await api('guojun',ticket.data.url);assert.equal(preview.status,200);assert.equal(preview.bytes.subarray(0,5).toString(),'%PDF-');
  fs.writeFileSync(path.join(evidence,'authorized-watermark-preview.pdf'),preview.bytes);
  assert.equal((await api('chenlx',`/api/v1/resources/${resource.id}`)).status,403);
 });
 await check('微格纯色视频资产及三个有效片段',async()=>{
  const assets=await api('guojun','/api/v1/resources/micro-assets?courseId='+byMajor.SE.course.id);assert.equal(assets.code,200);assert.ok(assets.data.some(a=>a.storedName===mark+'.mp4'&&a.durationMs===4000));
  const sliceResponse=await api('guojun','/api/v1/resources/micro-slices/course/'+byMajor.SE.course.id);assert.equal(sliceResponse.code,200);
  const slices=sliceResponse.data.filter(s=>s.videoTitle.includes(mark));assert.equal(slices.length,3);
  for(const s of slices){const video=await api('guojun',`/api/v1/resources/micro-slices/${s.id}/video`);assert.equal(video.status,200);assert.equal(video.bytes.subarray(4,8).toString(),'ftyp');}
 });
 await check('课程、大纲、专业目录与权限隔离',async()=>{
  const c=byMajor.SE.course;assert.equal((await api('guojun',`/api/v1/syllabus/course/${c.id}/indicators`)).code,200);
  const draft=await api('guojun',`/api/v1/courses/${c.id}/content/draft`);assert.equal(draft.code,200);assert.ok(draft.data.assessmentMethod.includes('合成演示'));assert.equal(draft.data.status,'DRAFT');
  assert.equal((await api('chenlx','/api/v1/courses/'+c.id)).status,403);assert.equal((await api('','/api/v1/courses')).status,401);
 });
 if(process.env.DATA_API_ONLY!=='true'){
  const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
  browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
  const errors=[];
  async function pageFor(role){const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();page.setDefaultTimeout(20000);page.on('pageerror',e=>errors.push({role,error:e.message}));await page.goto(frontend);await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(role);await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(password);await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();return page;}
  await check('真实教师页面显示合成建议、反馈和雷达图',async()=>{const page=await pageFor('guojun');await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByLabel('学期',{exact:true}).fill('2026-2027秋季');await page.getByRole('button',{name:'查询',exact:true}).click();await page.locator('form').filter({hasText:'该评语为脚本合成'}).first().waitFor();await page.waitForFunction(()=>document.querySelectorAll('canvas').length>0);await page.screenshot({path:path.join(evidence,'teacher-feedback.png'),fullPage:true});await page.context().close();});
  await check('真实主任页面展示质量样本',async()=>{const page=await pageFor('director');await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByLabel('学期',{exact:true}).fill('2026-2027秋季');await page.getByRole('button',{name:'查询',exact:true}).click();await page.getByText('CS1001 软件工程导论',{exact:true}).first().waitFor();await page.screenshot({path:path.join(evidence,'director-quality.png'),fullPage:true});await page.context().close();});
  await check('真实督导页面展示已审核统计与课程类别',async()=>{const page=await pageFor('supervisor');await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByLabel('学期',{exact:true}).fill('2026-2027秋季');await page.getByRole('button',{name:'查询',exact:true}).click();await page.getByRole('heading',{name:'专业核心课',exact:true}).waitFor();await page.screenshot({path:path.join(evidence,'supervisor-statistics.png'),fullPage:true});await page.context().close();});
  await check('真实课件水印预览和历史版本页面',async()=>{const page=await pageFor('guojun');await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByLabel('当前资源').selectOption(String(resource.id));await page.getByRole('button',{name:'授权水印预览',exact:true}).last().click();await page.waitForFunction(()=>document.querySelector('[data-testid="pdf-page-canvas"]')?.width>10);await page.screenshot({path:path.join(evidence,'resource-preview.png'),fullPage:true});await page.context().close();});
  await check('真实微格播放和片段自动停止',async()=>{const page=await pageFor('guojun');await page.getByRole('button',{name:'微格教学视频',exact:true}).click();await page.getByText(/资料未提供\/未验证/).first().waitFor();await page.getByLabel('微格课程',{exact:true}).selectOption(String(byMajor.SE.course.id));await page.getByRole('heading',{name:'【合成演示】'+mark+'-B',exact:true}).waitFor();await page.locator('article').filter({hasText:'【合成演示】'+mark+'-B'}).getByRole('button',{name:'回看片段',exact:true}).click();await page.waitForFunction(()=>document.querySelector('video')?.readyState>=2);await page.locator('video').evaluate(async v=>{v.currentTime=0;await v.play()});await page.waitForFunction(()=>{const v=document.querySelector('video');return v.paused&&v.currentTime>=.9&&v.currentTime<1});await page.screenshot({path:path.join(evidence,'micro-synthetic-video.png'),fullPage:true});await page.context().close();});
  await check('真实学生档案页面筛选60个合成身份',async()=>{const page=await pageFor('director');await page.getByRole('button',{name:'学生人脸档案库',exact:true}).click();await page.getByPlaceholder('搜索学号、姓名、班级...').fill('DEMO-DATA-');await page.getByText('学生档案列表 (共 60 人 / 总库 140 人)',{exact:true}).waitFor();await page.screenshot({path:path.join(evidence,'synthetic-student-roster.png'),fullPage:false});await page.context().close();});
  await check('真实教师历史页面展示12人冻结归档',async()=>{const page=await pageFor('guojun');const region=page.getByRole('region',{name:'历史开课与人次'});await region.waitFor();await region.getByLabel('历史学期').selectOption('2025-2026春季');await region.getByRole('button',{name:'查询历史',exact:true}).click();await region.getByText(new RegExp(mark)).first().waitFor();await page.screenshot({path:path.join(evidence,'teacher-history.png'),fullPage:false});await page.context().close();});
  assert.deepEqual(errors,[]);
 }
 fs.writeFileSync(path.join(evidence,'results.json'),JSON.stringify({passed:proof.length,proof,requests,backend,frontend,browser:browser?browser.version():null,syntheticDataset:mark,tokensStored:false},null,2));console.log(JSON.stringify({passed:proof.length,proof},null,2));
}
main().catch(e=>{fs.mkdirSync(evidence,{recursive:true});fs.writeFileSync(path.join(evidence,'failure.json'),JSON.stringify({error:e.message,passed:proof.length,proof,requests},null,2));console.error(e.message);process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();});
