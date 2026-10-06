// Real UI-to-backend regression for primary-teacher offering visibility.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');

const baseUrl = process.env.LIUBO_HISTORY_BASE_URL || 'http://127.0.0.1:5173';
const password = process.env.LIUBO_TEST_PASSWORD;
(async () => {
  if (!password) throw new Error('Set LIUBO_TEST_PASSWORD to the demo account password before running.');
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHROMIUM_PATH ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_PATH } : {})
  });
  try {
    const page = await browser.newPage();
    const pageErrors = [];
    let historyResponse;
    let historyBody;
    page.on('pageerror', error => pageErrors.push(error.message));
    page.on('response', async response => {
      if (new URL(response.url()).pathname === '/api/v1/courses/offerings/history') {
        historyResponse = response;
        try { historyBody = await response.json(); } catch {}
      }
    });

    await page.goto(baseUrl);
    await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('liubo');
    await page.getByPlaceholder('请输入登录密码 (默认 123456)')
      .fill(password);
    await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();

    const history = page.getByRole('region', { name: '历史开课与人次' });
    await history.getByText('敏捷开发与人机协同', { exact: false }).waitFor();
    await history.getByText('DevOps与持续交付', { exact: false }).waitFor();
    assert.ok(historyResponse, 'history API was called');
    assert.equal(historyResponse.status(), 200);
    assert.ok(historyBody.data.totalOfferings >= 2);
    assert.ok(historyBody.data.items.some(item => item.courseCode === 'SE3002'));
    assert.ok(historyBody.data.items.some(item => item.courseCode === 'SE3003'));
    assert.deepEqual(pageErrors, []);
    console.log(`PASS Playwright real login: history shows ${historyBody.data.totalOfferings} offerings including SE3002 and SE3003`);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
