const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const backend = process.env.MAJOR_TEST_BACKEND || 'http://127.0.0.1:18086';
const frontend = process.env.MAJOR_TEST_FRONTEND || 'http://127.0.0.1:15186';
const checks = [];
const output = __dirname;

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MAJOR_CHROMIUM_PATH });
  try {
    async function login(username) {
      const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } });
      const page = await context.newPage();
      page.on('pageerror', error => console.error('PAGE ERROR', error.message));
      page.on('console', message => { if (message.type() === 'error') console.error('CONSOLE ERROR', message.text()); });
      page.on('requestfailed', request => console.error('REQUEST FAILED', request.url(), request.failure()?.errorText));
      page.on('dialog', d => d.accept());
      await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
        const u = new URL(route.request().url());
        const response = await route.fetch({ url: backend + u.pathname + u.search });
        await route.fulfill({ response });
      });
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      assert.ok(process.env.UI_TEST_PASSWORD, '请从进程环境提供隔离测试账号密码');
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.UI_TEST_PASSWORD);
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('heading', { name: username.startsWith('director') ? '教研室主任工作台' : /老师工作台/ }).waitFor();
      return page;
    }
    async function api(page, url, method = 'GET', data) {
      const token = await page.evaluate(() => localStorage.getItem('jwtToken'));
      return page.request.fetch(backend + url, { method, data, headers: { Authorization: `Bearer ${token}` } });
    }
    const base = await login('director_base');
    const managed = await (await api(base, '/api/v1/director/managed-majors')).json();
    assert.deepEqual(managed.data.map(m => m.majorCode), ['CS']);
    const sample = await (await api(base, '/api/v1/courses/import/template')).text();
    assert.ok(sample.includes('基础软件教研室,CS'));
    assert.ok(!sample.includes('软件工程教研室'));
    checks.push('赵主任范围为CS，CSV示例使用基础软件教研室');

    await base.getByRole('button', { name: '督导建档与专业授权 (US-06)', exact: true }).click();
    await base.getByText('计算机科学与技术 (CS)', { exact: true }).first().waitFor();
    assert.equal(await base.getByText('正在获取管辖专业...').count(), 0);
    await base.getByRole('button', { name: '调整专业授权', exact: true }).click();
    assert.equal(await base.getByRole('checkbox', { name: '计算机科学与技术 (CS)' }).count(), 1);
    await base.screenshot({ path: path.join(output, 'zhao-supervisor-scope.png'), fullPage: true, animations: 'disabled' });
    await base.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('赵主任督导授权弹窗显示CS复选项');

    await base.getByRole('button', { name: '新增专业课程档案', exact: true }).click();
    assert.equal(await base.getByRole('textbox', { name: '课程所属教研室' }).inputValue(), '基础软件教研室');
    await base.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('赵主任新课程默认教研室正确');

    const fixture = await api(base, '/api/v1/courses', 'POST', {
      courseCode: 'MD-UI-CS-' + Date.now(), courseName: '关联规则浏览器合成测试', department: '基础软件教研室',
      majorCode: 'CS', credits: 3, hours: 48, theoryHours: 36, practiceHours: 12,
      courseType: '专业核心课', prerequisites: ''
    });
    assert.equal(fixture.status(), 200);
    const fixtureCourse = (await fixture.json()).data;
    await base.reload();
    await base.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
    await base.locator('select').filter({ has: base.locator(`option[value="${fixtureCourse.id}"]`) }).first().selectOption(String(fixtureCourse.id));
    await base.getByText('该课程尚未创建大纲，请先选择目录并创建大纲，再新增指标映射。', { exact: true }).waitFor();
    assert.ok(await base.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled());
    await base.getByRole('textbox', { name: '新大纲版本', exact: true }).fill('UI-v1');
    await base.getByRole('button', { name: '创建大纲并绑定目录', exact: true }).click();
    await base.getByText('当前大纲：UI-v1', { exact: false }).waitFor();
    await base.getByRole('button', { name: '新增认证指标点', exact: true }).click();
    assert.equal(await base.getByRole('combobox', { name: '指标点编号', exact: true }).locator('option').count(), 13);
    assert.equal(await base.getByRole('combobox', { name: '毕业要求大项', exact: true }).locator('option').count(), 12);
    await base.getByRole('combobox', { name: '指标点编号', exact: true }).selectOption('2-1');
    assert.equal(await base.getByRole('combobox', { name: '毕业要求大项', exact: true }).inputValue(), '问题分析');
    await base.screenshot({ path: path.join(output, 'zhao-indicator-options.png'), fullPage: true, animations: 'disabled' });
    await base.getByPlaceholder('请输入该指标点在课程中的分解细化要求与能力观测点...').fill('合成课程分解内容');
    await base.getByRole('button', { name: '保存并写入 MySQL', exact: true }).click();
    await base.getByRole('cell', { name: '合成课程分解内容', exact: true }).waitFor();
    checks.push('无大纲有明确创建入口，推荐示例编号/类别可选且映射保存成功');

    const lead = await login('director_arch');
    const importItems = [{ indicatorCode: '13-1', requirementCategory: '合成扩展类别', indicatorDescription: '合成扩展指标' }];
    assert.equal((await api(lead, '/api/v1/syllabus/plans/CS/UI-13/indicators', 'PUT', importItems)).status(), 200);
    assert.equal((await api(base, '/api/v1/syllabus/plans/CS/UI-13/indicators', 'PUT', importItems)).status(), 403);
    assert.equal((await api(base, '/api/v1/courses/' + (await (await api(lead, '/api/v1/courses')).json()).data[0].id)).status(), 403);
    checks.push('牵头主任可导入，参与主任不能改公共目录或其他室课程');
    await base.reload();
    await base.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
    await base.locator('select').filter({ has: base.locator(`option[value="${fixtureCourse.id}"]`) }).first().selectOption(String(fixtureCourse.id));
    await base.getByRole('combobox', { name: '新大纲目录版本' }).selectOption('UI-13');
    await base.getByRole('textbox', { name: '新大纲版本', exact: true }).fill('UI-v2');
    await base.getByRole('button', { name: '创建大纲并绑定目录', exact: true }).click();
    await base.getByText('当前大纲：UI-v2', { exact: false }).waitFor();
    await base.getByRole('button', { name: '新增认证指标点', exact: true }).click();
    await base.getByRole('combobox', { name: '指标点编号', exact: true }).selectOption('13-1');
    assert.equal(await base.getByRole('combobox', { name: '毕业要求大项', exact: true }).inputValue(), '合成扩展类别');
    await base.getByRole('button', { name: '取消', exact: true }).click();
    const history = (await (await api(base, `/api/v1/syllabus/course/${fixtureCourse.id}`)).json()).data;
    assert.equal(history.length, 2);
    checks.push('跨教研室读取已导入目录，13类扩展不受12类模板限制，旧大纲版本保留');

    await base.route('**/api/v1/director/managed-majors', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":200,"data":[]}' }));
    await base.reload();
    await base.getByRole('button', { name: '督导建档与专业授权 (US-06)', exact: true }).click();
    await base.getByText('当前教研室尚未配置专业关联', { exact: true }).waitFor();
    assert.equal(await base.getByText('正在获取管辖专业...').count(), 0);
    checks.push('空范围显示未配置，加载文案正常结束');
    await base.unroute('**/api/v1/director/managed-majors');
    await base.route('**/api/v1/syllabus/plans/CS/*/indicators', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":503,"message":"目录暂时不可用"}' }));
    await base.reload();
    await base.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
    await base.getByText('目录暂时不可用', { exact: false }).waitFor();
    assert.ok(await base.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled());
    await base.unroute('**/api/v1/syllabus/plans/CS/*/indicators');
    await base.getByRole('button', { name: '重试加载', exact: true }).click();
    await base.getByText('目录暂时不可用', { exact: false }).waitFor({ state: 'hidden' });
    checks.push('目录请求失败可见错误、阻止空弹窗并支持重试恢复');

    const teacher = await login('zhaogs');
    assert.equal((await api(teacher, '/api/v1/syllabus/plans/CS/RECOMMENDED-12/indicators')).status(), 200);
    await teacher.getByRole('textbox', { name: '新大纲版本', exact: true }).fill('UI-teacher-' + Date.now());
    await teacher.getByRole('button', { name: '套用推荐示例模板（12类）', exact: true }).click();
    await teacher.getByRole('button', { name: '新增认证指标点', exact: true }).waitFor();
    await teacher.getByRole('button', { name: '新增认证指标点', exact: true }).click();
    assert.equal(await teacher.locator('.fixed select').first().locator('option').count(), 13);
    assert.equal(await teacher.locator('.fixed select').nth(2).locator('option').count(), 12);
    await teacher.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('参与教研室任课教师可读取CS目录，示例模板及指标点选项正常');
    fs.writeFileSync(path.join(output, 'browser-results.json'), JSON.stringify({ status: 'passed', checks }, null, 2));
    console.log(JSON.stringify({ status: 'passed', checks }, null, 2));
  } catch (e) {
    fs.writeFileSync(path.join(output, 'browser-results.json'), JSON.stringify({ status: 'failed', checks, error: e.message }, null, 2));
    throw e;
  } finally { await browser.close(); }
}
main().catch(e => { console.error(e); process.exitCode = 1; });
