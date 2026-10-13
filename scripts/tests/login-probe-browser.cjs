// Delayed startup identity replies must not cancel or replace a new login.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const dir=path.resolve(process.env.LOGIN_PROBE_EVIDENCE||'docs/login-probe-browser');fs.mkdirSync(dir,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});const proof=[],errors=[];
 try{
  for(const status of [401,200]){
   const context=await browser.newContext(),page=await context.newPage();let release,releaseLogin,expired=false;page.on('pageerror',e=>errors.push(e.message));
   const user={id:902,role:'TEACHER',realName:'合成新教师',department:'合成教研室',teacherCode:'T902',token:'synthetic-login-fixture'};
   await context.route('**/api/**',async route=>{
    const req=route.request(),url=new URL(req.url());if(!url.pathname.startsWith('/api/'))return route.continue();let data=[],code=200;
    if(url.pathname.endsWith('/auth/csrf'))data={csrfToken:'synthetic-fixture'};
    else if(url.pathname.endsWith('/auth/login')){if(status===401)await new Promise(r=>releaseLogin=r);data=user;}
    else if(url.pathname.endsWith('/auth/me')){
     if(!req.headers().authorization){await new Promise(r=>release=r);code=status;data=status===200?{id:901,role:'ADMIN',realName:'合成旧管理员'}:null;}
     else data=user;
    }
    else if(expired&&url.pathname.endsWith('/sprint3/radar'))code=401;
    await route.fulfill({status:code,contentType:'application/json',body:JSON.stringify({code,message:code===401?'初始会话未登录':'success',data})});
   });
   try{
    await page.goto(process.env.UI_URL||'http://127.0.0.1:15181');
    for(let i=0;i<100&&!release;i++)await page.waitForTimeout(20);assert.ok(release);
    await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('synthetic-teacher');
    await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('synthetic-fixture');
    await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
    if(status===401){for(let i=0;i<100&&!releaseLogin;i++)await page.waitForTimeout(20);assert.ok(releaseLogin);release();release=undefined;await page.waitForTimeout(100);releaseLogin();releaseLogin=undefined;}
    await page.locator('.account-button').filter({hasText:user.realName}).waitFor();
    release?.();release=undefined;await page.waitForTimeout(150);
    assert.equal(await page.locator('.account-button').filter({hasText:user.realName}).count(),1);
    assert.equal(await page.getByRole('button',{name:'模板管理',exact:true}).count(),0);
    assert.equal(await page.getByText('登录会话已失效，请重新登录',{exact:true}).count(),0);
    await page.screenshot({path:path.join(dir,`startup-${status}.png`),fullPage:true});
    proof.push(status===401?'初始401不取消在途登录':'初始旧身份成功响应不覆盖新的登录');
    if(status===200){expired=true;await page.getByRole('button',{name:'统计与反馈',exact:true}).click();await page.getByRole('heading',{name:'教务统一身份认证',exact:true}).waitFor();assert.equal(await page.locator('.app-sidebar').count(),0);proof.push('正式业务401仍立即撤销已认证页面');}
   }finally{release?.();releaseLogin?.();await page.unrouteAll({behavior:'ignoreErrors'});await context.close();}
  }
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({proof,pageErrors:errors,syntheticOnly:true},null,2));console.log(JSON.stringify({proof,pageErrors:errors},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
