const fs = require('node:fs');
const path = require('node:path');
const modulePath = 'C:/Users/a3185/AppData/Local/uv/cache/archive-v0/GcBvtI027rUzG_nt/Lib/site-packages/playwright/driver/package';
const { chromium } = require(modulePath);
(async () => {
  const executablePath = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
  const browser = await chromium.launch({ headless: true, executablePath });
  try {
    const result = { observedAt: new Date().toISOString(), chromeVersion: browser.version(), playwrightVersion: require(path.join(modulePath, 'package.json')).version, executablePath, headless: true };
    fs.writeFileSync(path.join(__dirname, 'browser-version.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
