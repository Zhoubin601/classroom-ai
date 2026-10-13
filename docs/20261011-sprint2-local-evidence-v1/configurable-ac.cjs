const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const frontend = process.env.EXP3_FRONTEND_URL;
const backend = process.env.EXP3_BACKEND_URL;
const evidence = path.join(__dirname, 'configurable');
fs.mkdirSync(evidence, { recursive: true });
const results = [];
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.EXP3_CHROMIUM_PATH });
  try {
    async function login(username) {
      const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
      page.on('dialog', d => d.accept());
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('button', { name: '退出登录', exact: true }).waitFor();
      return page;
    }
    async function api(page, route, method='GET', options={}) {
      const token = await page.evaluate(() => localStorage.getItem('jwtToken'));
      return page.request.fetch(backend + route, { method, headers: { Authorization: `Bearer ${token}` }, ...options });
    }
    async function json(page, route, method='GET', data) {
      return (await api(page, route, method, data === undefined ? {} : { data })).json();
    }
    const teacher = await login('guojun');
    const supervisor = await login('supervisor');
    const director = await login('director');
    const teacherOfferings = (await json(teacher, '/api/v1/courses/offerings')).data;
    const common = (await json(supervisor, '/api/v1/courses/offerings')).data.find(o => teacherOfferings.some(t => t.id === o.id));
    assert.ok(common);
    const configured = { attitude: 10, content: 20, method: 30, effect: 40 };
    assert.deepEqual((await json(supervisor, '/api/v1/supervisions/weights')).data, configured);
    await supervisor.getByRole('button', { name: /待督导目标课程多维检索/ }).click();
    await supervisor.getByPlaceholder('按课程名 / 代码检索...').fill(common.course.courseCode);
    await supervisor.getByRole('combobox', { name: '检索学期' }).selectOption(common.academicTerm);
    await supervisor.getByTestId(`offering-${common.id}`).getByRole('button', { name: '随堂评价 (US-13)', exact: true }).click();
    const sliders = supervisor.locator('input[type="range"]');
    await sliders.nth(3).waitFor();
    assert.equal(await sliders.count(), 4);
    for (let i=0;i<4;i++) assert.equal(await sliders.nth(i).getAttribute('max'), String(Object.values(configured)[i]));
    await supervisor.screenshot({ path: path.join(evidence, 'custom-weights.png'), fullPage: true });
    const payload = { offeringId: common.id, listenTopic: '非默认配置验收',
      scoreAttitude: 10, scoreContent: 20, scoreMethod: 30, scoreEffect: 40,
      highlights: '组织有序\n案例清楚\n互动充分', suggestions: '增加练习', isDraft: false };
    const invalid = await json(supervisor, '/api/v1/supervisions', 'POST', { ...payload, scoreAttitude: 11 });
    assert.equal(invalid.code, 400);
    const draft = await json(supervisor, '/api/v1/supervisions', 'POST', { ...payload, isDraft: true });
    assert.equal(draft.data.status, 'DRAFT');
    const submitted = await json(supervisor, '/api/v1/supervisions', 'POST', { ...payload, id: draft.data.id });
    assert.equal(submitted.code, 200); assert.equal(submitted.data.totalScore, 100);
    assert.equal(submitted.data.status, 'PENDING_REVIEW');
    results.push({ check: 'US-13 nondefault weights', result: 'PASS', weights: configured, uiMaximaCorrect: true, overLimitRejected: true, total: 100 });
    const id = submitted.data.id;
    const rejected = await json(director, `/api/v1/supervisions/${id}/review`, 'POST', { approved: false, note: '完善教学建议' });
    assert.equal(rejected.data.status, 'REJECTED');
    assert.ok(!(await json(teacher, `/api/v1/supervisions?offeringId=${common.id}`)).data.some(e => e.id === id));
    const resubmitted = await json(supervisor, '/api/v1/supervisions', 'POST', { ...payload, id, suggestions: '增加后测与分组练习' });
    assert.equal(resubmitted.data.id, id); assert.equal(resubmitted.data.status, 'PENDING_REVIEW');
    const approved = await json(director, `/api/v1/supervisions/${id}/review`, 'POST', { approved: true, note: '配置专项通过' });
    assert.equal(approved.code, 200);
    const visible = (await json(teacher, `/api/v1/supervisions?offeringId=${common.id}`)).data.find(e => e.id === id);
    assert.ok(visible); assert.equal(visible.supervisorName, '匿名督导'); assert.equal(visible.supervisorUserId, null);
    const audit = await json(director, `/api/v1/supervisions/${id}/audit`);
    assert.ok(audit.data.length >= 5);
    results.push({ check: 'US-14 configurable zero-hour delay and reject/edit/resubmit audit', result: 'PASS', naturalPublication: true, anonymous: true, auditEntries: audit.data.length });
    async function upload(name, buffer) {
      return (await api(teacher, '/api/v1/resources/upload', 'POST', { multipart: {
        file: { name, mimeType: name.endsWith('.pdf') ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', buffer },
        courseId: String(common.course.id), chapter: '配置专项章节'
      } })).json();
    }
    const typeRejected = await upload('not-allowed.docx', Buffer.from('test'));
    assert.equal(typeRejected.code, 400);
    const sizeRejected = await upload('over-configured-limit.pdf', Buffer.alloc(1025));
    assert.equal(sizeRejected.code, 400);
    const accepted = await upload('untagged.pdf', Buffer.from('%PDF-1.4\nconfiguration upload fixture\n%%EOF'));
    assert.equal(accepted.code, 200); assert.deepEqual(accepted.data.tags, []);
    const untagged = await json(teacher, `/api/v1/resources?courseId=${common.course.id}&tag=${encodeURIComponent('未标注')}`);
    assert.ok(untagged.data.some(r => r.id === accepted.data.id));
    await teacher.reload();
    await teacher.waitForLoadState('networkidle');
    await teacher.locator('select:has(option[value="' + common.id + '"])').first().selectOption(String(common.id));
    await teacher.getByText('untagged.pdf', { exact: true }).first().waitFor();
    const resourceCard = teacher.locator('div').filter({has: teacher.getByText('untagged.pdf', {exact: true})}).filter({hasText: '未标注'});
    assert.ok(await resourceCard.count());
    await teacher.screenshot({ path: path.join(evidence, 'untagged-resource.png'), fullPage: true });
    results.push({ check: 'US-07/08 configured PDF-only 1024-byte limit and untagged search/display', result: 'PASS', extensionRejected: true, bytes1025Rejected: true, untaggedSearch: true, uiLabel: true });
    for (const result of results) console.log('PASS', result.check);
  } finally {
    fs.writeFileSync(path.join(evidence, 'results.json'), JSON.stringify(results, null, 2));
    await browser.close();
  }
})().catch(e => { console.error(e.message); process.exitCode = 1; });
