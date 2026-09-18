// npm install --no-save playwright, or point PLAYWRIGHT_MODULE at an existing installation.
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    const failures = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('response', response => {
      if (response.url().includes('/api/') && response.status() >= 400) failures.push(`${response.status()} ${response.url()}`);
    });
    await page.goto('http://127.0.0.1:5173', { waitUntil: 'networkidle' });
    for (const label of ['教研室主任工作台', '任课教师工作台', '教学督导工作台', '课堂智能考勤大屏', '学生人脸档案库']) {
      await page.getByRole('button', { name: label }).click();
      await page.waitForTimeout(1500);
      assert.ok((await page.locator('main').innerText()).trim().length > 30, `${label} is empty`);
      console.log(`PASS tab: ${label}`);
    }
    const wrongMethod = await page.request.get('http://127.0.0.1:5173/api/face/register-webcam');
    assert.equal(wrongMethod.status(), 405);
    const invalid = await page.request.post('http://127.0.0.1:5173/api/face/register-webcam', { data: { studentId: '../escape', name: 'Test' } });
    assert.equal(invalid.status(), 400);
    assert.deepEqual(errors, [], 'Browser runtime errors');
    assert.deepEqual(failures, [], 'API HTTP errors while navigating');
    console.log('PASS browser smoke: 5 tabs, no runtime/API errors, invalid camera requests rejected');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
