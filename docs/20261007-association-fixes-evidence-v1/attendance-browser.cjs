// Real Vue UI with explicitly synthetic API responses; all API writes stay in this browser context.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
const user = {id:900,username:'synthetic-collaborator',realName:'协作测试教师',role:'TEACHER',department:'合成教研室',teacherCode:'CO-T'};
const offerings = [101,102].map(id=>({id,course:{id,courseCode:'SYN-'+id,courseName:'合成课程'+id},
  teacherCode:'OTHER-T',teacherName:'另一主讲教师',className:'合成班'+id,academicTerm:'合成学期',studentCount:30}));
const requests = [], errors=[];
let delayedHistory=false,historyStarted;
const historyWait = new Promise(resolve=>historyStarted=resolve);
async function main() {
  const browser=await chromium.launch({headless:true,executablePath:process.env.MAJOR_CHROMIUM_PATH});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    await page.addInitScript(()=>localStorage.setItem('jwtToken','synthetic-ui-token'));
    page.on('pageerror',error=>{errors.push(error.message);console.error(error.stack);});
    await page.route('**/api/**',async route=>{
      const req=route.request(),url=new URL(req.url());
      if (!url.pathname.startsWith('/api/')) return route.continue();
      requests.push({path:url.pathname,method:req.method(),offeringId:url.searchParams.get('offeringId')});
      let data=[];
      if(url.pathname.endsWith('/auth/me')) data=user;
      else if(url.pathname.endsWith('/auth/csrf')) data={csrfToken:'synthetic-csrf'};
      else if(url.pathname.endsWith('/courses/offerings')) data=offerings;
      else if(url.pathname.endsWith('/offerings/history')) data={totalOfferings:0,cumulativePersonTimes:0,items:[]};
      else if(url.pathname.endsWith('/attendance/current')) {
        const id=Number(url.searchParams.get('offeringId'));
        assert.ok([101,102].includes(id),'Current-session query must specify the selected offering');
        data=id===102?{id:777,status:'ACTIVE',offering:offerings[1]}:null;
      } else if(url.pathname.includes('/attendance/offering/')) {
        const id=Number(url.pathname.split('/').pop());
        if(delayedHistory && id===101) {historyStarted();await new Promise(resolve=>setTimeout(resolve,400));}
        data=id===101 && delayedHistory?[{id:501,status:'FINISHED',offering:offerings[0]}]:[];
      } else if(url.pathname.endsWith('/visual/monitor-status')) data={running:false};
      else if(url.pathname.endsWith('/visual/overview')) data={totalRegistered:30,currentPresent:0,currentAbsent:30,
        attendanceRate:0,realtimeLookupRate:0,lookdownCount:0,auditingCount:0,auditingStudentIds:[],focusLevel:'待启动',lastUpdateTime:''};
      else if(req.method()!=='GET') data=null;
      await route.fulfill({json:{code:200,data}});
    });
    await page.goto('http://127.0.0.1:5173');
    await page.getByRole('button',{name:'课堂智能考勤大屏',exact:true}).click();
    const select=page.locator('select').filter({has:page.locator('option[value="101"]')});
    await select.waitFor();
    assert.deepEqual(await select.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value)),['101','102']);
    assert.equal(await select.inputValue(),'101');
    const finish=page.getByRole('button',{name:'结束考勤并归档下课',exact:true});
    await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.includes('结束考勤并归档下课')&&b.disabled));
    assert.equal(await finish.isDisabled(),true);
    await select.selectOption('102');
    await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.includes('结束考勤并归档下课')&&!b.disabled));
    assert.equal(await select.inputValue(),'102');
    delayedHistory=true;
    await select.selectOption('101');await historyWait;
    await select.selectOption('102');
    await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.includes('结束考勤并归档下课')&&!b.disabled));
    await page.waitForTimeout(500);
    assert.equal(await select.inputValue(),'102');
    assert.match(await page.getByRole('button',{name:/考勤归档记录/}).innerText(),/0次/);
    assert.equal(requests.some(r=>r.path.startsWith('/api/v1/attendance/')&&r.method!=='GET'),false);
    assert.deepEqual(errors,[]);
    await page.screenshot({path:path.join(__dirname,'attendance-selection.png'),fullPage:true,animations:'disabled'});
    fs.writeFileSync(path.join(__dirname,'attendance-browser-result.json'),JSON.stringify({observedAt:new Date().toISOString(),
      mode:'Real Vue / Chromium; synthetic API; no live backend writes',checks:['协作教师可见服务端授权班次',
      '当前考勤按所选班次查询且不自动跳转','活动会话正确恢复','快速切换不显示其他班次的历史记录'],requests,errors},null,2)+'\n');
    console.log('PASS: 4 attendance UI checks; synthetic API only');
  } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
