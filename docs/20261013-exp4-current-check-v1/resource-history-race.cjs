const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
const dir = __dirname;
const course = id => ({id, courseCode:'SYN-'+id, courseName:'合成课程'+id, department:'合成教研室', majorCode:'SE'});
const resource = (id, courseId, seriesId) => ({id, course:course(courseId), seriesId, resourceName:'合成资源'+id, version:'v1', tags:[], chapter:'合成章节', isPublic:true});
const a = resource(101,901,10), b = resource(201,902,20);
const history = r => ({series:{id:r.seriesId,currentVersionId:r.id,revision:0,shared:true},versions:[r,{...r,id:r.id+1,version:'v2'}]});
(async()=>{
 const browser = await chromium.launch({headless:true,executablePath:'/usr/bin/chromium'});
 const context = await browser.newContext({viewport:{width:1440,height:1000}});
 const page = await context.newPage(); const writes=[]; let release, requested=false,historyRequests=0;
 page.on('pageerror',e=>console.error('PAGE ERROR:',e.message));
 await context.addInitScript(()=>localStorage.setItem('jwtToken','synthetic-fixture-token'));
 await context.route('**/api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname;if(!p.startsWith('/api/'))return route.continue();let data=[];
  if(p.endsWith('/auth/me'))data={id:901,role:'DIRECTOR',realName:'合成主任',department:'合成教研室'};
  else if(p.endsWith('/auth/csrf'))data={csrfToken:'synthetic-test-only'};
  else if(p.endsWith('/analytics/dashboard'))data={academicTerm:'2026-2027秋季',totalCourses:2,supervisedCourses:0,totalEvaluations:0,coverageRate:0};
  else if(p==='/api/v1/courses')data=[course(901),course(902)];
  else if(p==='/api/v1/resources')data=[a,b];
  else if(p.endsWith('/sprint3/quality'))data={term:'2026-2027秋季',total:0,rows:[]};
  else if(p.endsWith('/resources/101/history')){if(++historyRequests===2){requested=true;await new Promise(r=>release=r);}data=history(a);}
  else if(p.endsWith('/resources/201/history'))data=history(b);
  else if(req.method()==='PUT'){writes.push({path:p,body:req.postDataJSON()});data={};}
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,message:'success',data})});
 });
 try {
  await page.goto('http://127.0.0.1:15179');
  await page.getByRole('button',{name:'统计与反馈',exact:true}).click();
  await page.getByRole('tab',{name:/资源版本/}).click();
  await page.getByLabel('当前资源').selectOption('101');
  await page.getByText('系列10 · 当前版本ID 101 · 系列记录版本 0',{exact:true}).waitFor();
  await page.getByRole('tab',{name:/质量汇总/}).click();
  await page.getByRole('tab',{name:/资源版本/}).click();
  for(let i=0;i<100&&!requested;i++)await page.waitForTimeout(20);assert.ok(requested);
  await page.getByLabel('当前资源').selectOption('201');
  await page.getByText('系列20 · 当前版本ID 201 · 系列记录版本 0',{exact:true}).waitFor();
  release();
  await page.getByText('系列10 · 当前版本ID 101 · 系列记录版本 0',{exact:true}).waitFor();
  const selected=await page.getByLabel('当前资源').inputValue();
  assert.equal(selected,'201');
  await page.screenshot({path:path.join(dir,'resource-history-race.png'),fullPage:true});
  await page.getByRole('button',{name:'设为当前版',exact:true}).click();
  await page.waitForTimeout(100);assert.equal(writes[0].path,'/api/v1/resources/series/10/current');
  fs.writeFileSync(path.join(dir,'resource-history-race.json'),JSON.stringify({selectedResource:selected,displayedSeries:10,writes,syntheticOnly:true},null,2));
  console.log('CONFIRMED: resource 201 selected; delayed resource 101 overwrites series and writes series 10',JSON.stringify(writes));
 } catch(e) {await page.screenshot({path:path.join(dir,'race-debug.png'),fullPage:true});console.error(await page.locator('body').innerText());throw e;} finally {release?.();await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
