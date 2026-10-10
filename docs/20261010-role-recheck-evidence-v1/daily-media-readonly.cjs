const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const base = 'http://127.0.0.1:5173';
const results = [];
let browser;
async function main() {
  browser = await chromium.launch({headless:true, executablePath:process.env.EXP3_CHROMIUM_PATH});
  try {
    const page = await browser.newPage();
    await page.goto(base);
    await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('guojun');
    await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.DAILY_TEST_PASSWORD);
    await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
    await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
    const token = await page.evaluate(()=>localStorage.getItem('jwtToken'));
    const headers = {Authorization:'Bearer '+token};
    const coursesResponse = await page.request.get(base+'/api/v1/courses',{headers});
    const courses = (await coursesResponse.json()).data;
    for (const course of courses) {
      const response = await page.request.get(base+'/api/v1/resources/micro-slices/course/'+course.id,{headers});
      assert.equal(response.status(),200);
      for (const slice of (await response.json()).data) {
        if (slice.sliceUrl.startsWith('/uploads/micro/')) {
          const decoded = await page.evaluate(async ({id,token})=>{
            const response = await fetch('/api/v1/resources/micro-slices/'+id+'/video',{headers:{Authorization:'Bearer '+token}});
            if (!response.ok) return {http:response.status};
            const blob = await response.blob(), url = URL.createObjectURL(blob), video = document.createElement('video');
            video.muted=true; video.src=url; document.body.append(video);
            try {
              await Promise.race([new Promise((resolve,reject)=>{video.onloadeddata=resolve;video.onerror=()=>reject(new Error('decode failed'));}),new Promise((_,reject)=>setTimeout(()=>reject(new Error('decode timeout')),8000))]);
              await video.play();
              await new Promise(resolve=>setTimeout(resolve,700));
              return {http:response.status,mime:blob.type,bytes:blob.size,width:video.videoWidth,height:video.videoHeight,time:video.currentTime};
            } finally { video.pause();video.remove();URL.revokeObjectURL(url); }
          },{id:slice.id,token});
          results.push({sliceId:slice.id,courseId:course.id,kind:'uploaded',status:decoded.http===200&&decoded.width>0&&decoded.time>0?'PLAYABLE':'FAILED',decoded});
        } else {
          const url = new URL(slice.sliceUrl,base);
          if (url.origin!==base) {
            results.push({sliceId:slice.id,courseId:course.id,kind:'external-reference',status:'NOT_TESTED',reason:'不访问第三方地址'});
          } else {
            const response = await page.request.get(url.href);
            const mime = response.headers()['content-type']||'';
            results.push({sliceId:slice.id,courseId:course.id,kind:'legacy-local-reference',status:response.ok()&&mime.startsWith('video/')?'VIDEO_RESPONSE':'NOT_VIDEO',http:response.status(),mime});
          }
        }
      }
    }
    console.log(JSON.stringify(results));
  } finally {
    fs.writeFileSync(path.join(__dirname,'daily-media-readonly.json'),JSON.stringify({base,businessWrites:false,results},null,2));
    await browser?.close();
  }
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
