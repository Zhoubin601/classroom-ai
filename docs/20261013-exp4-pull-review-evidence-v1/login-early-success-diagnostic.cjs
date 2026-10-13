// Synthetic API fixtures only. Reproduce old /me success before new login completes.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
const dir=path.resolve(process.env.LOGIN_PROBE_EVIDENCE);
fs.mkdirSync(dir,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
 const context=await browser.newContext(),page=await context.newPage();
 await context.addInitScript(()=>localStorage.setItem('jwtToken','synthetic-old-token'));
 const newUser={id:902,role:'TEACHER',realName:'合成新教师',department:'合成教研室',teacherCode:'T902',token:'synthetic-new-token'};
 let releaseMe,releaseLogin,probeUsedOldToken=false;
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/api/**',async route=>{
  const p=new URL(route.request().url()).pathname;
  if(!p.startsWith('/api/'))return route.continue();
  let data=[];
  if(p.endsWith('/auth/csrf'))data={csrfToken:'synthetic-only'};
  else if(p.endsWith('/auth/me')){probeUsedOldToken=route.request().headers().authorization==='Bearer synthetic-old-token';await new Promise(r=>releaseMe=r);data={id:901,role:'ADMIN',realName:'合成旧管理员'};}
  else if(p.endsWith('/auth/login')){await new Promise(r=>releaseLogin=r);data=newUser;}
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,message:'success',data})});
 });
 async function waitFor(fn){for(let i=0;i<150;i++){if(await fn())return;await page.waitForTimeout(20);}assert.fail('fixture not reached');}
 try{
  await page.goto(process.env.UI_URL);
  await waitFor(()=>!!releaseMe);
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('synthetic-teacher');
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('synthetic-password');
  await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await waitFor(()=>!!releaseLogin);
  releaseMe();releaseMe=undefined;
  await page.locator('.account-button').filter({hasText:'合成旧管理员'}).waitFor();
  await page.waitForTimeout(150);
  releaseLogin();releaseLogin=undefined;
  await waitFor(()=>page.evaluate(()=>localStorage.getItem('jwtToken')==='synthetic-new-token'));
  await page.waitForTimeout(400);
  const result={url:process.env.UI_URL,probeUsedOldToken,expectedAccount:newUser.realName,actualAccount:await page.locator('.account-button').innerText(),newTokenStored:await page.evaluate(()=>localStorage.getItem('jwtToken')==='synthetic-new-token'),adminNavigationVisible:await page.getByRole('button',{name:'模板管理',exact:true}).count()===1,newTeacherVisible:await page.locator('.account-button').filter({hasText:newUser.realName}).count()===1,pageErrors:errors,syntheticOnly:true};
  await page.screenshot({path:path.join(dir,'account-token-mismatch.png'),fullPage:true});
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify(result,null,2));
  assert.equal(result.newTokenStored,true);
  assert.equal(result.probeUsedOldToken,true);
  assert.equal(result.newTeacherVisible,false);
  assert.equal(result.adminNavigationVisible,true);
  console.log(JSON.stringify(result,null,2));
 }finally{releaseMe?.();releaseLogin?.();await page.unrouteAll({behavior:'ignoreErrors'});await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
