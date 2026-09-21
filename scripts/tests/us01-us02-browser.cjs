// Browser UI/HTTP-contract regression; API responses are synthetic, not an end-to-end DB test.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const url = process.env.US0102_PREVIEW_URL || 'http://127.0.0.1:15173';
const course = { id: 1, courseCode: 'TEST', courseName: '合成测试课程', department: '测试教研室', majorCode: 'SE', credits: 3, hours: 48, teacherName: '测试教师' };

(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.US0102_CHROMIUM_PATH ? { executablePath: process.env.US0102_CHROMIUM_PATH } : {}) });
  try {
    const page = await browser.newPage();
    page.on('dialog', dialog => { console.log('DIALOG:', dialog.message()); return dialog.accept(); });
    page.on('pageerror', error => console.error('PAGE ERROR:', error.message));
    let role = 'TEACHER', failDraft = false, forceConflict = false, draftId = 10, lockVersion = 0, publishVersion = 0;
    let published = null, lastWrite = null, importError = true, confirmed = false;
    let draft = { id: draftId, courseId: 1, status: 'DRAFT', lockVersion, publishVersion, description: '', objectives: '', assessmentMethod: '' };
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname;
      let data = [], status = 200, message = 'OK';
      if (path.endsWith('/auth/me')) data = { username: 'synthetic', role, realName: '测试教师', department: '测试教研室', teacherCode: 'TEST-T1' };
      else if (path.endsWith('/auth/csrf')) data = { csrfToken: 'synthetic' };
      else if (path === '/api/v1/courses') data = [course];
      else if (path === '/api/v1/courses/offerings') data = [{ id: 2, course, teacherName: '测试教师', teacherCode: 'TEST-T1', academicTerm: 'TEST', studentCount: 0, className: '合成班' }];
      else if (path.endsWith('/content/draft')) {
        if (failDraft) { status = 503; message = '草稿加载测试失败'; data = null; }
        else if (request.method() === 'GET') { await new Promise(resolve => setTimeout(resolve, 150)); data = draft; }
        else {
          lastWrite = request.postDataJSON();
          if (forceConflict) { status = 409; message = '检测到并发修改冲突，请重新拉取最新草稿'; data = null; }
          else { draft = { ...draft, ...lastWrite, lockVersion: ++lockVersion }; data = draft; }
        }
      } else if (path.endsWith('/content/publish')) {
        lastWrite = request.postDataJSON();
        published = { ...draft, ...lastWrite, status: 'PUBLISHED', publishVersion: ++publishVersion, publisherName: '测试教师', publishedAt: '2026-09-19T12:00:00' };
        draft = { ...published, id: ++draftId, status: 'DRAFT', lockVersion: 0 };
        lockVersion = 0; data = published;
      } else if (path.endsWith('/content/published')) data = published;
      else if (path.endsWith('/managed-majors')) data = [{ id: 1, majorCode: 'SE', majorName: '合成专业', department: '测试教研室' }];
      else if (path.endsWith('/import/preview')) data = {
        batchId: 'synthetic-batch', totalCount: 1, successCount: importError ? 0 : 1, errorCount: importError ? 1 : 0,
        errors: importError ? [{ rowNumber: 2, field: '学分', reason: '学分必须大于 0' }, { rowNumber: 2, field: '总学时', reason: '总学时必须大于 0' }] : [],
        validRows: importError ? [] : [course]
      };
      else if (path.endsWith('/import/confirm')) { confirmed = true; data = { importedCount: 1 }; }
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status, message, data }) });
    });
    await page.goto(url);
    const save = page.getByRole('button', { name: '暂存草稿', exact: true });
    await save.waitFor();
    await page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find(b => b.innerText.trim() === '暂存草稿')?.disabled);
    await page.getByPlaceholder('请输入课程背景、学科定位、主要授课内容概括...').fill('不完整草稿');
    await save.click();
    await page.waitForFunction(() => document.body.innerText.includes('lock-v1'));
    assert.deepEqual([lastWrite.draftId, lastWrite.lockVersion, lastWrite.publishVersion], [10, 0, 0]);
    console.log('PASS teacher save includes draft identity, lock and base publication; response updates lock');
    await page.getByPlaceholder('例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%').fill('合成考核');
    await page.getByPlaceholder('明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标...').fill('合成目标');
    await page.getByRole('button', { name: '正式发布', exact: true }).click();
    try {
      await page.waitForFunction(() => document.body.innerText.includes('已发布 v1'), null, { timeout: 10000 });
    } catch (error) {
      console.error('Publication diagnostics:', { lastWrite, published, draft, body: (await page.locator('body').innerText()).slice(0, 6000) });
      throw error;
    }
    assert.deepEqual([lastWrite.draftId, lastWrite.lockVersion, lastWrite.publishVersion], [10, 1, 0]);
    console.log('PASS publication displays v1 and loads a new draft');
    forceConflict = true;
    await save.click();
    await page.getByText('检测到并发修改冲突，请重新拉取最新草稿', { exact: true }).waitFor();
    assert.equal(await save.isDisabled(), true);
    forceConflict = false;
    await page.getByRole('button', { name: '拉取最新草稿', exact: true }).click();
    await page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find(b => b.innerText.trim() === '暂存草稿')?.disabled);
    console.log('PASS 409 prevents blind retry; reload restores editing');
    failDraft = true;
    await page.reload();
    await page.getByText('草稿加载测试失败', { exact: true }).waitFor();
    assert.equal(await save.isDisabled(), true);
    console.log('PASS failed draft fetch does not fabricate a writable lock-zero draft');
    role = 'DIRECTOR'; failDraft = false;
    await page.reload();
    await page.getByRole('button', { name: /批量导入课程/ }).click();
    const upload = page.locator('input[type=file][accept*="csv"]');
    await upload.setInputFiles({ name: 'synthetic.csv', mimeType: 'text/csv', buffer: Buffer.from('synthetic fixture') });
    await page.getByText('学分必须大于 0', { exact: true }).waitFor();
    const confirm = page.getByRole('button', { name: /确认导入并整批入库/ });
    assert.equal(await confirm.isDisabled(), true);
    console.log('PASS invalid CSV preview displays field errors and prevents confirmation');
    importError = false;
    await upload.setInputFiles({ name: 'valid.csv', mimeType: 'text/csv', buffer: Buffer.from('synthetic valid fixture') });
    await page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('确认导入并整批入库'))?.disabled);
    await confirm.click();
    await page.waitForTimeout(100);
    assert.equal(confirmed, true);
    console.log('PASS valid preview can confirm and refresh course list');
    console.log('SUMMARY: 6 browser checks passed (synthetic API contract fixtures).');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
