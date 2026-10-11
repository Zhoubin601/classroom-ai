// Read-only deployed-instance checks. No business writes, no recorded credentials.
const fs = require('node:fs'), path = require('node:path');
const { chromium } = require('C:/Users/a3185/AppData/Local/uv/cache/archive-v0/GcBvtI027rUzG_nt/Lib/site-packages/playwright/driver/package');
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const result = {observedAt:new Date().toISOString(),frontend:'http://127.0.0.1:5173',backend:'http://127.0.0.1:8080',mode:'Real Chrome UI login and read-only requests through daily Vite proxy',roles:[],pageErrors:[]};
  try {
    for (const role of ['director','guojun','supervisor']) {
      const context = await browser.newContext(), page = await context.newPage();
      page.on('pageerror', e => result.pageErrors.push({role,message:e.message}));
      const entry = {role,requests:[]};result.roles.push(entry);
      try {
        await page.goto(result.frontend);
        await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(role);
        await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.DAILY_DEMO_PASSWORD || '123456');
        await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
        await page.getByRole('button',{name:'退出登录',exact:true}).waitFor({timeout:12000});
        entry.login = 'PASS';
        for (const route of ['/api/v1/auth/me','/api/v1/courses','/api/v1/courses/10','/api/v1/courses/offerings','/api/v1/resources/micro-slices/course/10','/api/visual/overview?offeringId=10','/api/v1/attendance/offering/10']) {
          const observed = await page.evaluate(async route => {
            const r = await fetch(route,{headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken')}});
            let body;try{body=await r.json();}catch{}
            const scope = route==='/api/v1/auth/me' && body?.data ? {role:body.data.role,department:body.data.department,authorizedMajors:body.data.authorizedMajors} : route==='/api/v1/courses/10' && body?.data ? {department:body.data.department,majorCode:body.data.majorCode} : undefined;
            return {route,http:r.status,code:body?.code,count:Array.isArray(body?.data)?body.data.length:undefined,scope};
          },route);
          entry.requests.push(observed);
        }
        if (role === 'guojun') {
          await page.getByText('督导随堂评价 4 维雷达图 (US-17)',{exact:true}).waitFor();
          await page.waitForTimeout(2000); // Let the actual chart's default animation settle.
          entry.radar = await page.evaluate(async () => {
            const r = await fetch('/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent('郭军'),{headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken')}});
            const b = await r.json();
            const charts = Array.from(document.querySelectorAll('div[_echarts_instance_]')).map(el=>{
              const canvas=el.querySelector('canvas');if(!canvas)return {canvas:false};
              const {width,height}=canvas, pixels=canvas.getContext('2d').getImageData(0,0,width,height).data;
              let count=0,minX=width,minY=height,maxX=0,maxY=0;
              for(let y=0;y<height;y++)for(let x=0;x<width;x++){
                const i=(y*width+x)*4;
                if(pixels[i]>=60&&pixels[i]<=100&&pixels[i+1]>=50&&pixels[i+1]<=100&&pixels[i+2]>180&&pixels[i+3]>180){count++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
              }
              return {canvas:true,width,height,scoreColorPixels:count,scoreColorSpanX:maxX-minX,scoreColorSpanY:maxY-minY};
            });
            return {http:r.status,evaluationCount:b.data?.evaluationCount,scores:[b.data?.attitudeScore,b.data?.contentScore,b.data?.methodScore,b.data?.effectScore],charts};
          });
          await page.locator('div[_echarts_instance_]').first().screenshot({path:path.join(__dirname,'daily-radar-rendered.png')});
        }
      } catch(e) { entry.error = e.message; }
      await context.close();
    }
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(__dirname,'daily-readonly.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result));
  }
})().catch(e=>{console.error(e.message);process.exitCode=1;});
