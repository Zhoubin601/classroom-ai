// Real Chrome regression for server-configured limits and evaluation terminology.
// Fault injection cases are explicitly separated from real-backend acceptance.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { selectFeature } = require('./browser-helpers.cjs');

const evidence = path.resolve(process.env.RESOURCE_POLICY_EVIDENCE_DIR || 'docs/runtime/resource-upload-policy');
const runtime = process.env.RESOURCE_POLICY_RUNTIME;
const password = process.env.RESOURCE_POLICY_TEST_PASSWORD;
const highFrontend = process.env.RESOURCE_POLICY_HIGH_FRONTEND || 'http://127.0.0.1:15180';
const lowFrontend = process.env.RESOURCE_POLICY_LOW_FRONTEND || 'http://127.0.0.1:15181';
const highBackend = process.env.RESOURCE_POLICY_HIGH_BACKEND || 'http://127.0.0.1:18090';
const lowBackend = process.env.RESOURCE_POLICY_LOW_BACKEND || 'http://127.0.0.1:18091';
assert.ok(runtime && password, 'Set dedicated runtime directory and disposable test password via environment');
const proof = { startedAt: new Date().toISOString(), mockedBusinessResponses: false, passed: 0, failed: 0, proof: [], pageErrors: [] };
const fixtureProof = { mocked: true, classification: 'Fault injection interaction cases; no substitute for real-backend business acceptance', status: 'NOT_RUN', passed: 0, failed: 0, proof: [], pageErrors: [] };
let runningFaults = false;
fs.mkdirSync(evidence, { recursive: true });
const cases = proof.proof;
const contexts = [];
function save() {
  proof.passed = cases.filter(item => item.status === 'PASS').length;
  proof.failed = proof.failure ? 1 : 0;
  fixtureProof.passed = fixtureProof.proof.filter(item => item.status === 'PASS').length;
  fixtureProof.failed = fixtureProof.failure ? 1 : 0;
  fs.writeFileSync(path.join(evidence, 'results.json'), JSON.stringify(proof, null, 2));
  fs.writeFileSync(path.join(evidence, 'fixture-results.json'), JSON.stringify(fixtureProof, null, 2));
}
function passed(name, details, fault = false) {
  (fault ? fixtureProof.proof : cases).push({ name, ...details, status: 'PASS' });
  save(); console.log('PASS ' + name);
}

// Exact-size, well-formed single-page PDF. Size padding is an unreferenced stream;
// xref and EOF remain at the end, so padding does not invalidate PDF parsing.
function pdfFile(filename, targetBytes) {
  const contents = 'BT /F1 14 Tf 30 100 Td (Upload policy acceptance) Tj ET\n';
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${Buffer.byteLength(contents)} >>\nstream\n${contents}endstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  let body = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((object, i) => { offsets.push(Buffer.byteLength(body)); body += `${i + 1} 0 obj\n${object}\nendobj\n`; });
  offsets.push(Buffer.byteLength(body));
  let padding = targetBytes - Buffer.byteLength(body) - 290;
  assert.ok(padding >= 0, 'PDF target size too small');
  for (let attempt = 0; attempt < 10; attempt++) {
    const prefix = `6 0 obj\n<< /Length ${padding} >>\nstream\n`;
    const ending = '\nendstream\nendobj\n';
    const xref = Buffer.byteLength(body + prefix + ending) + padding;
    let trailer = 'xref\n0 7\n0000000000 65535 f \n';
    offsets.slice(1).forEach(offset => { trailer += `${String(offset).padStart(10, '0')} 00000 n \n`; });
    trailer += `trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
    const tail = ending + trailer;
    const actual = Buffer.byteLength(body + prefix + tail) + padding;
    if (actual !== targetBytes) { padding += targetBytes - actual; continue; }
    assert.ok(padding >= 0);
    const destination = path.join(runtime, filename), fd = fs.openSync(destination, 'wx');
    try {
      fs.writeSync(fd, body + prefix);
      const block = Buffer.alloc(1024 * 1024, 32);
      for (let remaining = padding; remaining > 0;) { const count = Math.min(remaining, block.length); fs.writeSync(fd, block, 0, count); remaining -= count; }
      fs.writeSync(fd, tail);
    } finally { fs.closeSync(fd); }
    assert.equal(fs.statSync(destination).size, targetBytes);
    return destination;
  }
  throw new Error('PDF size calculation did not converge');
}

function tinyDocx() {
  function crc32(bytes) { let crc = 0xffffffff; for (const byte of bytes) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); } return (crc ^ 0xffffffff) >>> 0; }
  const entries = [
    ['[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'],
    ['_rels/.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'],
    ['word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Policy</w:t></w:r></w:p></w:body></w:document>'],
  ];
  const locals = [], central = []; let offset = 0;
  for (const [filename, text] of entries) {
    const name = Buffer.from(filename), bytes = Buffer.from(text), compressed = zlib.deflateRawSync(bytes), crc = crc32(bytes);
    const header = Buffer.alloc(30); header.writeUInt32LE(0x04034b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(8, 8); header.writeUInt32LE(crc, 14); header.writeUInt32LE(compressed.length, 18); header.writeUInt32LE(bytes.length, 22); header.writeUInt16LE(name.length, 26);
    locals.push(header, name, compressed);
    const index = Buffer.alloc(46); index.writeUInt32LE(0x02014b50, 0); index.writeUInt16LE(20, 4); index.writeUInt16LE(20, 6); index.writeUInt16LE(8, 10); index.writeUInt32LE(crc, 16); index.writeUInt32LE(compressed.length, 20); index.writeUInt32LE(bytes.length, 24); index.writeUInt16LE(name.length, 28); index.writeUInt32LE(offset, 42);
    central.push(index, name); offset += header.length + name.length + compressed.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10); end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  const bytes = Buffer.concat([...locals, directory, end]); assert.ok(bytes.length < 1024, 'DOCX must be below size limit to isolate type validation');
  const destination = path.join(runtime, 'blocked-type.docx'); fs.writeFileSync(destination, bytes, { flag: 'wx' }); return destination;
}

async function login(browser, frontend, backend, username = 'guojun', intercept) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } }); contexts.push(context);
  if (intercept) await intercept(context);
  const page = await context.newPage(); page.setDefaultTimeout(30000);
  page.on('pageerror', error => (intercept ? fixtureProof.pageErrors : proof.pageErrors).push({ username, frontend, message: error.message }));
  let uploads = 0; page.on('request', request => { if (new URL(request.url()).pathname === '/api/v1/resources/upload') uploads++; });
  const policies = []; page.on('response', async response => { if (new URL(response.url()).pathname === '/api/v1/resources/upload-policy' && response.status() === 200) { try { policies.push((await response.json()).data); } catch {} } });
  await page.goto(frontend);
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(password);
  await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
  await page.locator('.account-button').waitFor();
  const token = await page.evaluate(() => localStorage.getItem('jwtToken')); assert.ok(token);
  async function api(url, method = 'GET', data) {
    const response = await fetch(backend + url, { method, headers: { Authorization: `Bearer ${token}`, ...(data instanceof FormData ? {} : { 'Content-Type': 'application/json' }) }, ...(data ? { body: data instanceof FormData ? data : JSON.stringify(data) } : {}) });
    return { httpStatus: response.status, ...(await response.json()) };
  }
  const result = { context, page, backend, frontend, username, api, policies, uploadCount: () => uploads };
  return result;
}
async function chooseOwnedOffering(session) {
  const result = await session.api('/api/v1/courses/offerings'); assert.equal(result.code, 200); assert.ok(result.data.length);
  const offering = result.data[0], select = session.page.locator(`select:has(option[value="${offering.id}"])`).first();
  if (await select.count()) await select.selectOption(String(offering.id));
  return offering;
}
async function openMain(session) {
  await selectFeature(session.page, '课程资料');
  await session.page.getByRole('button', { name: /上传新课件\/教案/ }).click();
  const modal = session.page.getByRole('dialog').filter({ has: session.page.getByRole('button', { name: '立即挂载', exact: true }) });
  await modal.waitFor(); return { root: modal, input: modal.locator('input[type="file"]'), upload: modal.getByRole('button', { name: '立即挂载', exact: true }) };
}
async function openVersions(session, resourceId) {
  await navigate(session.page, '统计与反馈');
  await selectFeature(session.page, '资源版本');
  const root = session.page.locator('.pro-card').filter({ has: session.page.getByRole('heading', { name: '资源历史版本', exact: true }) });
  const select = root.getByRole('combobox'); await select.selectOption(String(resourceId));
  const input = root.getByLabel('上传此系列的新版本'); await input.waitFor();
  const upload = root.getByRole('button', { name: '上传新版并设为当前', exact: true });
  return { root, input, upload };
}
async function navigate(page, name) {
  const button = page.getByRole('button', { name, exact: true });
  await button.click();
  await assertEventually(async () => {
    const discard = page.getByRole('button', { name: '放弃修改', exact: true });
    if (await discard.isVisible()) await discard.click();
    assert.equal(await button.getAttribute('aria-current'), 'page');
  });
}
async function checkPolicy(session, form, expectedBytes, expectedExtensions) {
  await form.input.waitFor({ state: 'attached' });
  await assertEventually(async () => assert.equal(await form.input.isDisabled(), false));
  const actual = await session.api('/api/v1/resources/upload-policy');
  assert.equal(actual.httpStatus, 200); assert.equal(actual.code, 200); assert.equal(actual.data.maxFileBytes, expectedBytes);
  assert.deepEqual([...actual.data.allowedExtensions].sort(), [...expectedExtensions].sort());
  const accept = (await form.input.getAttribute('accept')).split(',').map(value => value.trim()).sort();
  assert.deepEqual(accept, expectedExtensions.map(extension => '.' + extension).sort());
  const text = await form.root.innerText();
  assert.match(text, expectedBytes === 1024 ? /(?:1024\s*(?:B|字节)|1\s*(?:KB|KiB))/i : /150\s*(?:MB|MiB)/i);
  assert.doesNotMatch(text, /100\s*(?:MB|MiB)/i);
  return { maxFileBytes: actual.data.maxFileBytes, allowedExtensions: actual.data.allowedExtensions, accept: accept.join(',') };
}
async function assertEventually(action, timeout = 30000) {
  const deadline = Date.now() + timeout; let error;
  do { try { await action(); return; } catch (failure) { error = failure; await new Promise(resolve => setTimeout(resolve, 50)); } } while (Date.now() < deadline);
  throw error;
}
async function submitFile(session, form, file, name, main = true) {
  await form.input.setInputFiles(file);
  if (main) {
    await form.root.getByPlaceholder('如 第一章 软件项目管理概论').fill('可配置策略真实浏览器回归');
    await form.root.getByPlaceholder('如 第1讲-需求估算与甘特图.pptx').fill(name);
  }
  assert.equal(await form.upload.isDisabled(), false, 'Valid file must enable upload');
  const [response] = await Promise.all([
    session.page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/resources/upload' && response.request().method() === 'POST', { timeout: 120000 }),
    form.upload.click(),
  ]);
  const result = await response.json(); assert.equal(response.status(), 200); assert.equal(result.code, 200, result.message);
  assert.equal(result.data.fileSizeBytes, fs.statSync(file).size);
  if (main) await form.root.waitFor({ state: 'hidden' });
  else await form.root.getByText('新版本已保存，历史文件保留', { exact: true }).waitFor();
  return { resourceId: result.data.id, seriesId: result.data.seriesId, fileSizeBytes: result.data.fileSizeBytes, courseId: result.data.course.id, version: result.data.version, httpStatus: response.status(), code: result.code };
}
async function renderPdf(session, resource, main, screenshot) {
  if (main) {
    const card = session.page.getByTestId('my-resource-list').locator(':scope > div').filter({ hasText: resource.name });
    await card.getByRole('button', { name: '授权预览', exact: true }).click();
  } else {
    const row = session.page.getByRole('row').filter({ hasText: String(resource.resourceId) }).filter({ hasText: /当前/ });
    await row.getByRole('button', { name: '授权水印预览', exact: true }).click();
  }
  await session.page.getByTestId('protected-pdf-preview').waitFor();
  await session.page.waitForFunction(() => {
    const canvas = document.querySelector('[data-testid="pdf-page-canvas"]');
    if (!canvas || canvas.width < 10 || canvas.height < 10) return false;
    const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data; let dark = 0;
    for (let i = 0; i < pixels.length; i += 4) if (pixels[i + 3] && pixels[i] < 230 && pixels[i + 1] < 230 && pixels[i + 2] < 230 && ++dark > 200) return true;
    return false;
  }, undefined, { timeout: 120000 });
  await session.page.screenshot({ path: path.join(evidence, screenshot), fullPage: true });
  const counter = await session.page.getByTestId('pdf-page-counter').innerText();
  await session.page.getByRole('button', { name: main ? '关闭' : '关闭预览', exact: true }).click();
  return { browserCanvasRendered: true, counter };
}
async function rejectFile(session, form, fixture, type, screenshot, main) {
  const before = session.uploadCount(); await form.input.setInputFiles(fixture);
  const matcher = type === 'size' ? /(?:不能超过|超过|超出).*(?:1024|1\s*(?:KB|KiB))|(?:1024|1\s*(?:KB|KiB)).*(?:超过|超出)/i : /(?:不支持|不允许|仅支持|格式)/;
  const alert = main ? session.page.locator('.ui-toast[role="status"]') : form.root.getByRole('alert');
  await assertEventually(async () => assert.match(await alert.last().innerText(), matcher));
  assert.equal(session.uploadCount(), before);
  assert.equal(await form.upload.isDisabled(), true, 'Rejected file must not remain submittable');
  assert.equal(await form.input.evaluate(element => element.files?.length || 0), 0, 'Rejected native selection must be cleared');
  await session.page.screenshot({ path: path.join(evidence, screenshot), fullPage: true });
  return { filename: path.basename(fixture), fileSizeBytes: fs.statSync(fixture).size, notification: await alert.last().innerText(), uploadRequestsAdded: session.uploadCount() - before, uploadDisabled: true };
}

(async () => {
  const fixtures = { large: pdfFile('allowed-101MiB.pdf', 101 * 1024 * 1024), small: pdfFile('allowed-900B.pdf', 900), exact: pdfFile('allowed-1024B.pdf', 1024), over: pdfFile('blocked-1025B.pdf', 1025), docx: tinyDocx() };
  proof.fixtures = Object.fromEntries(Object.entries(fixtures).map(([name, file]) => [name, { filename: path.basename(file), bytes: fs.statSync(file).size }]));
  const browser = await chromium.launch({ headless: process.env.HEADLESS !== 'false', executablePath: process.env.EXP3_CHROMIUM_PATH });
  proof.browser = { name: 'Google Chrome', version: browser.version(), executablePath: process.env.EXP3_CHROMIUM_PATH, headless: process.env.HEADLESS !== 'false' };
  try {
    const high = await login(browser, highFrontend, highBackend), owned = await chooseOwnedOffering(high);
    const highMain = await openMain(high), extensions = ['ppt', 'pptx', 'doc', 'docx', 'pdf'];
    const highPolicy = await checkPolicy(high, highMain, 150 * 1024 * 1024, extensions);
    const largeName = 'Browser policy 101MiB main.pdf';
    const largeMain = await submitFile(high, highMain, fixtures.large, largeName); assert.equal(largeMain.courseId, owned.course.id);
    const largePreview = await renderPdf(high, { ...largeMain, name: largeName }, true, '01-high-policy-main-101MiB-rendered.png');
    passed('150MiB policy: main form really uploads and renders 101MiB PDF', { policy: highPolicy, ...largeMain, ...largePreview });
    const highVersions = await openVersions(high, largeMain.resourceId);
    const highVersionPolicy = await checkPolicy(high, highVersions, 150 * 1024 * 1024, extensions);
    const largeVersion = await submitFile(high, highVersions, fixtures.large, largeName, false);
    assert.equal(largeVersion.seriesId, largeMain.seriesId); assert.notEqual(largeVersion.resourceId, largeMain.resourceId);
    const history = await high.api(`/api/v1/resources/${largeMain.resourceId}/history`); assert.equal(history.code, 200);
    assert.equal(history.data.series.currentVersionId, largeVersion.resourceId);
    assert.ok(history.data.versions.some(version => version.id === largeMain.resourceId));
    const versionPreview = await renderPdf(high, largeVersion, false, '02-high-policy-version-101MiB-rendered.png');
    passed('150MiB policy: same-series new version uploads and renders 101MiB PDF', { policy: highVersionPolicy, ...largeVersion, previousResourceId: largeMain.resourceId, ...versionPreview });

    const low = await login(browser, lowFrontend, lowBackend); await chooseOwnedOffering(low);
    let lowResource;
    for (const kind of ['small', 'exact']) {
      const main = await openMain(low), policy = await checkPolicy(low, main, 1024, ['pdf']);
      const name = 'Browser policy ' + kind + '.pdf', uploaded = await submitFile(low, main, fixtures[kind], name);
      const preview = await renderPdf(low, { ...uploaded, name }, true, `03-low-policy-main-${kind}-rendered.png`);
      lowResource = uploaded;
      passed(`PDF-only 1024B policy: main form uploads ${kind} PDF`, { policy, ...uploaded, ...preview });
    }
    let lowMain = await openMain(low); await checkPolicy(low, lowMain, 1024, ['pdf']);
    for (const [kind, file] of [['size', fixtures.over], ['type', fixtures.docx]]) {
      const rejection = await rejectFile(low, lowMain, file, kind, `04-low-policy-main-reject-${kind}.png`, true);
      passed(`PDF-only 1024B policy: main form rejects ${kind} without upload`, rejection);
    }
    await lowMain.root.getByRole('button', { name: '取消', exact: true }).click();
    const lowVersions = await openVersions(low, lowResource.resourceId); await checkPolicy(low, lowVersions, 1024, ['pdf']);
    for (const kind of ['small', 'exact']) {
      const uploaded = await submitFile(low, lowVersions, fixtures[kind], '', false);
      assert.equal(uploaded.seriesId, lowResource.seriesId);
      passed(`PDF-only 1024B policy: version form uploads ${kind} PDF`, uploaded);
    }
    for (const [kind, file] of [['size', fixtures.over], ['type', fixtures.docx]]) {
      const rejection = await rejectFile(low, lowVersions, file, kind, `05-low-policy-version-reject-${kind}.png`, false);
      passed(`PDF-only 1024B policy: version form rejects ${kind} without upload`, rejection);
    }

    for (const username of ['guojun', 'liubo']) {
      const teacher = await login(browser, highFrontend, highBackend, username);
      await selectFeature(teacher.page, '教学反馈');
      await teacher.page.getByText('教学评价四维', { exact: true }).waitFor();
      await teacher.page.getByText('教学改进建议', { exact: true }).waitFor();
      assert.equal(await teacher.page.getByText('BOPPPS 教学模型', { exact: true }).count(), 0);
      assert.equal(await teacher.page.getByText('BOPPPS 进阶教改建议', { exact: true }).count(), 0);
      for (const axis of ['教学态度', '教学内容', '教学方法', '教学效果']) await teacher.page.getByText(`${axis} (归一化25分刻度)`, { exact: true }).waitFor();
      await teacher.page.screenshot({ path: path.join(evidence, `06-teacher-${username}-four-dimensions.png`), fullPage: true });
      passed(`Teacher ${username}: four teaching dimensions and improvement labels are accurate`, { oldMisleadingLabels: 0, dimensions: ['教学态度', '教学内容', '教学方法', '教学效果'] });
    }

    const supervisor = await login(browser, highFrontend, highBackend, 'supervisor');
    await selectFeature(supervisor.page, '课程检索');
    const authorizedOfferings = await supervisor.api('/api/v1/courses/offerings');
    assert.equal(authorizedOfferings.code, 200);
    assert.ok(authorizedOfferings.data.length, 'Supervisor requires an authorized synthetic offering');
    const evaluatedOffering = authorizedOfferings.data[0];
    await supervisor.page.getByRole('combobox', { name: '检索学期' }).selectOption(evaluatedOffering.academicTerm);
    await supervisor.page.getByTestId('offering-' + evaluatedOffering.id).getByRole('button', { name: /随堂评价/ }).click();
    await supervisor.page.getByText('教学评价四维打分项（配置权重合计 100 分）', { exact: true }).waitFor();
    assert.equal(await supervisor.page.getByText(/BOPPPS.*四维/).count(), 0);
    await supervisor.page.getByPlaceholder('如 第三讲：需求估算与WBS分解').fill('四维术语修正浏览器回归');
    const scoreValues = [21, 22, 23, 24];
    for (const [i, axis] of ['教学态度', '教学内容', '教学方法', '教学效果'].entries()) await supervisor.page.getByRole('slider', { name: axis + '评分' }).fill(String(scoreValues[i]));
    await supervisor.page.getByPlaceholder('例如：教学组织严密，能够结合实际敏捷项目案例启发学生...').fill('合成教学组织清晰\n合成案例贴合目标\n合成互动及时');
    await supervisor.page.getByPlaceholder('例如：建议在课后作业中进一步增加甘特图与工期缓冲池实训演练...').fill('合成建议：增加参与式学习中的反馈');
    await supervisor.page.screenshot({ path: path.join(evidence, '07-supervisor-teaching-four-dimension-form.png'), fullPage: true });
    const [draftResponse] = await Promise.all([supervisor.page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/supervisions' && response.request().method() === 'POST'), supervisor.page.getByRole('button', { name: '暂存草稿', exact: true }).click()]);
    const draft = await draftResponse.json(); assert.equal(draft.code, 200); assert.equal(draft.data.status, 'DRAFT');
    assert.deepEqual([draft.data.scoreAttitude, draft.data.scoreContent, draft.data.scoreMethod, draft.data.scoreEffect], scoreValues); assert.equal(draft.data.totalScore, 90);
    passed('Supervisor terminology correction preserves real four-dimension score submission', { evaluationId: draft.data.id, scores: scoreValues, totalScore: draft.data.totalScore, evaluationStatus: draft.data.status });
    await navigate(supervisor.page, '统计与反馈'); await selectFeature(supervisor.page, '督导建议');
    const parentAdviceLoaded = supervisor.page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/supervisions/${draft.data.id}/suggestions` && response.request().method() === 'GET');
    await supervisor.page.getByRole('combobox', { name: '本人可编辑评价' }).selectOption(String(draft.data.id));
    await parentAdviceLoaded;
    await assertEventually(async () => assert.equal(await supervisor.page.getByRole('button', { name: '新增独立建议', exact: true }).isEnabled(), true));
    await supervisor.page.getByLabel('建议文本', { exact: true }).fill('合成六环节可选标签保持不变');
    const stageLabels = ['导入（B）', '目标（O）', '前测（P1）', '参与式学习（P2）', '后测（P3）', '总结（S）'];
    for (const label of stageLabels) assert.equal(await supervisor.page.getByRole('checkbox', { name: label, exact: true }).isChecked(), false);
    for (const label of [stageLabels[0], stageLabels[3], stageLabels[5]]) await supervisor.page.getByRole('checkbox', { name: label, exact: true }).check();
    const [adviceResponse] = await Promise.all([supervisor.page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/supervisions/${draft.data.id}/suggestions` && response.request().method() === 'POST'), supervisor.page.getByRole('button', { name: '新增独立建议', exact: true }).click()]);
    const advice = await adviceResponse.json(); assert.equal(advice.code, 200); const savedStageTags = JSON.parse(advice.data.stageTagsJson); assert.deepEqual(savedStageTags, ['B', 'P2', 'S']);
    await supervisor.page.screenshot({ path: path.join(evidence, '08-supervisor-six-optional-stage-tags.png'), fullPage: true });
    const untagged = await supervisor.api(`/api/v1/supervisions/${draft.data.id}/suggestions`, 'POST', { text: '合成不选教学环节仍可保存', evidenceRefs: [], stageTags: [] });
    assert.equal(untagged.code, 200); assert.deepEqual(JSON.parse(untagged.data.stageTagsJson), []);
    const allEvaluations = await supervisor.api('/api/v1/supervisions'); const unchanged = allEvaluations.data.find(evaluation => evaluation.id === draft.data.id);
    assert.deepEqual([unchanged.scoreAttitude, unchanged.scoreContent, unchanged.scoreMethod, unchanged.scoreEffect], scoreValues);
    const persistedAdvice = await supervisor.api(`/api/v1/supervisions/${draft.data.id}/suggestions`);
    assert.deepEqual(persistedAdvice.data.find(item => item.id === advice.data.id).stageTags, ['B', 'P2', 'S']);
    passed('Six optional BOPPPS stage tags remain independent of four score dimensions', { evaluationId: draft.data.id, savedTags: savedStageTags, emptyTagsAccepted: true, scoresUnchanged: scoreValues });

    proof.businessAcceptanceStatus = 'PASS'; proof.realBackendCompletedAt = new Date().toISOString(); save();
    if (process.env.RESOURCE_POLICY_FAULT_INJECTION !== 'false') {
      runningFaults = true; fixtureProof.status = 'RUNNING'; fixtureProof.startedAt = new Date().toISOString();
      let failed = false;
      const retry = await login(browser, highFrontend, highBackend, 'guojun', async context => {
        await context.route('**/api/v1/resources/upload-policy', async route => {
          if (!failed) { failed = true; await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '合成策略暂不可用' }) }); }
          else await route.continue();
        });
      });
      const main = await openMain(retry);
      await retry.page.getByRole('button', { name: /重新加载上传规则|重试.*(?:上传|策略)|(?:上传|策略).*重试/ }).first().waitFor();
      assert.equal(await main.input.isDisabled(), true); assert.equal(await main.upload.isDisabled(), true);
      assert.doesNotMatch(await main.root.innerText(), /100\s*(?:MB|MiB)/i);
      await retry.page.screenshot({ path: path.join(evidence, '09-fault-injection-policy-failure-disabled.png'), fullPage: true });
      await retry.page.getByRole('button', { name: /重新加载上传规则|重试.*(?:上传|策略)|(?:上传|策略).*重试/ }).first().click();
      const policy = await checkPolicy(retry, main, 150 * 1024 * 1024, extensions);
      passed('Fault injection only: failed policy blocks upload and explicit retry loads real policy', { policy, syntheticFault: 'first upload-policy response HTTP 503; retry response from real backend', silent100MiBFallback: false }, true);

      let requests = 0;
      const versionRetry = await login(browser, highFrontend, highBackend, 'guojun', async context => {
        await context.route('**/api/v1/resources/upload-policy', async route => {
          requests++;
          if (requests === 2) await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '合成版本上传策略暂不可用' }) });
          else await route.continue();
        });
      });
      const versionForm = await openVersions(versionRetry, largeVersion.resourceId);
      await versionForm.root.getByRole('button', { name: '重新加载上传规则', exact: true }).waitFor();
      assert.equal(await versionForm.input.isDisabled(), true); assert.equal(await versionForm.upload.isDisabled(), true);
      assert.doesNotMatch(await versionForm.root.innerText(), /100\s*(?:MB|MiB)/i);
      await versionRetry.page.screenshot({ path: path.join(evidence, '10-fault-injection-version-policy-failure-disabled.png'), fullPage: true });
      await versionForm.root.getByRole('button', { name: '重新加载上传规则', exact: true }).click();
      const versionPolicy = await checkPolicy(versionRetry, versionForm, 150 * 1024 * 1024, extensions);
      passed('Fault injection only: version policy failure blocks upload and retry recovers', { policy: versionPolicy, syntheticFault: 'second upload-policy response HTTP 503 while mounting versions panel; retry uses real backend', silent100MiBFallback: false }, true);

      let release, settled;
      const held = new Promise(resolve => { release = resolve; }), finished = new Promise(resolve => { settled = resolve; });
      let delayedOnce = false;
      const late = await login(browser, highFrontend, highBackend, 'guojun', async context => {
        await context.route('**/api/v1/resources/upload-policy', async route => {
          if (delayedOnce) { await route.continue(); return; }
          delayedOnce = true;
          try { const response = await route.fetch(); await held; await route.fulfill({ response }).catch(() => {}); }
          catch {} finally { settled(); }
        });
      });
      const lateMain = await openMain(late); assert.equal(await lateMain.input.isDisabled(), true);
      await lateMain.root.getByRole('button', { name: '取消', exact: true }).click();
      await lateMain.root.waitFor({ state: 'hidden' });
      await navigate(late.page, '统计与反馈'); release(); await finished;
      await late.page.getByRole('heading', { name: '本人评价汇总', exact: true }).waitFor();
      assert.equal(await late.page.getByRole('dialog').count(), 0);
      await navigate(late.page, '任课教师工作台');
      const returnedMain = await openMain(late); await checkPolicy(late, returnedMain, 150 * 1024 * 1024, extensions);
      await late.page.screenshot({ path: path.join(evidence, '11-fault-injection-late-policy-remount-ready.png'), fullPage: true });
      passed('Fault injection only: late policy response after unmount does not restore abandoned UI', { syntheticFault: 'real policy response held until TeacherDesk unmounted', remountedPolicyReady: true, abandonedModalRestored: false }, true);
      assert.deepEqual(fixtureProof.pageErrors, []); fixtureProof.status = 'PASS'; fixtureProof.completedAt = new Date().toISOString();
    }
    assert.deepEqual(proof.pageErrors, []);
    proof.probeExecutionStatus = 'PASS'; proof.businessAcceptanceStatus = 'PASS'; proof.completedAt = new Date().toISOString(); save();
    console.log(JSON.stringify({ businessAcceptanceStatus: proof.businessAcceptanceStatus, passed: proof.passed, failed: proof.failed, faultInjectionCases: fixtureProof.proof.length, browser: proof.browser }));
  } catch (error) {
    proof.probeExecutionStatus = 'FAIL';
    if (runningFaults) { fixtureProof.status = 'FAIL'; fixtureProof.failure = { name: error.name, message: error.message }; fixtureProof.completedAt = new Date().toISOString(); }
    else { proof.businessAcceptanceStatus = 'NOT_ALL_PASSED'; proof.failure = { name: error.name, message: error.message }; }
    proof.completedAt = new Date().toISOString(); save();
    for (const [i, context] of contexts.entries()) for (const [j, page] of context.pages().entries()) if (!page.isClosed()) await page.screenshot({ path: path.join(evidence, `failure-${i}-${j}.png`), fullPage: true }).catch(() => {});
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
