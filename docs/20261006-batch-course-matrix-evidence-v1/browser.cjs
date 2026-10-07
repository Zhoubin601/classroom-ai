const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const plan = require('./plan.json');
const before = require('./before.json');
const verified = require('./verification.json');
async function main() {
  assert.ok(process.env.MATRIX_LOGIN_PASSWORD);
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MAJOR_CHROMIUM_PATH });
  const checks = [];
  try {
    for (const director of before.directors) {
      const context = await browser.newContext({ viewport: { width: 1600, height: 1100 }, reducedMotion: 'reduce' });
      const page = await context.newPage();
      await page.goto('http://127.0.0.1:5173');
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(director.username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.MATRIX_LOGIN_PASSWORD);
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('heading', { name: '教研室主任工作台' }).waitFor();
      await page.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
      const courses = before.courses.filter(c => c.department === director.department);
      const picker = page.locator('select').filter({ has: page.locator('option').filter({ hasText: `${courses[0].courseCode} -` }) });
      await picker.waitFor();
      for (const course of courses) {
        const item = plan.find(p => p.courseId === course.id);
        const expected = verified.courses.find(c => c.courseCode === course.courseCode);
        const response = page.waitForResponse(r => r.url().endsWith(`/api/v1/syllabus/course/${course.id}/indicators`));
        await picker.selectOption(String(course.id));
        assert.equal((await response).status(), 200);
        await page.getByText('正在加载课程指标与目录...', { exact: true }).waitFor({ state: 'hidden' });
        const table = page.locator('table').filter({ has: page.getByRole('columnheader', { name: '毕业要求大项', exact: true }) });
        assert.equal(await table.locator('tbody tr').count(), expected.rows);
        if (item.dto) {
          for (const row of item.dto.indicators) {
            const rendered = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: row.indicatorCode, exact: true }) });
            assert.equal(await rendered.count(), 1);
            assert.ok((await rendered.textContent()).includes(row.indicatorDescription));
            assert.ok((await rendered.textContent()).includes(row.targetGoal));
          }
          assert.equal(await page.getByRole('button', { name: '新增认证指标点', exact: true }).isEnabled(), true);
        } else {
          assert.equal(await page.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled(), true);
        }
        if (['CS1002', 'CS3001', 'AI3001', 'DS2001'].includes(course.courseCode)) await page.screenshot({ path: path.join(__dirname, `${course.courseCode}-matrix.png`), fullPage: true, animations: 'disabled' });
        checks.push({ courseCode: course.courseCode, visibleRows: expected.rows, status: expected.status, director: director.username });
      }
      await context.close();
    }
    assert.equal(checks.length, 24);
    fs.writeFileSync(path.join(__dirname, 'browser-results.json'), JSON.stringify({ status: 'passed', note: '七个主任账号正常登录，24门实际页面逐门检查，无接口mock，无业务保存', checks }, null, 2));
    console.log(JSON.stringify({ status: 'passed', checkedCourses: checks.length }));
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
