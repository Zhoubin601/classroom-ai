const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {execFileSync}=require('node:child_process');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
const state=JSON.parse(fs.readFileSync(path.join(__dirname,'environment.json'),'utf8').replace(/^\uFEFF/,''));
assert.match(state.mysql,/^classroom-exp3-browser-[a-f0-9]{10}$/);
const result={observedAt:new Date().toISOString(),mode:'Real Chrome, real server, isolated MySQL; asymmetric published scores compared with canvas pixels after animation',pageErrors:[]};
let browser;
async function api(page,route,method='GET',data){
  const observed=await page.evaluate(async ({route,method,data})=>{
    const r=await fetch(route,{method,headers:{Authorization:'Bearer '+localStorage.getItem('jwtToken'),'Content-Type':'application/json'},...(data===undefined?{}:{body:JSON.stringify(data)})});
    return {http:r.status,body:await r.json()};
  },{route,method,data});
  assert.equal(observed.http,200,route);assert.equal(observed.body.code,200,observed.body.message);return observed.body.data;
}
async function login(role){
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
  page.on('pageerror',e=>result.pageErrors.push(e.message));
  await page.goto('http://127.0.0.1:15173');
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(role);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
  await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
  await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();return page;
}
(async()=>{
  browser=await chromium.launch({headless:true,executablePath:process.env.EXP3_CHROMIUM_PATH});
  try{
    const director=await login('director'),supervisor=await login('supervisor');
    const evaluation=await api(supervisor,'/api/v1/supervisions','POST',{offeringId:2,listenTopic:'合成雷达像素专项',scoreAttitude:10,scoreContent:15,scoreMethod:20,scoreEffect:25,highlights:'像素专项亮点一\n像素专项亮点二\n像素专项亮点三',suggestions:'合成雷达专项建议',isDraft:false});
    await api(director,`/api/v1/supervisions/${evaluation.id}/review`,'POST',{approved:true,note:'合成像素专项'});
    assert.ok(Number.isSafeInteger(evaluation.id));
    execFileSync('docker',['exec','-e','MYSQL_PWD='+process.env.ROLE_TEST_MYSQL_PASSWORD,state.mysql,'mysql','-uroot','classroom_ai','-e',`UPDATE t_supervision_evaluation SET publish_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=${evaluation.id}`]);
    const teacher=await login('guojun');
    const radar=await api(teacher,'/api/v1/supervisions/analytics/radar?teacherName='+encodeURIComponent('郭军'));
    assert.deepEqual([radar.attitudeScore,radar.contentScore,radar.methodScore,radar.effectScore],[10,15,20,25]);
    const chart=teacher.locator('div[_echarts_instance_]').first();await chart.waitFor();
    await teacher.waitForTimeout(2200);
    const pixels=await chart.evaluate(el=>{
      const c=el.querySelector('canvas'),data=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
      let count=0,minX=c.width,minY=c.height,maxX=0,maxY=0;
      for(let y=0;y<c.height;y++)for(let x=0;x<c.width;x++){
        const i=(y*c.width+x)*4;
        if(data[i]>=60&&data[i]<=100&&data[i+1]>=50&&data[i+1]<=100&&data[i+2]>180&&data[i+3]>180){count++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
      }
      return {width:c.width,height:c.height,count,spanX:maxX-minX,spanY:maxY-minY};
    });
    result.scores=[radar.attitudeScore,radar.contentScore,radar.methodScore,radar.effectScore];result.pixels=pixels;
    await chart.screenshot({path:path.join(__dirname,'radar-asymmetric-rendered.png')});
    assert.ok(pixels.count>150&&pixels.spanX>80&&pixels.spanY>80,'Nonzero published scores must draw a visible radar polygon, not a center dot');
    assert.deepEqual(result.pageErrors,[]);result.status='PASS';console.log('PASS real asymmetrical radar scores produce rendered polygon',JSON.stringify(result));
  }catch(e){result.status='FAIL';result.error=e.message;console.error(e.stack);process.exitCode=1;}
  finally{fs.writeFileSync(path.join(__dirname,'results.json'),JSON.stringify(result,null,2));await browser?.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
