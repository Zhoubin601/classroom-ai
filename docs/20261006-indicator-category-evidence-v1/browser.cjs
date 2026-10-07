const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const checks = [];
async function login(page, account) {
  await page.goto('http://127.0.0.1:5173');
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(account);
  assert.ok(process.env.UI_TEST_PASSWORD, '请在进程环境中提供测试登录密码');
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.UI_TEST_PASSWORD);
  await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
}
async function mockDraft(page) {
  await page.route('**/api/v1/syllabus/course/*/latest', route => {
    assert.equal(route.request().method(), 'GET');
    return route.fulfill({ json: { code: 200, data: { id: -999, version: 'UI-readonly', planVersion: 'RECOMMENDED-12', status: 'DRAFT' } } });
  });
  await page.route('**/api/v1/syllabus/course/*/indicators', route => {
    assert.equal(route.request().method(), 'GET');
    return route.fulfill({ json: { code: 200, data: [] } });
  });
}
async function checkCategories(page, teacher) {
  const add = page.getByRole('button', { name: '新增认证指标点', exact: true });
  await add.waitFor();
  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('新增认证指标点') && !b.disabled));
  await add.click();
  const category = page.getByRole('combobox', { name: teacher ? '毕业要求大项（培养方案目录）' : '毕业要求大项', exact: true });
  const code = page.getByRole('combobox', { name: '指标点编号', exact: true });
  assert.equal(await category.isEnabled(), true);
  await category.selectOption({ label: '问题分析' });
  assert.equal(await code.inputValue(), '2-1');
  await code.selectOption('11-1');
  assert.equal(await category.inputValue(), '项目管理');
  const description = page.getByPlaceholder('请输入该指标点在课程中的分解细化要求与能力观测点...');
  await description.fill('浏览器检查：保留已填写的课程分解描述');
  await category.selectOption({ label: '工程知识' });
  assert.equal(await code.inputValue(), '1-1');
  assert.equal(await description.inputValue(), '浏览器检查：保留已填写的课程分解描述');
  await page.screenshot({ path: path.join(__dirname, teacher ? 'teacher-category.png' : 'director-category.png'), fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: '取消', exact: true }).click();
  checks.push(`${teacher ? '教师' : '主任'}大项可选、编号双向同步、保留手写描述；取消未保存`);
}
async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MAJOR_CHROMIUM_PATH });
  try {
    const directorContext = await browser.newContext({ viewport: { width: 1600, height: 1100 }, reducedMotion: 'reduce' });
    const page = await directorContext.newPage();
    await mockDraft(page);
    await login(page, 'director_base');
    await page.getByRole('heading', { name: '教研室主任工作台' }).waitFor();
    await page.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
    await checkCategories(page, false);
    await page.getByRole('button', { name: '开课排课统筹看板 (US-03)', exact: true }).click();
    const form = page.getByRole('form', { name: '排课筛选', exact: true });
    await form.waitFor();
    await page.waitForFunction(() => document.querySelector('#schedule-filter-teacher')?.options.length > 1);
    const layouts = [];
    for (const width of [1600, 1024, 768, 390]) {
      await page.setViewportSize({ width, height: 1100 });
      await form.scrollIntoViewIfNeeded();
      const layout = await form.evaluate(el => {
        const box = el.getBoundingClientRect();
        const fields = [...el.querySelectorAll('input,select,button')].map(f => {
          const r = f.getBoundingClientRect();
          return { label: f.getAttribute('aria-label') || f.textContent.trim(), x: r.x - box.x, y: r.y - box.y, width: r.width, height: r.height };
        });
        return { width: box.width, padding: getComputedStyle(el).paddingLeft, overflow: el.scrollWidth > el.clientWidth, fields };
      });
      assert.equal(layout.padding, '16px');
      assert.equal(layout.overflow, false);
      for (const field of layout.fields) {
        assert.equal(field.height, 40);
        assert.ok(field.x >= 16 && field.x + field.width <= layout.width - 15);
      }
      for (let i = 0; i < layout.fields.length; i++) for (let j = i + 1; j < layout.fields.length; j++) {
        const a = layout.fields[i], b = layout.fields[j];
        assert.ok(a.x + a.width <= b.x + 0.5 || b.x + b.width <= a.x + 0.5 || a.y + a.height <= b.y + 0.5 || b.y + b.height <= a.y + 0.5);
      }
      if (width === 1600) assert.ok(layout.fields[2].width >= 128);
      await form.screenshot({ path: path.join(__dirname, `schedule-filter-${width}.png`), animations: 'disabled' });
      layouts.push({ viewport: width, ...layout });
    }
    await page.setViewportSize({ width: 1600, height: 1100 });
    const teacherCode = await form.getByRole('combobox', { name: '教师', exact: true }).locator('option').nth(1).getAttribute('value');
    await form.getByLabel('学期', { exact: true }).fill('2026-2027-1');
    await form.getByRole('combobox', { name: '教师', exact: true }).selectOption(teacherCode);
    await form.getByRole('spinbutton', { name: '周次', exact: true }).fill('3');
    await form.getByRole('textbox', { name: '教室', exact: true }).fill('文管 A447');
    const filtered = page.waitForResponse(r => r.url().includes('/api/v1/schedules?') && new URL(r.url()).searchParams.get('week') === '3');
    await form.getByRole('button', { name: '筛选排课', exact: true }).click();
    const response = await filtered;
    assert.equal(response.status(), 200);
    const params = new URL(response.url()).searchParams;
    assert.equal(params.get('teacher'), teacherCode);
    assert.equal(params.get('term'), '2026-2027-1');
    assert.equal(params.get('classroom'), '文管 A447');
    const reset = page.waitForResponse(r => r.url().includes('/api/v1/schedules') && !new URL(r.url()).searchParams.get('week'));
    await form.getByRole('button', { name: '重置', exact: true }).click();
    assert.equal((await reset).status(), 200);
    for (const label of ['学期', '教师', '周次', '教室']) assert.equal(await form.getByLabel(label, { exact: true }).inputValue(), '');
    checks.push('排课筛选在1600/1024/768/390px无重叠或溢出，输入和按钮高度40px；真实GET筛选与重置通过');
    const teacherContext = await browser.newContext({ viewport: { width: 1600, height: 1100 }, reducedMotion: 'reduce' });
    const teacher = await teacherContext.newPage();
    await mockDraft(teacher);
    await login(teacher, 'zhaogs');
    await teacher.getByRole('heading', { name: /老师工作台/ }).waitFor();
    await checkCategories(teacher, true);
    fs.writeFileSync(path.join(__dirname, 'browser-results.json'), JSON.stringify({ status: 'passed', note: '仅模拟GET大纲和课程指标响应为未锁定草稿，目录与排课筛选请求使用真实服务；未保存业务数据', checks, layouts }, null, 2));
    console.log(JSON.stringify({ status: 'passed', checks }));
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
