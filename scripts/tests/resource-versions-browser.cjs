// Local API fixtures only: no business database or personal files.
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const dir = path.resolve(process.env.RESOURCE_VERSIONS_EVIDENCE || 'docs/resource-versions-browser');
fs.mkdirSync(dir, {recursive:true});
const course = id => ({id, courseCode:'SYN-'+id, courseName:'合成课程'+id, department:'合成教研室', majorCode:'SE'});
const resource = (id, courseId, seriesId) => ({id, course:course(courseId), seriesId, resourceName:'合成资源'+id, version:'v1', tags:[], chapter:'合成章节', isPublic:true});
const a = resource(101,901,10), b = resource(201,902,20);
const currents = new Map([[10,101],[20,201]]);
const history = r => ({series:{id:r.seriesId,currentVersionId:currents.get(r.seriesId),revision:0,shared:true},versions:[r,{...r,id:r.id+1,version:'v2'}]});
const proof=[],writes=[],errors=[];
let delayA=false,failA=false,failB=false,delayWrite=false,releaseHistory,releaseWrite;
async function waitFor(test){for(let i=0;i<100;i++){if(await test())return;await new Promise(r=>setTimeout(r,20));}assert.fail('Fixture request not reached');}
(async()=>{
 const browser = await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
 const context = await browser.newContext({viewport:{width:1440,height:1000}});
 const page = await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname;if(!p.startsWith('/api/'))return route.continue();let data=[],status=200;
  if(p.endsWith('/auth/me'))data={id:901,role:'DIRECTOR',realName:'合成主任',department:'合成教研室'};
  else if(p.endsWith('/auth/csrf'))data={csrfToken:'synthetic-test-only'};
  else if(p.endsWith('/analytics/dashboard'))data={academicTerm:'2026-2027秋季',totalCourses:2,supervisedCourses:0,totalEvaluations:0,coverageRate:0};
  else if(p==='/api/v1/courses')data=[course(901),course(902)];
  else if(p==='/api/v1/resources')data=[a,b].map(r=>({...r,id:currents.get(r.seriesId)}));
  else if(p.endsWith('/sprint3/quality'))data={term:'2026-2027秋季',total:0,rows:[]};
  else if(p.endsWith('/resources/101/history')){if(delayA)await new Promise(r=>releaseHistory=r);data=history(a);if(failA)status=503;}
  else if(p.endsWith('/resources/201/history')||p.endsWith('/resources/202/history')){data=history(b);if(failB)status=503;}
  else if(req.method()==='PUT'){
   const body=req.postDataJSON();writes.push({path:p,body});if(delayWrite)await new Promise(r=>releaseWrite=r);
   currents.set(Number(p.split('/')[5]),body.versionId);data={};
  }else if(p.endsWith('/resources/upload')){writes.push({path:p,body:req.postData()});data={};}
  await route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status,message:status===200?'success':'模拟历史加载失败',data})});
 });
 const selected=page.getByLabel('当前资源');
 const series=id=>page.getByText(new RegExp('系列'+id+' · 当前版本ID'));
 async function versions(){await page.getByRole('tab',{name:/资源版本/}).click();}
 async function quality(){await page.getByRole('tab',{name:/质量汇总/}).click();}
 try {
  await page.goto(process.env.UI_URL || 'http://127.0.0.1:15179');
  await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await versions();
  await selected.selectOption('101');await series(10).waitFor();
  await quality();delayA=true;await versions();await waitFor(()=>!!releaseHistory);
  assert.equal(await page.getByRole('button',{name:'设为当前版',exact:true}).count(),0);
  assert.equal(await page.getByLabel('上传此系列的新版本').count(),0);
  await selected.selectOption('201');await series(20).waitFor();
  releaseHistory();releaseHistory=undefined;delayA=false;await page.waitForTimeout(100);
  assert.equal(await selected.inputValue(),'201');assert.equal(await series(10).count(),0);
  proof.push('恢复记忆选择的慢请求不覆盖新资源；加载期间没有旧写操作');

  delayWrite=true;await page.getByRole('button',{name:'设为当前版',exact:true}).click();await waitFor(()=>!!releaseWrite);
  assert.equal(await selected.isDisabled(),true);
  await page.getByRole('button',{name:'设为当前版',exact:true}).evaluate(button=>button.click());
  assert.equal(writes.length,1);assert.equal(writes[0].path,'/api/v1/resources/series/20/current');assert.equal(writes[0].body.versionId,202);
  releaseWrite();releaseWrite=undefined;delayWrite=false;
  await page.getByText('系列20 · 当前版本ID 202 · 系列记录版本 0',{exact:true}).waitFor();
  assert.equal(await selected.inputValue(),'201');
  proof.push('版本切换固定到新资源系列，写入期间禁止切换与重复提交');

  await page.getByLabel('上传此系列的新版本').setInputFiles({name:'synthetic.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 synthetic fixture')});
  await page.getByRole('button',{name:'设为当前版',exact:true}).click();
  await page.getByRole('dialog',{name:'有未保存的修改'}).waitFor();
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();
  assert.equal(writes.length,1);assert.equal(await page.getByLabel('上传此系列的新版本').evaluate(input=>input.files.length),1);
  proof.push('待上传文件触发离开保护，取消版本切换保留文件且不写入');
  await page.getByRole('button',{name:'上传新版并设为当前',exact:true}).click();
  await page.getByText('新版本已保存，历史文件保留',{exact:true}).waitFor();
  const upload=writes.find(w=>w.path==='/api/v1/resources/upload');assert.ok(upload);
  assert.match(upload.body,/name="seriesId"\r\n\r\n20\r\n/);assert.match(upload.body,/name="courseId"\r\n\r\n902\r\n/);
  proof.push('新版上传使用当前资源课程与系列，不沿用旧历史');

  await selected.selectOption('101');await series(10).waitFor();await quality();delayA=true;failA=true;await versions();await waitFor(()=>!!releaseHistory);
  await selected.selectOption('202');await series(20).waitFor();releaseHistory();releaseHistory=undefined;delayA=false;await page.waitForTimeout(100);failA=false;
  assert.equal(await page.getByText('模拟历史加载失败',{exact:true}).count(),0);assert.equal(await series(10).count(),0);
  proof.push('过时请求的失败不污染新资源历史与错误提示');

  failB=true;await page.getByRole('button',{name:'刷新资源',exact:true}).click();await page.getByText('模拟历史加载失败',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'设为当前版',exact:true}).count(),0);assert.equal(await page.getByLabel('上传此系列的新版本').count(),0);
  failB=false;await page.getByRole('button',{name:'刷新资源',exact:true}).click();await series(20).waitFor();
  proof.push('当前历史加载失败撤销操作区，刷新可恢复');

  await selected.selectOption('101');await series(10).waitFor();await quality();delayA=true;await versions();await waitFor(()=>!!releaseHistory);
  await quality();releaseHistory();releaseHistory=undefined;delayA=false;await page.waitForTimeout(100);await versions();await series(10).waitFor();
  proof.push('卸载撤销历史请求，重新进入正常恢复');
  assert.deepEqual(errors,[]);
  await page.screenshot({path:path.join(dir,'resource-versions.png'),fullPage:true});
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({proof,pageErrors:errors,writes:writes.map(w=>({path:w.path,body:typeof w.body==='object'?w.body:'synthetic upload'})),syntheticOnly:true},null,2));
  console.log(JSON.stringify({proof,pageErrors:errors},null,2));
 } catch(e){await page.screenshot({path:path.join(dir,'failure.png'),fullPage:true});throw e;}
 finally{releaseHistory?.();releaseWrite?.();await page.unrouteAll({behavior:'ignoreErrors'});await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
