// Read-only review: every /api request is fulfilled locally; no business database or camera.
const { chromium } = require('C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const root=__dirname, results=[];
const user={id:901,role:'ADMIN',realName:'合成管理员',department:'合成教研室',teacherCode:'T901'};
const courses=[{id:901,courseCode:'TEST901',courseName:'合成课程一',department:user.department},{id:902,courseCode:'TEST902',courseName:'合成课程二',department:user.department}];
let writes=[], delayedFirst, videoFirst, queryTerms=[], exportTerms=[],failSecondCourse=false;
const asset=id=>({id,courseId:id,durationMs:2000,sourceDescription:'合成资产'+id});
const slice=(id,title)=>({id,assetId:901,course:courses[0],videoTitle:title,bopppsStage:'B',startMs:0,endMs:2000,recordVersion:0});
async function main(){
 const browser=await chromium.launch({headless:true,executablePath:path.join(process.env.LOCALAPPDATA,'ms-playwright/chromium-1228/chrome-win64/chrome.exe')});
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
 page.on('pageerror',e=>console.log('PAGE ERROR:',e.message));
 page.on('console',m=>{if(m.type()==='error')console.log('CONSOLE ERROR:',m.text());});
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),p=url.pathname;if(!p.startsWith('/api/'))return route.continue();let data=[];
  if(req.method()!=='GET')writes.push({path:p,body:req.postData()});
  if(p.endsWith('/auth/me'))data=user;
  else if(p.endsWith('/auth/csrf'))data={csrfToken:'synthetic-test-only'};
  else if(p==='/api/v1/admin/courses'||p==='/api/v1/courses')data=courses;
  else if(p.endsWith('/micro-assets'))data=[asset(Number(url.searchParams.get('courseId')))];
  else if(p.endsWith('/micro-slices')){if(failSecondCourse&&url.searchParams.get('courseId')==='902')return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,message:'模拟课程二加载失败',data:null})});data=url.searchParams.get('courseId')==='901'?[slice(1,'片段一'),slice(2,'片段二')]:[];}
  else if(p.endsWith('/micro-slices/1/video')){videoFirst=true;await new Promise(resolve=>delayedFirst=resolve);return route.fulfill({status:200,contentType:'video/mp4',body:'synthetic-non-decodable-fixture'});}
  else if(p.endsWith('/micro-slices/2/video'))return route.fulfill({status:200,contentType:'video/mp4',body:'synthetic-non-decodable-fixture'});
  else if(p.endsWith('/sprint3/quality')){queryTerms.push(url.searchParams.get('term'));data={total:1,asOf:'2026-10-12T12:00:00',rows:[{courseCode:'TEST',courseName:'页面课程-'+(url.searchParams.get('term')||'最新'),courseType:'合成',template:'T1',evaluationCount:1,validTotalCount:1,average:80,evaluationIds:[1]}],details:[]};}
  else if(p.endsWith('/sprint3/quality/export')){exportTerms.push(url.searchParams.get('term'));return route.fulfill({status:200,contentType:'text/csv',body:'synthetic-export'});}
  else if(p.endsWith('/analytics/dashboard'))data={totalCourses:0,supervisedCourses:0,totalEvaluations:0,coverageRate:0};
  else if(p.endsWith('/monitor-status'))data={running:false,starting:false};
  else if(p.endsWith('/offerings/history'))data={records:[],totalOfferings:0,cumulativePersonTimes:0,terms:[]};
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,message:'成功',data})});
 });
 try{
  await page.goto('http://127.0.0.1:25175');await page.getByLabel('模板名称').waitFor();
  await page.getByLabel('模板名称').fill('合法小数权重');
  for(const [label,value] of [['教学态度权重','33.3'],['教学内容权重','33.3'],['教学方法权重','0.1'],['教学效果权重','33.3']])await page.getByLabel(label,{exact:true}).fill(value);
  const before=writes.length;await page.getByRole('button',{name:'新建草稿',exact:true}).click();await page.waitForTimeout(200);
  assert.equal(writes.length,before);assert.ok((await page.locator('form').textContent()).includes('99.99999999999999'));
  results.push({id:'R01',issue:'合法小数权重不能提交',weights:[33.3,33.3,0.1,33.3],displayTotal:99.99999999999999,templateWrites:0});
  await page.screenshot({path:path.join(root,'decimal-weights.png'),fullPage:true});
  await page.reload();await page.getByLabel('模板名称').waitFor();
  // An already-aborted operation must never reach the adapter/server.
  const abortResult=await page.evaluate(async()=>{const {visualApi}=await import('/src/api/index.ts');const controller=new AbortController();controller.abort();try{await visualApi.startMonitor(901,901,controller.signal);return 'resolved';}catch(e){return e.message;}});
  assert.equal(writes.filter(x=>x.path==='/api/visual/start-monitor').length,1);
  results.push({id:'R02',issue:'预先取消的摄像头启动仍发送POST',startRequests:1,message:abortResult});
  await page.getByRole('button',{name:'微格教学视频',exact:true}).click();await page.getByRole('button',{name:'修改片段',exact:true}).first().waitFor();
  await page.getByRole('button',{name:'修改片段',exact:true}).first().click();
  failSecondCourse=true;
  await page.getByLabel('微格课程').selectOption('902');
  await page.getByRole('dialog',{name:'有未保存的修改',exact:true}).waitFor();
  results.push({id:'R06',issue:'只载入片段未编辑，切换课程也出现未保存提示',reason:'时间格式watch更新晚于sliceGuard.reset'});
  await page.getByRole('button',{name:'放弃修改',exact:true}).click();
  await page.getByText('模拟课程二加载失败',{exact:true}).waitFor();assert.equal(await page.getByLabel('微格课程').inputValue(),'902');assert.equal(await page.getByRole('heading',{name:'片段一',exact:true}).count(),1);
  const nextWrite=writes.length;await page.getByRole('button',{name:'移除记录',exact:true}).first().click();await page.getByRole('dialog',{name:'确认操作',exact:true}).waitFor();await page.getByRole('button',{name:'确认',exact:true}).click();await page.waitForTimeout(200);
  const request=writes.slice(nextWrite).find(x=>x.path==='/api/v1/admin/micro-slices/1');assert.ok(request);
  results.push({id:'R03',issue:'切换课程加载失败仍保留旧课程片段及可执行的删除按钮',selectedCourse:902,deletedSliceCourse:901,request});
  failSecondCourse=false;
  await page.reload();await page.getByLabel('模板名称').waitFor();await page.getByRole('button',{name:'微格教学视频',exact:true}).click();await page.getByRole('button',{name:'回看片段',exact:true}).first().waitFor();
  await page.getByRole('button',{name:'回看片段',exact:true}).first().click();for(let i=0;i<50&&!videoFirst;i++)await page.waitForTimeout(20);assert.ok(delayedFirst);
  await page.getByRole('button',{name:'回看片段',exact:true}).nth(1).click();await page.getByRole('heading',{name:'片段二',exact:true}).last().waitFor();delayedFirst();await page.getByRole('heading',{name:'片段一',exact:true}).last().waitFor();
  results.push({id:'R04',issue:'较早播放请求覆盖较新的片段选择',clickOrder:['片段一','片段二'],finalPlayingTitle:'片段一'});
  user.role='DIRECTOR';await page.reload();await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByRole('heading',{name:'质量汇总',exact:true}).waitFor();await page.getByLabel('学期',{exact:true}).fill('2025-2026秋季');
  const dl=page.waitForEvent('download');await page.getByRole('button',{name:'下载CSV',exact:true}).click();await dl;
  assert.equal(queryTerms.at(-1),'');assert.equal(exportTerms.at(-1),'2025-2026秋季');
  results.push({id:'R05',issue:'尚未查询的新筛选直接用于导出',displayedQueryTerm:queryTerms.at(-1),exportTerm:exportTerms.at(-1)});
  await page.setViewportSize({width:375,height:812});await page.emulateMedia({reducedMotion:'reduce'});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:path.join(root,'quality-375.png'),fullPage:true});
  fs.writeFileSync(path.join(root,'results.json'),JSON.stringify({fixtureOnly:true,realBusinessWrites:0,results},null,2));console.log(JSON.stringify(results,null,2));
 }catch(e){fs.writeFileSync(path.join(root,'failure.txt'),String(e.stack));await page.screenshot({path:path.join(root,'failure.png'),fullPage:true});throw e;}finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
