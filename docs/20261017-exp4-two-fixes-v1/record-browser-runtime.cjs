const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const evidence = __dirname;
const root = path.resolve(evidence, '../..');
const sha256 = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.EXP3_CHROMIUM_PATH });
  try {
    const assets = path.join(root, 'frontend/dist/assets');
    const files = ['backend/target/classroom-backend-0.0.1-SNAPSHOT.jar', 'frontend/dist/index.html', ...fs.readdirSync(assets).map(name => 'frontend/dist/assets/' + name)];
    const result = {
      recordedAt: new Date().toISOString(),
      browserVersion: browser.version(),
      browserExecutable: process.env.EXP3_CHROMIUM_PATH,
      playwrightVersion: require(path.join(process.env.PLAYWRIGHT_MODULE, 'package.json')).version,
      headless: true,
      browserMode: 'Installed local Google Chrome, controlled by Playwright',
      os: process.platform,
      nodeVersion: process.version,
      runtimeImageId: execFileSync('docker', ['image', 'inspect', 'classroom-ai-demo-classroom-backend:latest', '--format', '{{.Id}}'], { encoding: 'utf8' }).trim(),
      artifactsSha256: Object.fromEntries(files.map(file => [file, sha256(path.join(root, file))])),
    };
    fs.writeFileSync(path.join(evidence, 'runtime-artifacts.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify({ browser: result.browserVersion, playwright: result.playwrightVersion, artifacts: files.length, headless: true }));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
