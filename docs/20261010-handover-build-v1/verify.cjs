const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const work = __dirname;
const stage = path.join(work, 'staging');
const figures = path.join(stage, '20261010-handover-diagrams-v1');
const out = path.join(work, 'visual-qa');
fs.mkdirSync(out, { recursive: true });
let browser;
(async () => {
  browser = await chromium.launch({ executablePath: process.env.EXP3_CHROMIUM_PATH, headless: true, args: ['--disable-gpu'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  const errors = [], externalRequests = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.route(/^https?:/, route => { externalRequests.push(route.request().url()); return route.abort(); });
  const figureChecks = [];
  for (const name of fs.readdirSync(figures).filter(n => n.endsWith('.svg'))) {
    console.log('Checking ' + name);
    await page.setContent('<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0">' + fs.readFileSync(path.join(figures, name), 'utf8') + '</body></html>');
    await page.evaluate(() => document.fonts.ready);
    const dimensions = await page.evaluate(() => ({ width: Number(document.querySelector('svg').getAttribute('width')), height: Number(document.querySelector('svg').getAttribute('height')) }));
    await page.setViewportSize(dimensions);
    const check = await page.evaluate(() => {
      const root = document.querySelector('svg'), width = Number(root.getAttribute('width')), height = Number(root.getAttribute('height'));
      const overflow = [], nodeOverflow = [];
      for (const text of root.querySelectorAll('text')) {
        const r = text.getBBox();
        if (r.x < 0 || r.y < 0 || r.x + r.width > width || r.y + r.height > height) overflow.push(text.textContent);
      }
      for (const node of root.querySelectorAll('[data-node]')) {
        const x = Number(node.dataset.x), y = Number(node.dataset.y), w = Number(node.dataset.w), h = Number(node.dataset.h);
        for (const text of node.querySelectorAll('text')) {
          const r = text.getBBox();
          if (r.x < x + 8 || r.x + r.width > x + w - 8 || r.y < y + 5 || r.y + r.height > y + h - 5) nodeOverflow.push({ node: node.dataset.node, text: text.textContent, right: r.x + r.width, max: x + w - 8 });
        }
      }
      return { overflow, nodeOverflow };
    });
    await page.screenshot({ path: path.join(figures, name.replace('.svg', '.png')), animations: 'disabled', timeout: 15000 });
    figureChecks.push({ name, dimensions, ...check });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  const reportPath = path.join(stage, '20261010-developer-handover-v1.html');
  await page.goto(pathToFileURL(reportPath).href);
  await page.evaluate(() => document.fonts.ready);
  const desktop = await page.evaluate(() => {
    const ids = [...document.querySelectorAll('[id]')].map(x => x.id);
    const duplicateIds = ids.filter((id, i) => ids.indexOf(id) !== i);
    const brokenAnchors = [...document.querySelectorAll('a[href^="#"]')].map(x => x.getAttribute('href').slice(1)).filter(id => !document.getElementById(id));
    return { pageWidth: document.documentElement.scrollWidth, viewport: window.innerWidth, sections: document.querySelectorAll('main section').length, figures: document.querySelectorAll('figure svg').length, permissionRows: document.querySelectorAll('#permission-table tbody tr').length, duplicateIds, brokenAnchors };
  });
  await page.screenshot({ path: path.join(out, 'desktop-first-page.png') });
  await page.locator('#section-03').screenshot({ path: path.join(out, 'runtime-section.png') });
  await page.locator('#section-04').screenshot({ path: path.join(out, 'permissions-section.png') });
  await page.locator('label.filter').scrollIntoViewIfNeeded();
  await page.screenshot({ path: path.join(out, 'permissions-readable.png') });
  await page.locator('#permission-filter').fill('原文件');
  const filtering = await page.evaluate(() => ({ visible: [...document.querySelectorAll('#permission-table tbody tr')].filter(r => !r.hidden).length, summary: document.querySelector('#filter-result').textContent }));
  await page.locator('#permission-filter').fill('不应存在的搜索词');
  const emptyFiltering = await page.locator('#permission-table tbody tr:visible').count();
  await page.locator('#permission-filter').fill('');
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto(pathToFileURL(reportPath).href);
  await page.screenshot({ path: path.join(out, 'mobile-first-page.png') });
  const mobile = await page.evaluate(() => ({ pageWidth: document.documentElement.scrollWidth, viewport: window.innerWidth }));
  await page.setViewportSize({ width: 812, height: 375 });
  const landscape = await page.evaluate(() => ({ pageWidth: document.documentElement.scrollWidth, viewport: window.innerWidth }));
  const additionalWidths = [];
  for (const width of [768, 1024]) {
    await page.setViewportSize({ width, height: 900 });
    additionalWidths.push(await page.evaluate(() => ({ pageWidth: document.documentElement.scrollWidth, viewport: window.innerWidth })));
  }
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const reducedMotion = await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior);
  const result = { figureChecks, desktop, mobile, landscape, additionalWidths, filtering, emptyFiltering, reducedMotion, errors, externalRequests };
  fs.writeFileSync(path.join(work, 'visual-qa.json'), JSON.stringify(result, null, 2), 'utf8');
  await browser.close();
  const failed = figureChecks.some(c => c.overflow.length || c.nodeOverflow.length) || desktop.pageWidth > desktop.viewport || mobile.pageWidth > mobile.viewport || landscape.pageWidth > landscape.viewport || additionalWidths.some(c => c.pageWidth > c.viewport) || desktop.duplicateIds.length || desktop.brokenAnchors.length || desktop.figures !== 5 || desktop.permissionRows !== 38 || filtering.visible === 0 || emptyFiltering !== 0 || errors.length || externalRequests.length;
  console.log(JSON.stringify({ passed: !failed, diagrams: figureChecks.length, permissions: desktop.permissionRows, textOverflow: figureChecks.flatMap(c => c.nodeOverflow), pageWidths: { desktop, mobile, landscape }, errors }));
  if (failed) process.exitCode = 1;
})().catch(async error => { console.error(error); if (browser) await browser.close(); process.exitCode = 1; });
