// Read-only UI audit. Login/logout are the only permitted POST requests.
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright');
const evidence = path.join(__dirname, '20261009-ui-review-evidence-v1');
fs.mkdirSync(evidence, { recursive: true });
const results = [];
async function measure(page, name) {
  const data = await page.evaluate(() => {
    const visible = e => !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length);
    const controls = [...document.querySelectorAll('input:not([type=hidden]),select,textarea')].filter(visible);
    const unlabeled = controls.filter(e => !e.labels?.length && !e.getAttribute('aria-label') && !e.getAttribute('aria-labelledby'));
    const buttons = [...document.querySelectorAll('button')].filter(visible);
    return {
      viewport: { width: innerWidth, height: innerHeight }, scrollWidth: document.documentElement.scrollWidth,
      headerHeight: document.querySelector('header')?.getBoundingClientRect().height,
      mainTop: document.querySelector('main')?.getBoundingClientRect().top,
      controls: controls.length, unlabeledControls: unlabeled.length,
      unlabeledExamples: unlabeled.slice(0,5).map(e => ({tag:e.tagName,placeholder:e.type==='password'?'[密码输入框]':e.getAttribute('placeholder')})),
      smallButtons: buttons.filter(e => {const r=e.getBoundingClientRect(); return r.height<24 || r.width<24;}).length,
      navSelectedStates: [...document.querySelectorAll('nav button')].map(e=>({text:e.textContent.trim(),current:e.getAttribute('aria-current'),selected:e.getAttribute('aria-selected'),pressed:e.getAttribute('aria-pressed')})),
      overflow: [...document.querySelectorAll('main > div > div,header,nav,input,select')].filter(visible).map(e=>({tag:e.tagName,class:e.className,left:Math.round(e.getBoundingClientRect().left),right:Math.round(e.getBoundingClientRect().right)})).filter(e=>e.right>innerWidth+1 || e.left< -1).slice(0,8),
      textSizes: [...new Set([...document.querySelectorAll('main p,main span,main td')].filter(visible).map(e=>getComputedStyle(e).fontSize))].sort(),
      animations: [...document.querySelectorAll('*')].filter(e=>visible(e)&&getComputedStyle(e).animationName!=='none').length
    };
  });
  results.push({name,...data});
  console.log(JSON.stringify({name,...data}));
}
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  try {
    const context=await browser.newContext({viewport:{width:1440,height:900},timezoneId:'Asia/Shanghai'});
    await context.route('**/api/**', async route=>{
      const r=route.request();
      if(['GET','HEAD','OPTIONS'].includes(r.method()) || /\/auth\/(login|logout)$/.test(new URL(r.url()).pathname)) return route.continue();
      results.push({name:'blocked-business-write',method:r.method(),path:new URL(r.url()).pathname});
      await route.abort('blockedbyclient');
    });
    const page=await context.newPage();
    page.setDefaultTimeout(12000);
    page.on('dialog',async d=>{results.push({name:'native-dialog',type:d.type()});await d.dismiss();});
    await page.goto('http://127.0.0.1:5173');
    await page.getByRole('button',{name:'立即验证并登录',exact:true}).waitFor();
    await measure(page,'login-desktop');
    const switchButton=page.locator('button[title="点击隐藏快捷卡片面板"]');
    await switchButton.focus();
    results.push({name:'login-switch-focus',...await switchButton.evaluate(e=>{const s=getComputedStyle(e);return {outlineStyle:s.outlineStyle,outlineWidth:s.outlineWidth,boxShadow:s.boxShadow,role:e.getAttribute('role'),checked:e.getAttribute('aria-checked'),label:e.getAttribute('aria-label')};})});
    async function login(username){
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      const passwordInput=page.locator('input[autocomplete="current-password"]');
      const demoHint=await passwordInput.getAttribute('placeholder');
      const demoPassword=process.env.UI_DEMO_PASSWORD || demoHint?.match(/默认\s+(\S+)\)/)?.[1];
      if(!demoPassword) throw new Error('未提供演示登录凭据');
      await passwordInput.fill(demoPassword);
      await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
      await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
      await page.waitForTimeout(1200);
    }
    async function sizes(name, screenshot=true){
      for(const [w,h] of [[1440,900],[768,1024],[375,812],[667,375]]){
        await page.setViewportSize({width:w,height:h});await page.evaluate(()=>scrollTo(0,0));await page.waitForTimeout(350);await measure(page,`${name}-${w}`);
        if(screenshot && [1440,375].includes(w)) await page.screenshot({path:path.join(evidence,`${name}-${w}.png`)});
      }
      await page.setViewportSize({width:1440,height:900});
    }
    await login('director');
    await sizes('director');
    await page.getByRole('button',{name:'新增专业课程档案',exact:true}).click();
    const overlay=page.locator('div.fixed.inset-0').last();
    await overlay.waitFor();
    await page.waitForTimeout(350);
    results.push({name:'course-modal-open',dialogs:await page.getByRole('dialog').count(),focusInside:await overlay.evaluate(e=>e.contains(document.activeElement)),bodyOverflow:await page.evaluate(()=>getComputedStyle(document.body).overflow), ...await overlay.evaluate(e=>{const r=e.getBoundingClientRect(); const m=document.querySelector('main'),s=getComputedStyle(m);return {overlayTop:r.top,overlayHeight:r.height,viewportHeight:innerHeight,mainTransform:s.transform,mainAnimation:s.animationName,mainFillMode:s.animationFillMode};})});
    await page.keyboard.press('Escape');
    results.push({name:'course-modal-escape',stillVisible:await overlay.isVisible()});
    for(const [w,h] of [[375,812],[667,375]]){
      await page.setViewportSize({width:w,height:h});
      await page.evaluate(()=>scrollTo(0,0));await page.waitForTimeout(350);
      results.push({name:`course-modal-${w}`, ...await overlay.evaluate(e=>{const card=e.firstElementChild,r=card.getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height,viewportHeight:innerHeight,overflowY:getComputedStyle(card).overflowY};})});
    }
    await page.setViewportSize({width:1440,height:900});
    await page.getByRole('button',{name:'取消',exact:true}).last().click();
    await page.getByRole('button',{name:'学生人脸档案库',exact:true}).click();await page.waitForTimeout(600);await sizes('students',false);
    await page.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();await page.waitForTimeout(600);await sizes('attendance',false);
    await page.getByRole('button',{name:'退出登录',exact:true}).click();
    await login('guojun');await sizes('teacher');
    await page.getByRole('button',{name:'上传新课件/教案',exact:true}).click();
    if(await page.locator('div.fixed.inset-0').count()){
      await page.waitForTimeout(350);
      results.push({name:'teacher-modal',dialogs:await page.getByRole('dialog').count()});
      await page.keyboard.press('Escape');
      results.push({name:'teacher-modal-escape',stillVisible:await page.locator('div.fixed.inset-0').last().isVisible()});
      await page.getByRole('button',{name:'取消',exact:true}).last().click().catch(()=>{});
    }
    await page.getByRole('button',{name:'退出登录',exact:true}).click();
    await login('supervisor');await sizes('supervisor');
    await page.emulateMedia({reducedMotion:'reduce'});await measure(page,'supervisor-reduced-motion');
    await page.getByRole('button',{name:'退出登录',exact:true}).click();
  } catch(e) { results.push({name:'audit-error',message:e.message}); console.error(e.message);process.exitCode=1; }
  finally {fs.writeFileSync(path.join(evidence,'measurements.json'),JSON.stringify(results,null,2));await browser.close();}
})();
