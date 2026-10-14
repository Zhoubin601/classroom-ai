const assert = require('node:assert/strict');

// Follow the current UI's navigation/unsaved-input confirmation and await completion.
async function selectFeature(page, name) {
  const tab = page.getByRole('tab', { name });
  await tab.click();
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    const discard = page.getByRole('button', { name: '放弃修改', exact: true });
    if (await discard.isVisible()) await discard.click();
    if (await tab.getAttribute('aria-selected') === 'true') return;
    await page.waitForTimeout(50);
  }
  assert.fail(`功能切换未完成：${name}`);
}

module.exports = { selectFeature };
