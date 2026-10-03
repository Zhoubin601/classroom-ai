const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const frontend = process.env.EXP3_FRONTEND_URL || 'http://127.0.0.1:5173';
const backend = process.env.EXP3_BACKEND_URL || 'http://127.0.0.1:18081';
const expiredBackend = process.env.EXP3_EXPIRED_BACKEND_URL || 'http://127.0.0.1:18082';
const evidenceDir = path.resolve(__dirname, '../../docs/sprint2-chrome-evidence');

function pdfBuffer() {
  const stream = 'BT /F1 14 Tf 40 100 Td (Sprint 2 preview) Tj ET\n';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'
  ];
  let body = '%PDF-1.4\n';
  const offsets = [0];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  }
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) body += `${String(offset).padStart(10, '0')} 00000 n \n`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body);
}

async function main() {
  fs.mkdirSync(evidenceDir, { recursive: true });
  const browser = await chromium.launch({ headless: true,
    ...(process.env.EXP3_CHROMIUM_PATH ? { executablePath: process.env.EXP3_CHROMIUM_PATH } : {}) });
  const pages = [];
  try {
    async function login(username, title) {
      const context = await browser.newContext();
      const page = await context.newPage();
      page.on('dialog', dialog => dialog.accept());
      page.on('pageerror', error => console.error('PAGE ERROR', username, error.message));
      await page.route('**/api/v1/**', async route => {
        try {
          const url = new URL(route.request().url());
          const response = await route.fetch({ url: backend + url.pathname + url.search });
          await route.fulfill({ response });
        } catch (error) {
          if (!page.isClosed()) console.error('ROUTE ERROR', username, error.message);
        }
      });
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('123456');
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('heading', { name: title }).waitFor();
      pages.push(page);
      return page;
    }
    async function api(page, path, method = 'GET', options = {}) {
      const token = await page.evaluate(() => localStorage.getItem('jwtToken'));
      return page.request.fetch(backend + path, {
        method, headers: { Authorization: `Bearer ${token}`, ...(options.headers || {}) }, ...options
      });
    }
    const teacher = await login('guojun', /老师工作台/);
    const supervisor = await login('supervisor', '教学督导工作台');
    const director = await login('director', /教研室主任/);
    const otherTeacher = await login('liubo', /老师工作台/);
    const foreignTeacher = await login('jiangly', /老师工作台/);
    const planVersion = 'EXP3-E2E-' + Date.now();
    await director.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)' }).click();
    await director.getByRole('combobox', { name: '培养方案专业' }).selectOption('SE');
    await director.getByRole('textbox', { name: '培养方案版本' }).fill(planVersion);
    await director.getByRole('textbox', { name: '培养方案指标目录' }).fill(
      'E2E-1 | 工程知识 | 隔离测试合成指标一\nE2E-2 | 工程实践 | 隔离测试合成指标二');
    const [planImport] = await Promise.all([
      director.waitForResponse(response => response.url().includes(`/plans/SE/${planVersion}/indicators`) && response.request().method() === 'PUT'),
      director.getByRole('button', { name: '导入目录' }).click()
    ]);
    assert.equal((await planImport.json()).data.length, 2);
    await director.screenshot({ path: path.join(evidenceDir, '00-director-plan-import.png'), fullPage: true });
    await director.getByRole('textbox', { name: '培养方案指标目录' }).fill('E2E-1 | 工程知识 | 隔离测试修订指标');
    const [planReplace] = await Promise.all([
      director.waitForResponse(response => response.url().includes(`/plans/SE/${planVersion}/indicators`) && response.request().method() === 'PUT'),
      director.getByRole('button', { name: '导入目录' }).click()
    ]);
    assert.deepEqual((await planReplace.json()).data.map(item => item.indicatorCode), ['E2E-1']);
    const duplicatePlan = await api(director, `/api/v1/syllabus/plans/SE/${planVersion}/indicators`, 'PUT', { data: [
      { indicatorCode: 'E2E-1', requirementCategory: '工程知识', indicatorDescription: '重复一' },
      { indicatorCode: 'E2E-1', requirementCategory: '工程知识', indicatorDescription: '重复二' }
    ]});
    assert.equal((await duplicatePlan.json()).code, 400);
    await teacher.getByRole('textbox', { name: '培养方案版本' }).fill(planVersion);
    await teacher.getByRole('textbox', { name: '新大纲版本' }).fill(planVersion);
    const [syllabusCreated] = await Promise.all([
      teacher.waitForResponse(response => response.url().includes('/from-plan') && response.request().method() === 'POST'),
      teacher.getByRole('button', { name: '从培养方案创建大纲' }).click()
    ]);
    assert.equal((await syllabusCreated.json()).code, 200);
    const syllabus = (await syllabusCreated.json()).data;
    const mappings = (await (await api(teacher, `/api/v1/syllabus/course/${syllabus.course.id}/indicators`)).json()).data;
    assert.deepEqual(mappings.map(item => item.indicatorCode), ['E2E-1']);
    await teacher.screenshot({ path: path.join(evidenceDir, '00-teacher-syllabus-mapping.png'), fullPage: true });
    console.log('PASS US-05 Chrome: synthetic plan import, same-version replacement, duplicate rejection, course mapping');
    const teacherOfferings = (await (await api(teacher, '/api/v1/courses/offerings')).json()).data;
    const supervisorOfferings = (await (await api(supervisor, '/api/v1/courses/offerings')).json()).data;
    const shared = teacherOfferings.find(off => supervisorOfferings.some(other => other.id === off.id));
    assert.ok(shared, 'teacher/supervisor must share one authorized offering');
    if (await teacher.locator('select:has(option[value="' + shared.id + '"])').count()) {
      await teacher.locator('select:has(option[value="' + shared.id + '"])').first().selectOption(String(shared.id));
    }
    const name = 'EXP3-browser-' + Date.now() + '.pdf';
    await teacher.getByRole('button', { name: /上传新课件\/教案/ }).click();
    await teacher.locator('input[type="file"]').last().setInputFiles({ name, mimeType: 'application/pdf', buffer: pdfBuffer() });
    await teacher.getByPlaceholder('如 第一章 软件项目管理概论').fill('实验三测试章节');
    await teacher.locator('label:has(input[type="checkbox"][value="理论"]) input').check();
    await teacher.locator('label:has(input[type="checkbox"][value="实验"]) input').check();
    await teacher.locator('label:has(input[type="checkbox"][value="讨论"]) input').check();
    await teacher.getByRole('checkbox', { name: '教研室共享' }).check();
    const [uploadResponse] = await Promise.all([
      teacher.waitForResponse(response => response.url().includes('/api/v1/resources/upload')),
      teacher.getByRole('button', { name: '立即挂载' }).click()
    ]);
    const uploadBody = await uploadResponse.json();
    assert.equal(uploadBody.code, 200, JSON.stringify(uploadBody));
    const resources = (await (await api(teacher, `/api/v1/resources?courseId=${shared.course.id}`)).json()).data;
    const resource = resources.find(item => item.resourceName === name);
    assert.deepEqual(resource.tags, ['理论', '实验', '讨论']);
    assert.equal(resource.isPublic, true);
    const sharedLibrary = otherTeacher.getByTestId('shared-resource-library');
    await sharedLibrary.getByRole('textbox', { name: '共享资源关键词' }).fill(name);
    await sharedLibrary.getByRole('button', { name: '检索共享资源' }).click();
    const sharedCard = otherTeacher.getByTestId(`shared-resource-${resource.id}`);
    await sharedCard.waitFor();
    assert.match(await sharedCard.innerText(), /郭军/);
    assert.match(await sharedCard.innerText(), new RegExp(shared.course.courseName));
    await otherTeacher.screenshot({ path: path.join(evidenceDir, '01-cross-teacher-shared-search.png'), fullPage: true });
    const [sharedPreview] = await Promise.all([
      otherTeacher.waitForResponse(response => response.url().includes('/api/v1/resources/preview/') && response.status() === 200),
      sharedCard.getByRole('button', { name: '授权预览' }).click()
    ]);
    assert.equal((await sharedPreview.body()).subarray(0, 5).toString(), '%PDF-');
    await otherTeacher.getByRole('button', { name: '关闭', exact: true }).click();
    const sharedTicketResponse = await api(otherTeacher, `/api/v1/resources/${resource.id}/preview-ticket`, 'POST');
    assert.equal((await sharedTicketResponse.json()).code, 200);
    const sharedTicketUrl = (await sharedTicketResponse.json()).data.url;
    assert.equal((await otherTeacher.request.get(backend + sharedTicketUrl)).status(), 401);
    assert.equal((await api(teacher, sharedTicketUrl)).status(), 403);
    const foreignLibrary = foreignTeacher.getByTestId('shared-resource-library');
    await foreignLibrary.getByRole('textbox', { name: '共享资源关键词' }).fill(name);
    await foreignLibrary.getByRole('button', { name: '检索共享资源' }).click();
    await foreignLibrary.getByText('暂无符合条件的共享资源').waitFor();
    assert.equal(await foreignTeacher.getByTestId(`shared-resource-${resource.id}`).count(), 0);
    console.log('PASS US-10 Chrome: same-department teacher finds and previews shared resource; foreign department cannot discover it');
    const myResourceList = teacher.getByTestId('my-resource-list');
    await teacher.getByRole('button', { name: '实验', exact: true }).click();
    await myResourceList.getByText(name, { exact: true }).waitFor();
    await teacher.getByRole('button', { name: '讨论', exact: true }).click();
    await myResourceList.getByText(name, { exact: true }).waitFor();
    await teacher.getByRole('button', { name: '未标注', exact: true }).click();
    await myResourceList.getByText(name, { exact: true }).waitFor({ state: 'detached' });
    await teacher.getByRole('button', { name: '全部', exact: true }).click();
    await myResourceList.getByText(name, { exact: true }).waitFor();
    const resourceCard = myResourceList.locator('div').filter({ hasText: name }).filter({ has: teacher.getByRole('button', { name: '编辑标签与共享' }) }).last();
    const [previewTicketUi, previewPdfUi] = await Promise.all([
      teacher.waitForResponse(response => response.url().includes(`/api/v1/resources/${resource.id}/preview-ticket`)),
      teacher.waitForResponse(response => response.url().includes('/api/v1/resources/preview/') && response.status() === 200),
      resourceCard.getByRole('button', { name: '授权预览' }).click()
    ]);
    assert.equal((await previewTicketUi.json()).code, 200);
    assert.equal((await previewPdfUi.body()).subarray(0, 5).toString(), '%PDF-');
    await teacher.getByTitle('课件 PDF 预览').waitFor();
    assert.match(await teacher.getByTitle('课件 PDF 预览').getAttribute('src'), /^blob:/);
    await teacher.getByRole('button', { name: '关闭', exact: true }).click();
    await resourceCard.getByRole('button', { name: '编辑标签与共享' }).click();
    const editModal = teacher.locator('div.fixed.inset-0').filter({ has: teacher.getByRole('heading', { name: '编辑资源标签与共享范围' }) });
    await editModal.getByRole('checkbox', { name: '理论' }).uncheck();
    await editModal.getByRole('checkbox', { name: '讨论' }).uncheck();
    await editModal.getByRole('checkbox', { name: '教研室共享' }).uncheck();
    await teacher.screenshot({ path: path.join(evidenceDir, '02-edit-tags-and-sharing.png'), fullPage: true });
    const [metadataResponse] = await Promise.all([
      teacher.waitForResponse(response => response.url().endsWith('/api/v1/resources') && response.request().method() === 'POST'),
      editModal.getByRole('button', { name: '保存修改' }).click()
    ]);
    assert.equal((await metadataResponse.json()).code, 200);
    const edited = (await (await api(teacher, `/api/v1/resources/${resource.id}`)).json()).data;
    assert.deepEqual(edited.tags, ['实验']);
    assert.equal(edited.isPublic, false);
    assert.equal(edited.fileUrl, resource.fileUrl);
    assert.equal(edited.version, resource.version);
    assert.equal(edited.dynamicWatermark, resource.dynamicWatermark);
    assert.equal((await api(otherTeacher, sharedTicketUrl)).status(), 403);
    await sharedLibrary.getByRole('button', { name: '检索共享资源' }).click();
    await sharedLibrary.getByText('暂无符合条件的共享资源').waitFor();
    assert.equal(await otherTeacher.getByTestId(`shared-resource-${resource.id}`).count(), 0);
    const invalidMetadata = await api(teacher, '/api/v1/resources', 'POST', { data: {
      id: resource.id, courseId: shared.course.id, chapter: resource.chapter,
      resourceName: resource.resourceName, tags: ['未授权标签'], isPublic: true
    }});
    assert.equal((await invalidMetadata.json()).code, 400);
    console.log('PASS US-08/10 Chrome: tags editable, sharing revocation immediate, old ticket denied, protected metadata preserved, invalid tag rejected');
    const unauthed = await teacher.request.get(backend + resource.fileUrl);
    assert.ok(unauthed.status() === 401 || unauthed.status() === 403);
    const ticketResponse = await api(teacher, `/api/v1/resources/${resource.id}/preview-ticket`, 'POST');
    assert.equal(ticketResponse.status(), 200);
    const url = (await ticketResponse.json()).data.url;
    const preview = await api(teacher, url);
    assert.equal(preview.status(), 200);
    assert.match(preview.headers()['content-type'], /application\/pdf/);
    const invalid = await api(teacher, '/api/v1/resources/upload', 'POST', { multipart: {
      file: { name: 'invalid.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('bad') },
      courseId: String(shared.course.id), chapter: '测试', resourceName: 'invalid', tags: '理论', isPublic: 'false'
    }});
    assert.equal((await invalid.json()).code, 400);
    const corrupt = await api(teacher, '/api/v1/resources/upload', 'POST', { multipart: {
      file: { name: 'corrupt.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', buffer: Buffer.from([0, 1, 2, 3, 4, 5]) },
      courseId: String(shared.course.id), chapter: '测试', resourceName: '损坏文档', tags: '理论', isPublic: 'false'
    }});
    assert.equal((await corrupt.json()).code, 200);
    const corruptId = (await corrupt.json()).data.id;
    const forbiddenDetail = await api(otherTeacher, `/api/v1/resources/${corruptId}`);
    assert.equal(forbiddenDetail.status(), 403);
    const foreignList = (await (await api(otherTeacher, '/api/v1/resources')).json()).data;
    assert.ok(!foreignList.some(item => item.id === corruptId));
    const corruptTicket = await api(teacher, `/api/v1/resources/${corruptId}/preview-ticket`, 'POST');
    const conversion = await api(teacher, (await corruptTicket.json()).data.url);
    assert.equal(conversion.status(), 422);
    const teacherToken = await teacher.evaluate(() => localStorage.getItem('jwtToken'));
    const expiredTicket = await teacher.request.post(expiredBackend + `/api/v1/resources/${resource.id}/preview-ticket`, {
      headers: { Authorization: `Bearer ${teacherToken}` }
    });
    assert.equal((await expiredTicket.json()).code, 200);
    const expiredPreview = await teacher.request.get(expiredBackend + (await expiredTicket.json()).data.url, {
      headers: { Authorization: `Bearer ${teacherToken}` }
    });
    assert.equal(expiredPreview.status(), 400);
    console.log('PASS teacher browser: upload, multiple tags, shared visibility, secure preview, invalid format, conversion failure, expired link');

    await supervisor.getByRole('button', { name: /待督导目标课程多维检索/ }).click();
    await supervisor.getByPlaceholder('按课程名 / 代码检索...').fill(shared.course.courseCode);
    await supervisor.getByRole('combobox', { name: '检索学期' }).selectOption(shared.academicTerm);
    await supervisor.locator('select:has(option[value="' + shared.className + '"])').selectOption(shared.className);
    const offeringCard = supervisor.getByTestId(`offering-${shared.id}`);
    await offeringCard.getByRole('button', { name: '课件免密预审' }).click();
    const supervisorResource = supervisor.locator('div.p-3.bg-slate-50.border.rounded-xl').filter({ hasText: name });
    await supervisorResource.waitFor();
    const [supervisorPdf] = await Promise.all([
      supervisor.waitForResponse(response => response.url().includes('/api/v1/resources/preview/') && response.status() === 200),
      supervisorResource.getByRole('button', { name: '打开限时水印预览' }).click()
    ]);
    assert.equal((await supervisorPdf.body()).subarray(0, 5).toString(), '%PDF-');
    await supervisor.getByTitle('课件 PDF 预览').waitFor();
    assert.match(await supervisor.getByTitle('课件 PDF 预览').getAttribute('src'), /^blob:/);
    await supervisor.getByRole('button', { name: '关闭', exact: true }).click();
    await offeringCard.getByRole('button', { name: /随堂评价/ }).click();
    await supervisor.getByPlaceholder('如 第三讲：需求估算与WBS分解').fill('浏览器测试');
    const sliders = supervisor.locator('input[type="range"]');
    for (let i = 0; i < 4; i++) await sliders.nth(i).fill('20');
    await supervisor.getByPlaceholder('例如：教学组织严密，能够结合实际敏捷项目案例启发学生...').fill('教学组织清晰');
    await supervisor.getByPlaceholder('例如：建议在课后作业中进一步增加甘特图与工期缓冲池实训演练...').fill('增加练习');
    const [evaluation] = await Promise.all([
      supervisor.waitForResponse(response => response.url().endsWith('/api/v1/supervisions') && response.request().method() === 'POST'),
      supervisor.getByRole('button', { name: '正式提交（待审核）' }).click()
    ]);
    assert.equal(evaluation.status(), 200);
    const evaluationData = (await evaluation.json()).data;
    assert.equal(evaluationData.status, 'PENDING_REVIEW');
    assert.ok(evaluationData.supervisorUserId);
    const teacherBefore = (await (await api(teacher, `/api/v1/supervisions?offeringId=${shared.id}`)).json()).data;
    assert.ok(!teacherBefore.some(item => item.id === evaluationData.id));
    await director.getByRole('button', { name: /督导评价审核/ }).click();
    await director.getByText('浏览器测试', { exact: false }).first().waitFor();
    const reviewCard = director.locator('div.border.rounded-xl.p-3').filter({ hasText: '听课主题：浏览器测试' });
    const [reviewResponse] = await Promise.all([
      director.waitForResponse(response => response.url().includes(`/api/v1/supervisions/${evaluationData.id}/review`)),
      reviewCard.getByRole('button', { name: '通过', exact: true }).click()
    ]);
    assert.equal((await reviewResponse.json()).code, 200);
    const reviewed = (await (await api(director, `/api/v1/supervisions/${evaluationData.id}`)).json()).data;
    assert.equal(reviewed.status, 'APPROVED_PENDING');
    assert.ok(reviewed.reviewedBy);
    const teacherAfter = (await (await api(teacher, `/api/v1/supervisions?offeringId=${shared.id}`)).json()).data;
    assert.ok(!teacherAfter.some(item => item.id === evaluationData.id));
    const coverage = (await (await api(supervisor, `/api/v1/supervisions/analytics/coverage?term=${encodeURIComponent(shared.academicTerm)}`)).json()).data;
    assert.ok(coverage.some(item => item.courseId === shared.course.id && item.evaluationIds.includes(evaluationData.id)));
    console.log('PASS supervisor/director/teacher browser: submission, identity, review, delay, term coverage');
  } finally {
    for (const page of pages) {
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await page.context().close();
    }
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
