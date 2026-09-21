// Complete UI -> real login/JWT -> Spring controllers -> isolated MySQL.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const preview = process.env.US0102_PREVIEW_URL || 'http://127.0.0.1:15173';
const backend = process.env.US0203_BACKEND_URL;
const courseId = process.env.US0203_COURSE_ID;
const intro = '请输入课程背景、学科定位、主要授课内容概括...';
const assessment = '例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%';
const objectives = '明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标...';

(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.US0102_CHROMIUM_PATH ? { executablePath: process.env.US0102_CHROMIUM_PATH } : {}) });
  try {
    async function open(role) {
      const context = await browser.newContext(); const page = await context.newPage();
      page.on('dialog', d => d.accept()); page.on('pageerror', e => console.log('PAGE ERROR',e.message));
      await page.route('**/api/**', async route => {
        const u = new URL(route.request().url());
        const response = await route.fetch({ url: backend + u.pathname + u.search, headers: { ...route.request().headers() } });
        await route.fulfill({ response });
      });
      await page.goto(preview);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('browser_'+role);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.US0203_TEST_PASSWORD);
      await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
      await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
      return page;
    }
    const a = await open('TEACHER'), b = await open('TEACHER');
    const save = p => p.getByRole('button',{name:'暂存草稿',exact:true});
    async function ready(p) { await p.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.innerText.trim()==='暂存草稿' && !b.disabled)); }
    async function write(p, label, action) { const response = p.waitForResponse(r => r.url().endsWith(label) && r.request().method() !== 'GET'); await action(); return response; }
    async function api(p,path,method='GET',data) {
      const token=await p.evaluate(()=>localStorage.getItem('jwtToken'));
      return p.request.fetch(backend+path,{method,headers:{Authorization:'Bearer '+token},data});
    }
    async function published() { return (await (await api(a,`/api/v1/courses/${courseId}/content/published`)).json()).data; }
    await Promise.all([ready(a),ready(b)]);
    assert.equal(await published(),null);
    await a.getByPlaceholder(intro).fill('不完整草稿');
    assert.equal((await write(a,'/content/draft',()=>save(a).click())).status(),200);
    await b.getByPlaceholder(intro).fill('旧页面覆盖尝试');
    assert.equal((await write(b,'/content/draft',()=>save(b).click())).status(),409);
    await b.getByText(/检测到并发修改冲突/).first().waitFor(); assert.equal(await save(b).isDisabled(),true);
    console.log('PASS US02 two independent browser contexts: incomplete draft saved, stale write HTTP 409, no silent overwrite');
    await a.getByPlaceholder(assessment).fill('课程考核'); await a.getByPlaceholder(objectives).fill('课程目标');
    assert.equal((await write(a,'/content/publish',()=>a.getByRole('button',{name:'正式发布',exact:true}).click())).status(),200);
    await a.getByText('已发布 v1',{exact:false}).first().waitFor(); await ready(a);
    const v1=await published(); assert.equal(v1.publishVersion,1); assert.equal(v1.publisherCode,'BROWSER-T1'); assert.ok(v1.publishedAt);
    await a.getByPlaceholder(intro).fill('再次编辑的简介');
    assert.equal((await write(a,'/content/draft',()=>save(a).click())).status(),200);
    assert.equal((await published()).description,'不完整草稿');
    assert.equal((await write(a,'/content/publish',()=>a.getByRole('button',{name:'正式发布',exact:true}).click())).status(),200);
    const v2=await published(); assert.equal(v2.publishVersion,2); assert.equal(v2.description,'再次编辑的简介');
    console.log('PASS US02 publish -> edit draft -> reader retains v1 -> republish v2, publisher/time traceable');
    const d=await open('DIRECTOR');
    await d.getByRole('button',{name:/批量导入课程/}).click();
    const template=d.waitForResponse(r=>r.url().endsWith('/import/template'));
    await d.getByRole('button',{name:/下载.*模板/}).click();
    const templateResponse=await template;assert.equal(templateResponse.status(),200);assert.match(templateResponse.request().headers().authorization,/^Bearer /);
    const upload=d.locator('input[type=file][accept*="csv"]');
    await upload.setInputFiles({name:'review.csv',mimeType:'text/csv',buffer:Buffer.from('courseCode,courseName,department,majorCode,credits,hours,theoryHours,practiceHours,courseType,prerequisites,description\nBROWSER-IMPORT,浏览器导入课程,Browser Dept,SE,3,48,36,12,核心课,,\n')});
    const confirm=d.getByRole('button',{name:/确认导入并整批入库/});await confirm.waitFor();
    await d.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.innerText.includes('确认导入并整批入库')&&!b.disabled));
    await confirm.click();await d.getByText('浏览器导入课程',{exact:true}).first().waitFor();
    console.log('PASS US01 director login, JWT template download, CSV preview and confirm in full UI');
    await d.getByRole('button',{name:/开课排课统筹看板/}).click();
    const board=d.getByRole('region',{name:'开课与排课'});
    await board.getByRole('button',{name:'编辑班次'}).click();
    let dialog=d.getByRole('dialog',{name:'维护班次'});
    await dialog.getByText('选课名单 · 已选 95 人').waitFor();
    await dialog.getByRole('checkbox',{name:/协同教师/}).check();
    assert.equal((await write(d,'/courses/offerings/1',()=>dialog.getByRole('button',{name:'保存',exact:true}).click())).status(),200);
    await d.waitForFunction(()=>!document.querySelector('[role=dialog]'));
    await board.getByText('测试教师（主讲）、协同教师（协同）',{exact:true}).waitFor();
    console.log('PASS US03 edit team, retain 95 selected students and display both teachers');
    await board.getByRole('button',{name:'新增排课',exact:true}).click(); dialog=d.getByRole('dialog',{name:'维护排课'});
    await dialog.getByLabel('开课班次').selectOption({index:1}); await dialog.getByLabel('教室',{exact:true}).fill('文管 A447');
    await dialog.getByLabel('星期',{exact:true}).selectOption('3'); await dialog.getByLabel('起始节').fill('3'); await dialog.getByLabel('结束节').fill('4');
    assert.equal((await write(d,'/schedules',()=>dialog.getByRole('button',{name:'保存',exact:true}).click())).status(),200);
    await board.locator('article').filter({hasText:'文管 A447'}).waitFor();
    assert.match(await board.locator('article').first().innerText(),/95 人/);
    await d.screenshot({path:require('node:path').resolve(__dirname,'../../docs/us0203-schedule-sample.png'),fullPage:true});
    console.log('PASS US03 文管 A447 / 95 students / both teachers / week and period ranges displayed');
    await board.getByRole('button',{name:'新增排课',exact:true}).click(); dialog=d.getByRole('dialog',{name:'维护排课'});
    await dialog.getByLabel('开课班次').selectOption({index:1}); await dialog.getByLabel('教室',{exact:true}).fill('信息馆 B201');
    await dialog.getByLabel('星期',{exact:true}).selectOption('3'); await dialog.getByLabel('起始节').fill('3'); await dialog.getByLabel('结束节').fill('4');
    assert.equal((await write(d,'/schedules',()=>dialog.getByRole('button',{name:'保存',exact:true}).click())).status(),409);
    const conflict=dialog.locator('[aria-label="冲突详情"]'); await conflict.waitFor();
    assert.match(await conflict.innerText(),/教师冲突/); assert.match(await conflict.innerText(),/文管 A447/); assert.match(await conflict.innerText(),/协同教师/);
    await dialog.getByRole('button',{name:'取消'}).click();
    console.log('PASS US03 same offering across rooms blocked HTTP 409; UI displays reasons, course, teachers, room and ranges');
    await board.getByLabel('学期',{exact:true}).fill('2026秋季'); await board.getByLabel('教师',{exact:true}).selectOption('BROWSER-T2');
    await board.getByLabel('周次',{exact:true}).fill('16'); await board.getByLabel('教室',{exact:true}).fill('文管A447');
    await board.getByRole('button',{name:'筛选排课'}).click(); await board.locator('article').filter({hasText:'文管 A447'}).waitFor();
    await board.getByRole('button',{name:'编辑排课',exact:true}).click(); dialog=d.getByRole('dialog',{name:'维护排课'});
    await dialog.getByLabel('教室',{exact:true}).fill('文管 A448');
    assert.equal((await write(d,'/schedules',()=>dialog.getByRole('button',{name:'保存',exact:true}).click())).status(),200);
    await d.waitForFunction(()=>!document.querySelector('[role=dialog]')); await board.getByRole('button',{name:'重置',exact:true}).click();
    await board.locator('article').filter({hasText:'文管 A448'}).waitFor();
    console.log('PASS US03 combined semester/collaborator/week/room filter, edit excludes its own record');
    // Create a new offering using dictionary selections and the complete roster.
    await board.getByRole('button',{name:'新增班次',exact:true}).click(); dialog=d.getByRole('dialog',{name:'维护班次'});
    await dialog.getByLabel('课程',{exact:true}).selectOption({index:1}); await dialog.getByLabel('学期',{exact:true}).fill('2027春季');
    await dialog.getByLabel('教学班',{exact:true}).fill('新建教学班'); await dialog.getByLabel('主讲教师',{exact:true}).selectOption({index:2});
    await dialog.getByRole('button',{name:'全选筛选结果'}).click();
    assert.equal((await write(d,'/courses/offerings',()=>dialog.getByRole('button',{name:'保存',exact:true}).click())).status(),200);
    await board.getByText('新建教学班',{exact:true}).waitFor();
    console.log('PASS US03 new offering with dictionary teacher and selected 95-student roster');
    const supervisor=await open('SUPERVISOR');
    await supervisor.getByRole('button',{name:/待督导目标课程多维检索/}).click();
    const majorOptions=await supervisor.getByLabel('授权专业',{exact:true}).locator('option').allTextContents();
    assert.deepEqual(majorOptions,['全部授权专业','软件工程']);
    await supervisor.getByText('测试教师（授权 SE）',{exact:true}).waitFor();
    await supervisor.getByLabel('授权专业',{exact:true}).selectOption({label:'软件工程'});
    await supervisor.getByLabel('任课教师',{exact:true}).selectOption({label:'协同教师 · BROWSER-T2'});
    await supervisor.getByLabel('检索学期',{exact:true}).selectOption('2026秋季');
    await supervisor.getByPlaceholder('按课程名 / 代码检索...').fill('软件项目管理');
    await supervisor.locator('span').filter({hasText:/^样例教学班$/}).waitFor();
    await supervisor.getByPlaceholder('按课程名 / 代码检索...').fill('不存在的关键字');
    await supervisor.waitForFunction(()=>document.body.innerText.includes('共检索到 0'));
    await supervisor.getByRole('button',{name:'清空检索条件'}).click();
    await supervisor.waitForFunction(()=>document.body.innerText.includes('共检索到 2'));
    assert.equal((await api(supervisor,'/api/v1/courses/offerings?majorCode=AI')).status(),403);
    assert.equal((await api(supervisor,'/api/v1/courses/offerings?majorId=2')).status(),403);
    assert.equal((await api(supervisor,'/api/v1/courses/'+process.env.US0406_OUTSIDE_COURSE_ID)).status(),403);
    assert.equal((await api(supervisor,'/api/v1/courses/offerings/'+process.env.US0406_OUTSIDE_OFFERING_ID)).status(),403);
    const scopedHistory=(await (await api(supervisor,'/api/v1/courses/offerings/history')).json()).data;
    assert.equal(scopedHistory.totalOfferings,2);
    assert.ok(scopedHistory.items.every(item=>String(item.offeringId)!==process.env.US0406_OUTSIDE_OFFERING_ID));
    assert.equal((await supervisor.request.get(backend+'/api/v1/courses/offerings/history')).status(),401);
    console.log('PASS US06 supervisor real login, authorized major options, collaborator + term + keyword AND, clear/empty and forbidden query');
    await d.reload();await d.getByRole('button',{name:/开课排课统筹看板/}).click();
    const archiveRow=d.locator('tr').filter({hasText:'样例教学班'});
    await archiveRow.getByRole('button',{name:'结课归档'}).click();await archiveRow.getByText('已归档',{exact:true}).waitFor();
    assert.equal(await archiveRow.getByRole('button',{name:'编辑班次'}).count(),0);
    await a.reload();const hist=a.getByRole('region',{name:'历史开课与人次'});await hist.getByRole('cell').filter({hasText:'已归档'}).waitFor();
    assert.match(await hist.innerText(),/累计人次 95/);
    await hist.getByLabel('历史学期').fill('不存在');await hist.getByRole('button',{name:'查询历史'}).click();
    await hist.getByText('暂无历史开课记录',{exact:true}).waitFor();assert.match(await hist.innerText(),/累计人次 0/);
    await hist.getByRole('button',{name:'清空条件'}).click();await hist.getByRole('cell').filter({hasText:'已归档'}).waitFor();
    assert.equal((await api(d,'/api/v1/courses/offerings/1/students/add','POST',[])).status(),409);
    console.log('PASS US04 director archive, frozen UI and API, teacher semester history and empty table/zero');
    const timings=[];
    for(let i=0;i<20;i++){const begin=performance.now();const result=await api(supervisor,'/api/v1/courses/offerings?term=2026%E7%A7%8B%E5%AD%A3&teacherId=2&majorId=1&keyword=%E8%BD%AF%E4%BB%B6');assert.equal(result.status(),200);timings.push(performance.now()-begin)}
    const sorted=[...timings].sort((a,b)=>a-b);
    require('node:fs').writeFileSync(require('node:path').resolve(__dirname,'../../docs/us0406-query-performance.json'),JSON.stringify({measuredAt:new Date().toISOString(),scope:'local HTTP + JWT + Spring + isolated MySQL; 20 sequential requests; no concurrency/load claim',environment:{os:process.platform,node:process.version,mysql:'8.0.36',browser:browser.version()},data:{courses:3,offerings:3,teachers:2,students:95,majors:2},milliseconds:timings,median:sorted[10],p95:sorted[18],min:sorted[0],max:sorted[19]},null,2));
    await supervisor.screenshot({path:require('node:path').resolve(__dirname,'../../docs/us0406-supervisor.png'),fullPage:true});
    await a.screenshot({path:require('node:path').resolve(__dirname,'../../docs/us0406-history.png'),fullPage:true});
    console.log('SUMMARY: three real-login roles and all five stories passed; measured query timings saved.');
  } finally {
    for (const context of browser.contexts()) for (const page of context.pages()) await page.unrouteAll({behavior:'wait'});
    await browser.close();
  }
})().catch(e=>{console.error(e);process.exitCode=1;});
