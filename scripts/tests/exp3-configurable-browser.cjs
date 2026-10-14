// Non-default upload policy, published decimal weights, audit trail and natural zero-delay feedback.
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const dir = path.resolve(process.env.EXP3_CONFIG_EVIDENCE_DIR || 'docs/runtime/sprint2-config');
const backend = process.env.EXP3_CONFIG_BACKEND_URL, container = process.env.EXP3_TEST_MYSQL_CONTAINER;
assert.equal(backend, 'http://127.0.0.1:18083');
const name = execFileSync('docker', ['inspect', '--format', '{{.Name}}', container], { encoding: 'utf8' }).trim();
assert.match(name, /^\/classroom-exp3-browser-[a-f0-9]{10}$/);
fs.mkdirSync(dir, { recursive: true });
function sql(text) { return execFileSync('docker', ['exec', '-i', '-e', 'MYSQL_PWD=root', container, 'mysql', '--default-character-set=utf8mb4', '-uroot', 'classroom_ai', '-N'], { input: text, encoding: 'utf8' }).trim(); }
(async () => {
  sql("INSERT INTO t_user_account(username,password,real_name,role,department) VALUES ('config_admin','123456','合成配置管理员','ADMIN','合成教研室');");
  const users = {};
  for (const role of ['director', 'supervisor', 'guojun', 'config_admin']) {
    const b = await (await fetch(backend + '/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: role, password: '123456' }) })).json();
    assert.equal(b.code, 200); users[role] = b.data;
  }
  async function api(role, url, method = 'GET', body) {
    const r = await fetch(backend + url, { method, headers: { Authorization: 'Bearer ' + users[role].token, ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }) }, ...(body ? { body: body instanceof FormData ? body : JSON.stringify(body) } : {}) });
    return r.json();
  }
  const offerings = (await api('guojun', '/api/v1/courses/offerings')).data;
  const authorized = (await api('supervisor', '/api/v1/courses/offerings')).data;
  const offering = offerings.find(o => authorized.some(a => a.id === o.id)); assert.ok(offering);
  async function upload(filename, bytes) {
    const form = new FormData(); form.append('file', new Blob([bytes]), filename);
    form.append('courseId', String(offering.course.id)); form.append('chapter', '合成配置专项'); form.append('resourceName', filename);
    return api('guojun', '/api/v1/resources/upload', 'POST', form);
  }
  assert.equal((await upload('blocked.docx', fs.readFileSync(path.join(__dirname, 'fixtures/sprint2.docx')))).code, 400);
  assert.equal((await upload('too-large.pdf', Buffer.alloc(1025, 65))).code, 400);
  const small = await upload('untagged.pdf', Buffer.from('%PDF-1.4\nSynthetic policy fixture\n%%EOF'));
  assert.equal(small.code, 200); assert.deepEqual(small.data.tags, []);
  const draft = await api('config_admin', '/api/v1/admin/templates', 'POST', { name: '合成小数权重模板', scenario: '配置专项', descriptions: '四个示例维度', attitude: 33.3, content: 33.3, method: 0.1, effect: 33.3 });
  assert.equal(draft.code, 200);
  const published = await api('config_admin', `/api/v1/admin/templates/${draft.data.id}/publish`, 'POST', { expectedVersion: draft.data.revision });
  assert.equal(published.code, 200);
  const input = { offeringId: offering.id, templateVersionId: published.data.id, listenTopic: '合成配置验收', scoreAttitude: 10, scoreContent: 20, scoreMethod: 0.1, scoreEffect: 30, highlights: '组织清晰\n案例充分\n互动及时', suggestions: '合成即时反馈建议' };
  const submitted = await api('supervisor', '/api/v1/supervisions', 'POST', input); assert.equal(submitted.code, 200); assert.equal(submitted.data.totalScore, 60.1);
  const rejected = await api('director', `/api/v1/supervisions/${submitted.data.id}/review`, 'POST', { approved: false, note: '合成补充意见' }); assert.equal(rejected.code, 200);
  const modified = await api('supervisor', '/api/v1/supervisions', 'POST', { ...input, id: submitted.data.id, expectedVersion: rejected.data.recordVersion, suggestions: '合成修订后的即时反馈建议' });
  assert.equal(modified.code, 200); assert.equal(modified.data.id, submitted.data.id);
  const approved = await api('director', `/api/v1/supervisions/${submitted.data.id}/review`, 'POST', { approved: true, note: '合成审核通过' }); assert.equal(approved.code, 200);
  const delay = new Date(approved.data.publishTime).getTime() - new Date(approved.data.reviewedAt).getTime();
  assert.ok(delay >= 0 && delay < 1000);
  const feedback = (await api('guojun', `/api/v1/supervisions?offeringId=${offering.id}`)).data.find(e => e.id === submitted.data.id);
  assert.ok(feedback); assert.equal(feedback.supervisorName, '匿名督导'); assert.equal(feedback.supervisorUserId, null); assert.equal(feedback.reviewedBy, null);
  assert.ok(Number(sql(`SELECT COUNT(*) FROM t_evaluation_audit_log WHERE evaluation_id=${submitted.data.id}`)) >= 4);
  const browser = await chromium.launch({ headless: true, executablePath: process.env.EXP3_CHROMIUM_PATH });
  const context = await browser.newContext(); await context.addInitScript(user => localStorage.setItem('jwtToken', user.token), users.guojun);
  const page = await context.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  // Forward real responses to the zero-delay/policy instance; do not mock business data.
  await page.route('**/api/**', async route => { const u = new URL(route.request().url()); const response = await route.fetch({ url: backend + u.pathname + u.search }); await route.fulfill({ response }); });
  try {
    await page.goto(process.env.EXP3_FRONTEND_URL); await page.locator('.account-button').waitFor();
    await page.getByRole('button', { name: '未标注', exact: true }).click();
    await page.getByTestId('my-resource-list').getByText('untagged.pdf', { exact: true }).waitFor();
    await page.getByRole('tab', { name: /^教学反馈/ }).click();
    await page.getByText('合成修订后的即时反馈建议', { exact: true }).first().waitFor();
    assert.equal(await page.getByText(users.supervisor.realName, { exact: true }).count(), 0);
    assert.deepEqual(errors, []); await page.screenshot({ path: path.join(dir, 'natural-feedback.png'), fullPage: true });
    const proof = ['PDF-only and 1024-byte upload policy enforced', 'Untagged resource filter and display', 'Published decimal weights determine server score', 'Rejection/modification/approval audit and natural immediate anonymous feedback UI'];
    fs.writeFileSync(path.join(dir, 'results.json'), JSON.stringify({ proof, pageErrors: errors }, null, 2)); console.log(JSON.stringify({ proof, pageErrors: errors }, null, 2));
  } finally { await page.unrouteAll({ behavior: 'ignoreErrors' }); await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
