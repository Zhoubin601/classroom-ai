const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MAJOR_CHROMIUM_PATH });
  const checks = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1100 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('dialog', d => d.accept());
    await page.goto('http://127.0.0.1:5173');
    await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('director_base');
    assert.ok(process.env.UI_TEST_PASSWORD, '请从进程环境提供测试账号密码');
    await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.UI_TEST_PASSWORD);
    await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
    await page.getByRole('heading', { name: '教研室主任工作台' }).waitFor();
    await page.getByRole('button', { name: '督导建档与专业授权 (US-06)', exact: true }).click();
    await page.getByText('计算机科学与技术 (CS)', { exact: true }).first().waitFor();
    assert.equal(await page.getByText('正在获取管辖专业...').count(), 0);
    await page.getByRole('button', { name: '调整专业授权', exact: true }).click();
    await page.getByRole('checkbox', { name: '计算机科学与技术 (CS)' }).waitFor();
    await page.screenshot({ path: path.join(__dirname, 'daily-zhao-scope.png'), fullPage: true, animations: 'disabled' });
    await page.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('本机运行页面赵主任范围与授权选项显示CS');
    await page.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)', exact: true }).click();
    await page.getByText(/当前大纲：|该课程尚未创建大纲/).first().waitFor();
    assert.ok(await page.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled());
    assert.equal(await page.getByRole('combobox', { name: '新大纲目录版本', exact: true }).inputValue(), 'RECOMMENDED-12');
    await page.screenshot({ path: path.join(__dirname, 'daily-zhao-indicator-entry.png'), fullPage: true, animations: 'disabled' });
    checks.push('本机运行页面清楚显示大纲/目录状态及创建绑定入口，未写入日常指标数据');
    fs.writeFileSync(path.join(__dirname, 'live-readonly-results.json'), JSON.stringify({ status: 'passed', checks }, null, 2));
    console.log(JSON.stringify({ status: 'passed', checks }));
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
