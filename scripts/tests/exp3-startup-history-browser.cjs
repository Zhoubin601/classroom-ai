// All responses come from the isolated real backend; only delivery timing is delayed.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const dir=path.resolve(process.env.EXP3_RACE_EVIDENCE_DIR||'docs/runtime/history-startup-race');fs.mkdirSync(dir,{recursive:true});
(async()=>{
 const backend=process.env.EXP3_BACKEND_URL,frontend=process.env.EXP3_FRONTEND_URL;
 const login=await (await fetch(backend+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'guojun',password:'123456'})})).json();assert.equal(login.code,200);
 async function api(p,method='GET',data){const r=await fetch(backend+p,{method,headers:{Authorization:'Bearer '+login.data.token,'Content-Type':'application/json'},...(data?{body:JSON.stringify(data)}:{})});const b=await r.json();assert.equal(b.code,200,JSON.stringify(b));return b.data;}
 const offerings=await api('/api/v1/courses/offerings');assert.ok(offerings.length>1,'two real offerings required for a genuine selection change');const offering=offerings[1],courseId=offering.course.id;
 const expected=[];
 for(const suffix of ['old','current']){const v=await api(`/api/v1/syllabus/course/${courseId}/from-plan`,'POST',{syllabusVersion:`R${Date.now()}-${suffix}`,planVersion:'RECOMMENDED-12'});const rows=await api(`/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${v.id}`);const goal='合成时序测试-'+suffix;await api('/api/v1/syllabus/indicators/'+rows[0].id,'PUT',{supportWeight:suffix==='old'?'H':'L',targetGoal:goal});expected.push({id:v.id,goal});}
 const browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});const ctx=await browser.newContext();await ctx.addInitScript(token=>localStorage.setItem('jwtToken',token),login.data.token);const page=await ctx.newPage();let release,delayed=false;const requests=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().includes('/syllabus/'))requests.push({url:r.url(),time:Date.now()})});
 await page.route('**/api/v1/schedules?*',async route=>{const response=await route.fetch();if(!delayed){delayed=true;await new Promise(r=>release=r);}await route.fulfill({response});});
 try{
  await page.goto(frontend);for(let i=0;i<200&&!release;i++)await page.waitForTimeout(20);assert.ok(release,'startup schedule must reach timing gate');
  await page.getByLabel('当前主讲课程',{exact:true}).selectOption(String(offering.id));
  await page.getByRole('tab',{name:/^指标矩阵/}).click();const selector=page.getByLabel('查看大纲版本',{exact:true});await page.getByRole('row').filter({hasText:expected[1].goal}).waitFor();
  await selector.selectOption(String(expected[0].id));await page.getByRole('row').filter({hasText:expected[0].goal}).waitFor();
  const before={id:await selector.inputValue(),oldVisible:await page.getByRole('row').filter({hasText:expected[0].goal}).count()};release();release=undefined;
  // The released startup chain must finish without replacing the user's later selection.
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(250);
  const result={expectedHistoricalId:expected[0].id,actualSelectedId:await selector.inputValue(),before,oldVisibleAfter:await page.getByRole('row').filter({hasText:expected[0].goal}).count(),currentVisibleAfter:await page.getByRole('row').filter({hasText:expected[1].goal}).count(),realBackend:true,timingOnly:true,pageErrors:errors};
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify(result,null,2));await page.screenshot({path:path.join(dir,'history-preserved.png'),fullPage:true});assert.equal(result.oldVisibleAfter,1);assert.equal(result.currentVisibleAfter,0);assert.equal(result.actualSelectedId,String(expected[0].id));assert.deepEqual(errors,[]);assert.equal(await page.getByRole('button',{name:'新增认证指标点',exact:true}).isDisabled(),true);console.log(JSON.stringify(result,null,2));
 }finally{release?.();fs.writeFileSync(path.join(dir,'requests.json'),JSON.stringify(requests,null,2));await page.screenshot({path:path.join(dir,'final.png'),fullPage:true}).catch(()=>{});await page.unrouteAll({behavior:'ignoreErrors'});await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
