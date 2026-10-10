const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {spawn,execFileSync}=require('node:child_process');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..'),dir=path.join(__dirname,'camera-micro');fs.mkdirSync(dir,{recursive:true});
const base=process.env.BASE_URL,backend=process.env.BACKEND_URL,stamp=Date.now();
const state=JSON.parse(fs.readFileSync(path.join(__dirname,'environment.json'),'utf8').replace(/^\uFEFF/,''));
assert.match(state.mysql,/^classroom-exp3-browser-[a-f0-9]{10}$/);
let browser,active,offering,student='CAMERA_'+stamp,seeded=false,slice,session;
const actors={},results=[],requests=[],errors=[];
const pause=ms=>new Promise(r=>setTimeout(r,ms));
function sql(q){return execFileSync('docker',['exec','-e','MYSQL_PWD',state.mysql,'mysql','-uroot','classroom_ai','-N','-e',q],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.ROLE_TEST_MYSQL_PASSWORD},stdio:['ignore','pipe','pipe']}).trim();}
async function req(actor,p,method='GET',data,front=false){
 const r=await (actor?actors[actor].page.request:actors.director.page.request).fetch((front?base:backend)+p,{method,headers:actor?{Authorization:'Bearer '+actors[actor].token}:{},...(data===undefined?{}:{data})});
 const json=(r.headers()['content-type']||'').includes('json');const body=json?await r.json():null;
 const code=r.status()===200&&json?body.code:r.status();requests.push({actor:actor||'anonymous',path:p.split('?')[0],method,http:r.status(),code});return {r,code,data:body?.data,message:body?.message};
}
async function good(...args){const r=await req(...args);assert.equal(r.code,200,`${args[0]} ${args[1]}: ${r.message}`);return r.data;}
async function check(name,fn){try{const details=await fn();results.push({name,status:'PASS',details});console.log('PASS '+name);}catch(e){results.push({name,status:'FAIL',error:e.message});console.log('FAIL '+name+': '+e.message);if(active&&!active.isClosed())await active.screenshot({path:path.join(dir,'failure-'+results.length+'.png'),fullPage:true}).catch(()=>{});}}
async function login(name){const context=await browser.newContext({viewport:{width:1500,height:1100}}),page=await context.newPage();
 page.on('pageerror',e=>errors.push({actor:name,error:e.message}));page.on('dialog',d=>d.accept());
 await page.goto(base);await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(name);await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
 await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
 actors[name]={page,token:await page.evaluate(()=>localStorage.getItem('jwtToken'))};return page;
}
async function py(file,extra={}){return new Promise((resolve,reject)=>{let output='',err='';const child=spawn(path.join(root,'.venv1/Scripts/python.exe'),[path.join(__dirname,file)],{cwd:root,windowsHide:true,env:{...process.env,...extra}});
 const timer=setTimeout(()=>{child.kill();reject(new Error('Native helper timeout'));},100000);child.stdout.on('data',d=>output+=d);child.stderr.on('data',d=>err=(err+d).slice(-1200));child.on('error',reject);child.on('close',code=>{clearTimeout(timer);try{const line=output.trim().split('\n').at(-1);resolve({code,data:JSON.parse(line),error:err});}catch{reject(new Error(`Native helper failed (${code}): ${err}`));}});});}
function pyEnv(who,s){return {CAMERA_TEST_AUTH:'Bearer '+actors[who].token,CAMERA_TEST_OFFERING:String(offering.id),CAMERA_TEST_SESSION:String(s.id),CAMERA_TEST_STUDENT:student};}
async function status(){return good('guojun','/api/visual/monitor-status','GET',undefined,true);}
async function stopped(){for(let i=0;i<25;i++){const s=await status();if(!s.running&&!s.starting)return s;await pause(300);}throw new Error('Native camera remained running');}
async function goAttendance(){active=actors.guojun.page;await active.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();await active.locator('select').first().selectOption(String(offering.id));await pause(1200);}
async function startCamera(){await active.getByRole('button',{name:'打开摄像头开启考勤',exact:true}).click();
 await active.getByRole('button',{name:'停止摄像头监控',exact:true}).waitFor({timeout:95000});
 await active.getByText('摄像头与考勤数据已连接',{exact:true}).waitFor({timeout:20000});
 for(let i=0;i<30;i++){if(await active.locator('img[src*="video-feed"]').evaluate(el=>el.naturalWidth>=640).catch(()=>false))break;await pause(500);}
 assert.ok(await active.locator('img[src*="video-feed"]').evaluate(el=>el.naturalWidth>=640),'Real MJPEG image must decode');return status();
}
async function main(){browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
try{
 for(const name of ['director','guojun','supervisor','liubo'])await login(name);
 await check('真实摄像头提取人脸；向量仅进入隔离数据库',async()=>{const r=await py('seed-camera-person.py',{CAMERA_TEST_AUTH:'Bearer '+actors.director.token,CAMERA_TEST_STUDENT:student});
   fs.writeFileSync(path.join(dir,'physical-face-seed.json'),JSON.stringify(r.data,null,2));assert.equal(r.code,0,'摄像头在20秒内未检测到人脸');assert.equal(r.data.featureDimension,512);seeded=true;return r.data;});
 if(!seeded)await good('director','/api/student','POST',{studentId:student,name:'无真人特征合成学生',className:'隔离班'});
 const teachers=await good('director','/api/v1/teachers');const teacher=teachers.find(t=>t.teacherCode==='T2024001');assert.ok(teacher);
 offering=await good('director','/api/v1/courses/offerings','POST',{courseId:2,academicTerm:'CAMERA-'+stamp,className:'摄像头隔离验收班-'+stamp,primaryTeacherId:teacher.id,collaboratingTeacherIds:[],studentNumbers:[student]});
 await check('本机启动接口先验证登录、班次权限及会话',async()=>{
   assert.equal((await req(null,'/api/visual/start-monitor','POST',{},true)).code,401);
   assert.equal((await req('guojun','/api/visual/start-monitor','POST',{},true)).code,400);
   const s=await good('director','/api/v1/attendance/start','POST',{offeringId:offering.id,weekNumber:2,classroom:'隔离验收室'});
   assert.equal((await req('liubo','/api/visual/start-monitor','POST',{offeringId:offering.id,sessionId:s.id},true)).code,403);
   await good('director','/api/v1/attendance/finish','POST',{sessionId:s.id,actualCount:0,avgLookupRate:0});
   assert.equal((await req('guojun','/api/visual/start-monitor','POST',{offeringId:offering.id,sessionId:s.id},true)).code,409);
   return {anonymous:401,missingContext:400,foreignTeacher:403,finishedSession:409};
 });
 await check('三角色真实Python传输→后端→班次考勤；结束后拒绝旧帧',async()=>{
   const detail=[];for(const who of ['director','guojun','supervisor']){
     const s=await good(who,'/api/v1/attendance/start','POST',{offeringId:offering.id,weekNumber:2,classroom:'隔离验收室'});
     const context=await good(who,`/api/visual/monitor-context?offeringId=${offering.id}&sessionId=${s.id}`);
     assert.ok(context.faces.every(f=>f.studentId===student));if(seeded)assert.equal(context.faces.length,1);
     const p=await py('transport-real-backend.py',pyEnv(who,s));assert.equal(p.data.sent,true,p.data.error);
     const current=await good(who,`/api/v1/attendance/current?offeringId=${offering.id}`),overview=await good(who,`/api/visual/overview?offeringId=${offering.id}`);
     assert.equal(current.actualCount,1);assert.equal(current.attendanceRate,100);assert.equal(overview.currentPresent,1);assert.equal(overview.auditingCount,1);
     await good(who,'/api/v1/attendance/finish','POST',{sessionId:s.id,actualCount:1,avgLookupRate:50});
     const old=await py('transport-real-backend.py',pyEnv(who,s));assert.equal(old.data.sent,false);assert.equal(old.data.fatal,true);assert.match(old.data.error,/409/);
     detail.push({role:who,sessionId:s.id,actualCount:1,auditingCount:1,finishedFrameHttp:409});
   }return detail;
 });
 await goAttendance();
 await check('真实摄像头人脸/姿态→MJPEG→Redis大屏→MySQL活动考勤',async()=>{
   const initial=await startCamera();assert.equal(initial.offeringId,offering.id);assert.ok(initial.framesSent>0);assert.ok(initial.reporting);
   session=await good('guojun',`/api/v1/attendance/current?offeringId=${offering.id}`);
   let overview;for(let i=0;i<30;i++){overview=await good('guojun',`/api/visual/overview?offeringId=${offering.id}`);if(overview.currentPresent===1)break;await pause(500);}
   if(seeded)assert.equal(overview.currentPresent,1,'实际镜头中的已入班测试人必须匹配');
   const trend=await good('guojun',`/api/visual/trend?offeringId=${offering.id}`),rows=await good('guojun',`/api/visual/students/status?offeringId=${offering.id}`);
   assert.ok(trend.length>0);assert.ok(rows.some(r=>r.studentId===student));
   const live=await good('guojun',`/api/v1/attendance/current?offeringId=${offering.id}`);assert.equal(live.actualCount,overview.currentPresent);
   assert.ok(Number(sql(`SELECT COUNT(*) FROM classroom_record WHERE session_id='CAMERA_${offering.id}_${session.id}'`))>0);
   await active.screenshot({path:path.join(dir,'real-camera-connected.png'),fullPage:true});
   assert.equal((await req('director','/api/visual/stop-monitor','POST',undefined,true)).code,403);
   assert.equal((await req(null,'/api/visual/video-feed','GET',undefined,true)).code,401);
   const internal=await fetch('http://127.0.0.1:18088/health');assert.equal(internal.status,403);
   return {offeringId:offering.id,sessionId:session.id,framesSent:initial.framesSent,currentPresent:overview.currentPresent,lookupRate:overview.currentLookupRate,trendPoints:trend.length,rosterCount:rows.length,internalControl:403};
 });
 await check('停止摄像头释放设备，旧视频授权失效且数据库不再增长',async()=>{
   const s=await status(),oldUrl=s.videoUrl;await active.getByRole('button',{name:'停止摄像头监控',exact:true}).click();await stopped();
   const before=sql(`SELECT COUNT(*) FROM classroom_record WHERE session_id='CAMERA_${offering.id}_${session.id}'`);await pause(2200);const after=sql(`SELECT COUNT(*) FROM classroom_record WHERE session_id='CAMERA_${offering.id}_${session.id}'`);assert.equal(after,before);
   assert.equal((await req(null,oldUrl,'GET',undefined,true)).code,401);return {recordsAfterStop:Number(after),noAdditionalFrames:true,oldVideoTicket:401};
 });
 await check('最后一帧为0时归档保留0，不使用历史正值覆盖',async()=>{
   const p=await py('transport-real-backend.py',{...pyEnv('guojun',session),CAMERA_TEST_EMPTY:'true'});assert.equal(p.data.sent,true);
   await pause(1200);await active.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click();
   let saved;for(let i=0;i<25;i++){saved=(await good('guojun',`/api/v1/attendance/offering/${offering.id}`)).find(s=>s.id===session.id);if(saved?.status==='FINISHED')break;await pause(300);}
   assert.equal(saved.actualCount,0);assert.equal(saved.avgLookupRate,0);return {actualCount:0,lookupRate:0,status:saved.status};
 });
 await check('重新启动并通过页面结束考勤，归档结果与实时数据一致',async()=>{
   await startCamera();await pause(1500);const current=await good('guojun',`/api/v1/attendance/current?offeringId=${offering.id}`);
   await active.getByRole('button',{name:'结束考勤并归档下课',exact:true}).click();await stopped();
   let finished;for(let i=0;i<30;i++){finished=(await good('guojun',`/api/v1/attendance/offering/${offering.id}`)).find(s=>s.id===current.id);if(finished?.status==='FINISHED')break;await pause(300);}
   assert.equal(finished.status,'FINISHED');assert.equal(finished.actualCount,current.actualCount);assert.equal(finished.operatorRole,'TEACHER');
   await active.getByRole('button',{name:/考勤归档记录/}).click();await active.getByText('课堂考勤归档历史').waitFor({timeout:10000}).catch(()=>{});
   await active.screenshot({path:path.join(dir,'real-camera-archive.png'),fullPage:true});
   await active.keyboard.press('Escape');return {sessionId:finished.id,actualCount:finished.actualCount,attendanceRate:finished.attendanceRate,lookup:finished.avgLookupRate,status:finished.status};
 });
 // Close archive overlay by switching to another tab, then return.
 await actors.guojun.page.getByRole('button',{name:'任课教师工作台',exact:true}).click({force:true});await goAttendance();
 await check('切换班次自动停止原生摄像头',async()=>{await startCamera();const values=await active.locator('select').first().locator('option').evaluateAll(xs=>xs.map(x=>x.value));const other=values.find(x=>x!==String(offering.id));assert.ok(other);await active.locator('select').first().selectOption(other);await stopped();return {stoppedOnOfferingChange:true};});
 await active.locator('select').first().selectOption(String(offering.id));await pause(1200);
 await check('退出登录自动释放原生摄像头，重新登录可继续操作',async()=>{
   await startCamera();const old=active;await old.getByRole('button',{name:'退出登录',exact:true}).click();await old.getByRole('button',{name:'立即验证并登录',exact:true}).waitFor();
   const s=await good('director','/api/visual/monitor-status','GET',undefined,true);assert.equal(s.running,false);assert.equal(s.starting,false);
   await login('guojun');await old.close();await goAttendance();return {stoppedBeforeLogout:true};
 });
 await check('班次冻结后推断停止并显示错误；新启动被拦截',async()=>{
   await startCamera();await good('director',`/api/v1/courses/offerings/${offering.id}/archive`,'POST');
   await active.getByTestId('monitor-error').waitFor({timeout:25000});await stopped();assert.match(await active.getByTestId('monitor-error').innerText(),/冻结|结束|停止/);
   assert.ok(await active.getByRole('button',{name:'打开摄像头开启考勤',exact:true}).isDisabled());await active.screenshot({path:path.join(dir,'frozen-camera-visible-error.png'),fullPage:true});return {nativeStopped:true,visibleError:true};
 });
 await check('真实浏览器生成合成WebM，教师上传并实际解码播放',async()=>{
   active=actors.guojun.page;await active.getByRole('button',{name:'微格教学视频',exact:true}).click();await active.getByLabel('微格课程').selectOption('2');
   const bytes=await require('./make-micro-video.cjs')(active);
   fs.writeFileSync(path.join(dir,'synthetic-micro.webm'),Buffer.from(bytes));assert.ok(bytes.length>2000);
   await active.getByPlaceholder('填写微格视频标题').fill('实机视频解码验收-'+stamp);await active.locator('input[type=file]').setInputFiles({name:'synthetic-micro.webm',mimeType:'video/webm',buffer:Buffer.from(bytes)});await active.getByLabel('视频时长').fill('3');
   await active.getByRole('button',{name:'上传微格视频',exact:true}).click();await active.getByText('微格视频已上传',{exact:true}).waitFor();
   slice=(await good('guojun','/api/v1/resources/micro-slices/course/2')).find(s=>s.videoTitle==='实机视频解码验收-'+stamp);assert.ok(slice);assert.match(slice.sliceUrl,/^\/uploads\/micro\//);
   const card=active.locator('article').filter({hasText:slice.videoTitle});await card.getByRole('button',{name:'播放视频'}).click();const video=active.getByTestId('micro-video');await video.waitFor();
   await video.evaluate(v=>v.play());await active.waitForFunction(()=>document.querySelector('[data-testid=micro-video]')?.currentTime>0.6);
   const decoded=await video.evaluate(v=>{const c=document.createElement('canvas');c.width=2;c.height=2;c.getContext('2d').drawImage(v,0,0,2,2);return {time:v.currentTime,width:v.videoWidth,height:v.videoHeight,ready:v.readyState,pixel:Array.from(c.getContext('2d').getImageData(0,0,1,1).data)};});assert.equal(decoded.width,320);assert.ok(decoded.pixel[0]>100||decoded.pixel[2]>100);
   await active.screenshot({path:path.join(dir,'teacher-real-video-playback.png'),fullPage:true});return {sliceId:slice.id,bytes:bytes.length,...decoded};
 });
 await check('督导只读播放真实视频；越权和绕过媒体接口被拒绝',async()=>{
   active=actors.supervisor.page;await active.getByRole('button',{name:'微格教学视频',exact:true}).click();await active.getByLabel('微格课程').selectOption('2');
   assert.equal(await active.getByRole('button',{name:'上传微格视频',exact:true}).count(),0);assert.equal(await active.getByRole('button',{name:'删除视频',exact:true}).count(),0);
   await active.locator('article').filter({hasText:slice.videoTitle}).getByRole('button',{name:'播放视频'}).click();const v=active.getByTestId('micro-video');await v.waitFor();await v.evaluate(v=>v.play());await active.waitForFunction(()=>document.querySelector('[data-testid=micro-video]')?.currentTime>0.5);
   await active.screenshot({path:path.join(dir,'supervisor-real-video-playback.png'),fullPage:true});
   assert.equal((await req('liubo',`/api/v1/resources/micro-slices/${slice.id}/video`)).code,403);assert.equal((await req(null,`/api/v1/resources/micro-slices/${slice.id}/video`)).code,401);
   assert.equal((await req('guojun',slice.sliceUrl)).code,403);assert.equal((await req('guojun',slice.sliceUrl,'GET',undefined,true)).code,403);
   assert.equal((await req('supervisor',`/api/v1/resources/micro-slices/${slice.id}`,'DELETE')).code,403);
   return {decodedBySupervisor:true,outsideScope:403,anonymous:401,directPath:403,supervisorDelete:403};
 });
 await check('主任读取、教师删除后媒体不可访问',async()=>{assert.equal((await req('director',`/api/v1/resources/micro-slices/${slice.id}/video`)).code,200);await good('guojun',`/api/v1/resources/micro-slices/${slice.id}`,'DELETE');assert.equal((await req('guojun',`/api/v1/resources/micro-slices/${slice.id}/video`)).code,400);return {deleted:true};});
 await check('浏览器无未捕获页面异常',async()=>{assert.deepEqual(errors,[]);});
}finally{
 if(actors.guojun)await req('guojun','/api/visual/stop-monitor','POST',undefined,true).catch(()=>{});
 fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({browser:'Real Chrome',mockedAPIs:false,syntheticVideo:true,physicalCamera:true,results,requests,pageErrors:errors},null,2));await browser?.close();
}
console.log(`CAMERA_MICRO ${results.filter(r=>r.status==='PASS').length}/${results.length}`);if(results.some(r=>r.status==='FAIL'))process.exitCode=1;
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
