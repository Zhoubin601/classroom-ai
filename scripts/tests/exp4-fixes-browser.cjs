// All API replies are local fixtures. This suite never writes a business DB or starts a camera.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright');
const evidence=path.resolve(process.env.EXP4_FIX_EVIDENCE||'docs/20261013-exp4-fix-evidence-v1');
fs.mkdirSync(evidence,{recursive:true});
const user={id:901,role:'ADMIN',realName:'合成用户',department:'合成教研室',teacherCode:'T901'};
const courses=[901,902].map(id=>({id,courseCode:'TEST'+id,courseName:'合成课程'+id,department:user.department,majorCode:'SE',majorId:901}));
const asset=id=>({id,courseId:id,durationMs:2000,sourceDescription:'合成资产'+id});
const slice=(id,title)=>({id,assetId:901,course:courses[0],videoTitle:title,bopppsStage:'B',startMs:500,endMs:1500,recordVersion:0});
const media=fs.readFileSync('docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4');
const proof=[],writes=[],queries=[],exportRequests=[],pageErrors=[];
let drafts=[],courseFailure=false,qualityFailure=false,qualityDelay=false,releaseQuality,latestTerm='2026-2027秋季',delayVideo=false,releaseVideo,videoRequested=false,delayedTransport,transportRequested=false;
async function main(){
 const browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH||path.join(process.env.LOCALAPPDATA,'ms-playwright/chromium-1228/chrome-win64/chrome.exe')});
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
 page.on('pageerror',e=>pageErrors.push(e.message));
 await context.addInitScript(()=>{
  const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);window.__activeBlobs=new Set();
  URL.createObjectURL=value=>{const url=create(value);window.__activeBlobs.add(url);return url};
  URL.revokeObjectURL=url=>{window.__activeBlobs.delete(url);revoke(url)};
 });
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),p=url.pathname;if(!p.startsWith('/api/'))return route.continue();let data=[];
  if(req.method()!=='GET')writes.push({path:p,body:req.postData()});
  if(p.endsWith('/auth/me'))data=user;
  else if(p.endsWith('/auth/csrf'))data={csrfToken:'synthetic-test-only'};
  else if(p==='/api/v1/admin/templates'&&req.method()==='POST'){data={id:11,revision:0,...req.postDataJSON()};drafts=[data];}
  else if(p==='/api/v1/admin/templates')data=drafts;
  else if(p==='/api/v1/admin/courses'||p==='/api/v1/courses')data=courses;
  else if(p.endsWith('/micro-assets'))data=[asset(Number(url.searchParams.get('courseId')))];
  else if(p.endsWith('/micro-slices')||/\/micro-slices\/course\/\d+$/.test(p)){
   const id=Number(url.searchParams.get('courseId')||p.split('/').at(-1));
   if(courseFailure&&id===902)return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,message:'模拟课程加载失败',data:null})});
   data=id===901?[slice(1,'片段一'),slice(2,'片段二')]:[];
  }
  else if(p.endsWith('/micro-slices/1/video')){
   if(delayVideo){videoRequested=true;await new Promise(resolve=>releaseVideo=resolve)}
   return route.fulfill({status:200,contentType:'video/mp4',body:media}).catch(()=>{});
  }
  else if(p.endsWith('/micro-slices/2/video'))return route.fulfill({status:200,contentType:'video/mp4',body:media});
  else if(p==='/api/v1/review-delayed'){transportRequested=true;await new Promise(resolve=>delayedTransport=resolve);return route.fulfill({status:200,contentType:'application/octet-stream',body:'fixture'}).catch(()=>{});}
  else if(p.endsWith('/sprint3/quality')){
   queries.push({term:url.searchParams.get('term'),page:url.searchParams.get('page')});
   const term=url.searchParams.get('term')||latestTerm;
   if(qualityDelay)await new Promise(resolve=>releaseQuality=resolve);
   if(qualityFailure)return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,message:'模拟质量查询失败',data:null})});
   data={term,total:21,asOf:'2026-10-13T12:00:00',rows:[{courseCode:'TEST901',courseName:'页面课程-'+term,courseType:'合成',template:'T1',evaluationCount:1,validTotalCount:1,average:80,evaluationIds:[1]}],details:[]};
  }
  else if(p.endsWith('/sprint3/quality/export')||p.endsWith('/analytics/export-report')){exportRequests.push({path:p,term:url.searchParams.get('term')});return route.fulfill({status:200,contentType:'text/csv',body:'synthetic-export'});}
  else if(p.endsWith('/analytics/dashboard'))data={academicTerm:latestTerm,totalCourses:1,supervisedCourses:1,totalEvaluations:1,coverageRate:100};
  else if(p.endsWith('/offerings/history'))data={records:[],totalOfferings:0,cumulativePersonTimes:0,terms:[]};
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,message:'成功',data})});
 });
 try{
  await page.goto(process.env.UI_URL||'http://127.0.0.1:25175');await page.getByLabel('模板名称').waitFor();
  await page.getByLabel('模板名称').fill('合法小数权重');
  for(const [label,value] of [['教学态度权重','33.3'],['教学内容权重','33.3'],['教学方法权重','0.1'],['教学效果权重','33.3']])await page.getByLabel(label,{exact:true}).fill(value);
  await page.getByRole('button',{name:'新建草稿',exact:true}).click();await page.getByText('模板草稿已保存',{exact:true}).waitFor();
  assert.equal(writes.filter(x=>x.path==='/api/v1/admin/templates').length,1);assert.ok((await page.locator('form').textContent()).includes('权重合计：100%'));
  await page.getByLabel('教学效果权重',{exact:true}).fill('33');await page.getByRole('button',{name:'保存草稿修改',exact:true}).click();await page.getByText(/四维权重必须精确合计100/).waitFor();
  assert.equal(writes.filter(x=>x.path==='/api/v1/admin/templates').length,1);proof.push('R01 合法小数权重保存、无浮点尾数；非法合计提示并拒绝请求');
  await page.reload();await page.getByLabel('模板名称').waitFor();
  const cancel=await page.evaluate(async()=>{
   const {visualApi,sprint3Api}=await import('/src/api/index.ts');const caller=new AbortController();caller.abort();const states=[];
   for(const request of [()=>visualApi.startMonitor(901,901,caller.signal),()=>sprint3Api.blob('/api/v1/review-cancelled',undefined,caller.signal)]){
    try{await request();states.push('resolved')}catch(e){states.push(e.name)}
   }return states;
  });
  assert.equal(writes.filter(x=>x.path==='/api/visual/start-monitor').length,0);assert.ok(!cancel.includes('resolved'));
  await page.evaluate(async()=>{const {sprint3Api}=await import('/src/api/index.ts');window.__caller=new AbortController();window.__transportState='pending';sprint3Api.blob('/api/v1/review-delayed',undefined,window.__caller.signal).then(()=>window.__transportState='resolved',()=>window.__transportState='cancelled')});
  await eventually(()=>transportRequested);await page.evaluate(()=>window.__caller.abort());await page.waitForFunction(()=>window.__transportState==='cancelled');delayedTransport();proof.push('R02 预先取消不发送启动请求，在途组件取消到达实际传输');
  await page.getByRole('button',{name:'微格教学视频',exact:true}).click();await page.getByRole('button',{name:'修改片段',exact:true}).first().waitFor();
  await page.getByRole('button',{name:'修改片段',exact:true}).first().click();assert.equal(await page.getByLabel('起点（分:秒.毫秒）').inputValue(),'0:00.500');assert.equal(await page.getByLabel('终点（分:秒.毫秒）').inputValue(),'0:01.500');
  courseFailure=true;await page.getByLabel('微格课程').selectOption('902');await page.getByRole('alert').filter({hasText:'模拟课程加载失败'}).waitFor();
  assert.equal(await page.getByRole('dialog',{name:'有未保存的修改',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'移除记录',exact:true}).count(),0);assert.equal(await page.getByLabel('片段标题').inputValue(),'');assert.equal(await page.getByLabel('片段标题').isDisabled(),true);
  proof.push('R03 加载失败无旧课程片段/删除动作，编辑与资产上下文清空');proof.push('R06 同步时间文本与完整编辑基线，未编辑切换无需保存提示');
  courseFailure=false;await page.getByRole('button',{name:'重试加载课程片段',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('input[maxlength="255"]')?.disabled);assert.equal(await page.getByLabel('微格课程').inputValue(),'902');assert.equal(await page.getByRole('button',{name:'移除记录',exact:true}).count(),0);
  await page.getByLabel('微格课程').selectOption('901');await page.getByRole('button',{name:'修改片段',exact:true}).first().waitFor();
  await page.getByRole('button',{name:'修改片段',exact:true}).first().click();await page.getByLabel('片段标题').fill('本地未保存标题');await page.getByLabel('微格课程').selectOption('902');await page.getByRole('dialog',{name:'有未保存的修改',exact:true}).waitFor();await page.getByRole('button',{name:'继续编辑',exact:true}).click();assert.equal(await page.getByLabel('微格课程').inputValue(),'901');assert.equal(await page.getByLabel('片段标题').inputValue(),'本地未保存标题');
  await page.getByRole('button',{name:'新建片段',exact:true}).click();await page.getByRole('button',{name:'放弃修改',exact:true}).click();proof.push('R06 真正编辑仍受保护，取消切换保留输入，放弃后可新建');
  delayVideo=true;await page.getByRole('button',{name:'回看片段',exact:true}).first().click();await eventually(()=>videoRequested);await page.getByRole('button',{name:'回看片段',exact:true}).nth(1).click();await page.waitForFunction(()=>document.querySelector('video')?.readyState>=2);assert.equal(await page.locator('video').locator('..').getByRole('heading').textContent(),'片段二');releaseVideo();await page.waitForTimeout(150);assert.equal(await page.locator('video').locator('..').getByRole('heading').textContent(),'片段二');assert.equal(await page.evaluate(()=>window.__activeBlobs.size),1);
  delayVideo=false;await page.getByRole('button',{name:'回看片段',exact:true}).first().click();await page.waitForFunction(()=>document.querySelector('video')?.readyState>=2);assert.equal(await page.evaluate(()=>window.__activeBlobs.size),1);
  await page.locator('video').evaluate(v=>v.currentTime=1.9);await page.waitForFunction(()=>document.querySelector('video').currentTime<=1.5);await page.getByLabel('微格课程').selectOption('902');await page.waitForFunction(()=>window.__activeBlobs.size===0);proof.push('R04 最新播放选择生效、真实合成视频可解码且区间有效，替换/离开释放Blob');
  user.role='DIRECTOR';await page.reload();await page.getByRole('button',{name:'导出最新学期质量报表 (CSV)',exact:true}).waitFor();latestTerm='2027-2028春季';
  const annualDownload=page.waitForEvent('download');await page.getByRole('button',{name:'导出最新学期质量报表 (CSV)',exact:true}).click();const named=await annualDownload;assert.equal(named.suggestedFilename(),'teaching-quality-2027-2028春季.csv');assert.equal(exportRequests.at(-1).term,'2027-2028春季');proof.push('R07 最新学期文案、显式学期参数和实际学期文件名，不含固定年份');
  latestTerm='2026-2027秋季';await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByRole('heading',{name:'质量汇总',exact:true}).waitFor();await enabled(page.getByRole('button',{name:'下载CSV',exact:true}));
  // Latest term is pinned for pagination/export even when the latest visible term changes.
  latestTerm='2027-2028秋季';await page.getByRole('button',{name:'下一页',exact:true}).click();await enabled(page.getByRole('button',{name:'下载CSV',exact:true}));assert.equal(queries.at(-1).term,'2026-2027秋季');
  await page.getByLabel('学期',{exact:true}).fill('2025-2026秋季');assert.equal(await page.getByRole('button',{name:'下载CSV',exact:true}).isDisabled(),true);await page.getByText('筛选尚未应用，请先查询。',{exact:true}).waitFor();
  qualityFailure=true;await page.getByRole('button',{name:'查询',exact:true}).click();await page.getByText(/模拟质量查询失败/).waitFor();assert.equal(await page.getByRole('button',{name:'下载CSV',exact:true}).isDisabled(),true);
  qualityFailure=false;qualityDelay=true;await page.getByRole('button',{name:'查询',exact:true}).click();await eventually(()=>!!releaseQuality);await page.getByLabel('学期',{exact:true}).fill('2024-2025秋季');releaseQuality();await page.waitForTimeout(150);assert.equal(await page.getByRole('button',{name:'下载CSV',exact:true}).isDisabled(),true);qualityDelay=false;
  await page.getByLabel('学期',{exact:true}).fill('2025-2026秋季');await enabled(page.getByRole('button',{name:'下载CSV',exact:true}));const qualityDownload=page.waitForEvent('download');await page.getByRole('button',{name:'下载CSV',exact:true}).click();await qualityDownload;assert.equal(exportRequests.at(-1).term,'2025-2026秋季');proof.push('R05 未应用/失败/请求中修改筛选禁止导出，成功查询后的显式学期与页面一致');
  await page.setViewportSize({width:375,height:812});await page.emulateMedia({reducedMotion:'reduce'});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(evidence,'quality-375.png'),fullPage:true});
  assert.deepEqual(pageErrors,[]);fs.writeFileSync(path.join(evidence,'browser-results.json'),JSON.stringify({fixtureOnly:true,realBusinessWrites:0,proof,pageErrors,writeRequests:writes.length},null,2));console.log(JSON.stringify({proof,pageErrors},null,2));
 }catch(e){fs.writeFileSync(path.join(evidence,'browser-failure.txt'),e.stack);await page.screenshot({path:path.join(evidence,'browser-failure.png'),fullPage:true});throw e}finally{releaseVideo?.();delayedTransport?.();releaseQuality?.();await browser.close()}
}
async function eventually(read){for(let i=0;i<100;i++){if(await read())return;await new Promise(resolve=>setTimeout(resolve,20))}assert.fail('Expected asynchronous fixture was not reached')}
async function enabled(locator){await locator.waitFor();await eventually(async()=>!await locator.isDisabled())}
main().catch(e=>{console.error(e);process.exitCode=1});
