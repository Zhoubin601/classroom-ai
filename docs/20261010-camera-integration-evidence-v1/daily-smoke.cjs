const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE),base='http://127.0.0.1:5173';
const results=[],errors=[];let browser;
async function main(){assert.ok(process.env.DAILY_TEST_PASSWORD,'Provide current demo password only in process environment');browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});try{
 for(const who of ['director','guojun','supervisor']){
  const c=await browser.newContext({viewport:{width:1500,height:1000}}),p=await c.newPage();p.on('pageerror',e=>errors.push({actor:who,error:e.message}));
  await p.goto(base);await p.getByPlaceholder('如 guojun, director, supervisor 等').fill(who);await p.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.DAILY_TEST_PASSWORD);await p.getByRole('button',{name:'立即验证并登录',exact:true}).click();await p.getByRole('button',{name:'退出登录',exact:true}).waitFor();
  const token=await p.evaluate(()=>localStorage.getItem('jwtToken')),headers={Authorization:'Bearer '+token};
  const read=async route=>{const r=await p.request.get(base+route,{headers});assert.equal(r.status(),200,route);const b=await r.json();assert.equal(b.code,200,b.message);return b.data;};
  const courses=await read('/api/v1/courses'),offers=await read('/api/v1/courses/offerings');assert.ok(courses.length);assert.ok(offers.length);
  await p.getByRole('button',{name:'微格教学视频',exact:true}).click();await p.getByLabel('微格课程').waitFor();await p.locator('h1').filter({hasText:'微格教学视频'}).waitFor();
  const slices=await read('/api/v1/resources/micro-slices/course/'+courses[0].id);if(who==='supervisor')assert.equal(await p.getByRole('button',{name:'上传微格视频',exact:true}).count(),0);
  const invalid=await p.request.post(base+'/api/visual/start-monitor',{headers,data:{}});assert.equal(invalid.status(),400);
  const noauth=await p.request.get(base+'/api/visual/monitor-status');assert.equal(noauth.status(),401);
  const context=await p.request.get(base+`/api/visual/monitor-context?offeringId=${offers.find(o=>!o.isSnapshotFrozen)?.id||offers[0].id}&sessionId=9223372036854775806`,{headers});assert.ok([400,409].includes(context.status()));
  await p.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();await p.getByText('按所选班次名单识别出勤，实时查看课堂在座与抬头状态。',{exact:true}).waitFor();
  await p.screenshot({path:path.join(__dirname,`daily-${who}.png`),fullPage:true});
  results.push({actor:who,status:'PASS',courses:courses.length,offerings:offers.length,microSlices:slices.length,monitorInvalidContext:400,monitorAnonymous:401,scopedContextEndpoint:context.status()});
  await p.getByRole('button',{name:'退出登录',exact:true}).click();await p.getByRole('button',{name:'立即验证并登录',exact:true}).waitFor();await c.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS Daily three-role login, new micro UI, camera authorization guards, scoped backend route, attendance UI; no business fixture writes');
}finally{fs.writeFileSync(path.join(__dirname,'daily-smoke.json'),JSON.stringify({base,businessFixtureWrites:false,results,pageErrors:errors},null,2));await browser?.close();}}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
