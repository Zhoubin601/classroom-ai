// Exercise authenticated startup probes both before and after interactive authentication.
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const evidence = path.resolve(process.env.LOGIN_START_EVIDENCE || 'docs/runtime/login-start');
fs.mkdirSync(evidence, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.EXP3_CHROMIUM_PATH });
  const proof = [], errors = [];
  try {
    for (const kind of ['success-before', 'success-after', 'http401-before', 'business401-before', 'failed-login']) {
      const context = await browser.newContext(), page = await context.newPage();
      await context.addInitScript(() => localStorage.setItem('jwtToken', 'synthetic-old-token'));
      const user = { id: 902, role: 'TEACHER', realName: '合成新教师', department: '合成教研室', teacherCode: 'T902', token: 'synthetic-new-token' };
      let releaseProbe, releaseAuth, probeUsedOldToken = false;
      page.on('pageerror', e => errors.push(e.message));
      await context.route('**/api/**', async route => {
        const req = route.request(), url = new URL(req.url());
        if (!url.pathname.startsWith('/api/')) return route.continue();
        let data = [], code = 200, status = 200;
        if (url.pathname.endsWith('/auth/csrf')) data = { csrfToken: 'synthetic-fixture' };
        else if (/\/auth\/(login|register-supervisor)$/.test(url.pathname)) {
          await new Promise(r => releaseAuth = r);
          if (kind === 'failed-login') { code = 400; status = 400; data = null; } else data = user;
        } else if (url.pathname.endsWith('/auth/me')) {
          probeUsedOldToken = req.headers().authorization === 'Bearer synthetic-old-token';
          await new Promise(r => releaseProbe = r);
          if (kind.includes('401')) { code = 401; status = kind.startsWith('http') ? 401 : 200; data = null; }
          else data = { id: 901, role: 'ADMIN', realName: '合成旧管理员' };
        }
        // Aborting the startup request is part of the expected fix.
        await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code, message: code === 400 ? '合成登录失败' : 'success', data }) }).catch(() => {});
      });
      async function gate(get) { for (let i = 0; i < 200; i++) { if (get()) return; await page.waitForTimeout(20); } assert.fail('认证时序夹具未到达'); }
      try {
        await page.goto(process.env.UI_URL || 'http://127.0.0.1:15181');
        await gate(() => releaseProbe);
        await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('synthetic-new');
        await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('synthetic-password');
        await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
        await gate(() => releaseAuth);
        if (kind === 'success-after') { releaseAuth(); releaseAuth = null; await page.locator('.account-button').filter({ hasText: user.realName }).waitFor(); releaseProbe(); releaseProbe = null; }
        else { releaseProbe(); releaseProbe = null; await page.waitForTimeout(150); releaseAuth(); releaseAuth = null; }
        if (kind === 'failed-login') {
          await page.getByText('合成登录失败', { exact: true }).waitFor();
          assert.equal(await page.locator('.app-sidebar').count(), 0);
          assert.equal(await page.evaluate(() => localStorage.getItem('jwtToken')), 'synthetic-old-token');
        } else {
          await page.locator('.account-button').filter({ hasText: user.realName }).waitFor();
          await page.waitForTimeout(150);
          assert.equal(await page.getByRole('button', { name: '模板管理', exact: true }).count(), 0);
          assert.equal(await page.evaluate(() => localStorage.getItem('jwtToken')), user.token);
          assert.equal(await page.getByRole('button', { name: '任课教师工作台', exact: true }).count(), 1);
        }
        assert.ok(probeUsedOldToken);
        await page.screenshot({ path: path.join(evidence, kind + '.png'), fullPage: true });
        proof.push({ kind, result: 'PASS', probeUsedOldToken });
      } finally { releaseProbe?.(); releaseAuth?.(); await page.unrouteAll({ behavior: 'ignoreErrors' }); await context.close(); }
    }
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(evidence, 'results.json'), JSON.stringify({ proof, pageErrors: errors, syntheticOnly: true }, null, 2));
    console.log(JSON.stringify({ proof, pageErrors: errors }, null, 2));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
