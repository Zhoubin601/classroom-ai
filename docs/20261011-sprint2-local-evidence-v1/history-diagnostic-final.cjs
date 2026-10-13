// Real backend/browser regression for version snapshots and required scores.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const frontend = process.env.EXP3_FRONTEND_URL || 'http://127.0.0.1:15173';
const backend = process.env.EXP3_BACKEND_URL || 'http://127.0.0.1:18081';
const evidence = path.join(__dirname, 'history-diagnostic-final');
fs.mkdirSync(evidence, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true,
    ...(process.env.EXP3_CHROMIUM_PATH ? { executablePath: process.env.EXP3_CHROMIUM_PATH } : {}) });
  const results = [];
  try {
    async function login(username) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
      const page = await context.newPage();
      page.on('dialog', dialog => dialog.accept());
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('123456');
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('button', { name: '退出登录', exact: true }).waitFor();
      return page;
    }
    async function api(page, route, method = 'GET', data) {
      const token = await page.evaluate(() => localStorage.getItem('jwtToken'));
      const response = await page.request.fetch(backend + route, {
        method, headers: { Authorization: `Bearer ${token}` }, ...(data !== undefined ? { data } : {})
      });
      return { status: response.status(), body: await response.json() };
    }
    const teacher = await login('guojun');
    const supervisor = await login('supervisor');
    const director = await login('director');
    const foreign = await login('jiangly');
    const teacherOfferings = (await api(teacher, '/api/v1/courses/offerings')).body.data;
    const common = (await api(supervisor, '/api/v1/courses/offerings')).body.data.find(o => teacherOfferings.some(t => t.id === o.id));
    assert.ok(common);
    const base = { offeringId: common.id, listenTopic: '评分边界专项',
      highlights: '讲解清晰\n组织有序\n互动充分', suggestions: '增加练习' };
    const scores = { scoreAttitude: 0, scoreContent: 0, scoreMethod: 0, scoreEffect: 0 };
    const before = (await api(supervisor, `/api/v1/supervisions?offeringId=${common.id}`)).body.data.length;
    for (const missing of ['all', ...Object.keys(scores)]) {
      const payload = { ...base, ...scores };
      for (const key of Object.keys(scores)) if (missing === 'all' || missing === key) delete payload[key];
      const response = await api(supervisor, '/api/v1/supervisions', 'POST', payload);
      assert.equal(response.body.code, 400);
      assert.match(response.body.message, /全部四维评分/);
    }
    const nullScores = await api(supervisor, '/api/v1/supervisions', 'POST', {
      ...base, ...Object.fromEntries(Object.keys(scores).map(key => [key, null]))
    });
    assert.equal(nullScores.body.code, 400);
    assert.equal((await api(supervisor, `/api/v1/supervisions?offeringId=${common.id}`)).body.data.length, before);
    const draft = await api(supervisor, '/api/v1/supervisions', 'POST', { offeringId: common.id, isDraft: true });
    assert.equal(draft.body.code, 200);
    assert.equal(draft.body.data.status, 'DRAFT');
    const blocked = await api(supervisor, '/api/v1/supervisions', 'POST', { ...base, id: draft.body.data.id });
    assert.equal(blocked.body.code, 400);
    assert.equal((await api(supervisor, `/api/v1/supervisions/${draft.body.data.id}`)).body.data.status, 'DRAFT');
    const zero = await api(supervisor, '/api/v1/supervisions', 'POST', { ...base, ...scores, id: draft.body.data.id });
    assert.equal(zero.body.code, 200);
    assert.equal(zero.body.data.status, 'PENDING_REVIEW');
    assert.equal(zero.body.data.totalScore, 0);
    assert.equal(zero.body.data.id, draft.body.data.id);
    results.push({ check: 'scores', result: 'PASS', missingRejected: 6, explicitZerosAccepted: true,
      incompleteDraftAllowed: true, failedSubmissionKeepsDraft: true });
    console.log('PASS scores: omitted/null/partial scores rejected, explicit four zeros accepted, incomplete draft preserved');

    const courseId = common.course.id;
    const stamp = Date.now();
    const snapshots = [];
    for (const [suffix, weight] of [['v1', 'H'], ['v2', 'L']]) {
      const created = await api(teacher, `/api/v1/syllabus/course/${courseId}/from-plan`, 'POST', {
        syllabusVersion: `HISTORY-${stamp}-${suffix}`, planVersion: 'RECOMMENDED-12'
      });
      assert.equal(created.body.code, 200);
      const id = created.body.data.id;
      const mappings = (await api(teacher, `/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${id}`)).body.data;
      const goal = `历史核验-${stamp}-${suffix}`;
      const edited = await api(teacher, `/api/v1/syllabus/indicators/${mappings[0].id}`, 'PUT', { supportWeight: weight, targetGoal: goal });
      assert.equal(edited.body.code, 200);
      snapshots.push({ id, goal, weight, mappingId: mappings[0].id });
    }
    for (const snapshot of snapshots) {
      for (const page of [teacher, supervisor, director]) {
        const read = await api(page, `/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${snapshot.id}`);
        assert.equal(read.body.code, 200);
        const mapping = read.body.data.find(item => item.id === snapshot.mappingId);
        assert.equal(mapping.targetGoal, snapshot.goal);
        assert.equal(mapping.supportWeight, snapshot.weight);
      }
    }
    const old = snapshots[0], latest = snapshots[1];
    const unauthorized = await api(foreign, `/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${old.id}`);
    assert.equal(unauthorized.status, 403);
    const anonymous = await teacher.request.get(`${backend}/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${old.id}`);
    assert.equal(anonymous.status(), 401);
    const otherCourse = (await api(director, '/api/v1/courses')).body.data.find(c => c.id !== courseId);
    assert.ok(otherCourse);
    assert.equal((await api(director, `/api/v1/syllabus/course/${otherCourse.id}/indicators?syllabusId=${old.id}`)).status, 400);
    assert.equal((await api(teacher, `/api/v1/syllabus/indicators/${old.mappingId}`, 'PUT', { targetGoal: '历史不应改写' })).status, 409);
    const defaultMappings = (await api(teacher, `/api/v1/syllabus/course/${courseId}/indicators`)).body.data;
    assert.ok(defaultMappings.some(item => item.targetGoal === latest.goal));
    results.push({ check: 'history-api', result: 'PASS', roles: 3, snapshotWeights: ['H', 'L'],
      unauthorizedRejected: true, mismatchedCourseRejected: true, historicalWriteRejected: true });
    console.log('PASS history API: independent snapshots readable by authorized roles; anonymous/foreign/mismatched reads and old edits rejected');

    for (const [name, page] of [['teacher', teacher], ['director', director]]) {
      await page.reload();
      // Finish mount-time course loading before changing the selected offering.
      await page.waitForLoadState('networkidle');
      if (name === 'teacher') {
        await page.locator(`select:has(option[value="${common.id}"])`).first().selectOption(String(common.id));
      } else {
        await page.getByRole('button', { name: '毕业要求指标点矩阵 (US-05)' }).click();
        await page.locator(`select:has(option[value="${courseId}"])`).first().selectOption(String(courseId));
      }
      await page.waitForLoadState('networkidle');
      const selector = page.getByRole('combobox', { name: '查看大纲版本', exact: true });
      await selector.waitFor();
      await page.getByRole('row').filter({ hasText: latest.goal }).waitFor();
      assert.equal(await selector.inputValue(), String(latest.id));
      const historicalRequests = [];
      const capture = request => { const url = new URL(request.url()); if(url.pathname.includes('/syllabus/')) historicalRequests.push(url.pathname + url.search); };
      page.on('request', capture);
      await selector.selectOption(String(old.id));
      const oldRow = page.getByRole('row').filter({ hasText: old.goal });
      try { await oldRow.waitFor({ timeout: 5000 }); }
      catch (error) {
        const state = { role: name, expectedOldId: old.id, expectedLatestId: latest.id,
          selected: await selector.inputValue(), options: await selector.locator('option').allTextContents(),
          alerts: await page.getByRole('alert').allTextContents(), statuses: await page.getByRole('status').allTextContents(),
          rows: await page.getByRole('row').allTextContents(), historicalRequests,
          oldApiMappings: (await api(page, `/api/v1/syllabus/course/${courseId}/indicators?syllabusId=${old.id}`)).body.data };
        await page.screenshot({path:path.join(evidence, `${name}-failed-history.png`),fullPage:true});
        fs.writeFileSync(path.join(evidence, `${name}-failed-history.json`), JSON.stringify(state,null,2));
        results.push({check:`${name}-history-ui`,result:'FAIL',reason:'old goal not displayed after selection',selected:state.selected,expectedOldId:old.id,historicalRequests});
        console.log('FAIL', name, 'expected old', old.id, 'selected', state.selected);
        page.off('request', capture);
        continue;
      }
      page.off('request', capture);
      assert.ok((await oldRow.innerText()).includes('H'));
      const unsetRow = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '2-1', exact: true }) });
      assert.equal(await unsetRow.getByRole('cell', { name: '未设置', exact: true }).count(), 1);
      assert.equal(await page.getByRole('row').filter({ hasText: latest.goal }).count(), 0);
      assert.ok(await page.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled());
      for (const button of await oldRow.getByRole('button').all()) assert.ok(await button.isDisabled());
      if (name === 'director') assert.ok(await page.getByRole('button', { name: '审查锁定本版大纲', exact: true }).isDisabled());
      await page.screenshot({ path: path.join(evidence, `${name}-history-readonly.png`), fullPage: true });
      await selector.selectOption(String(latest.id));
      await page.getByRole('row').filter({ hasText: latest.goal }).waitFor();
      // The director displays mapping rows before its editable catalog finishes loading.
      await page.getByRole('button', { name: '新增认证指标点', exact: true })
        .and(page.locator(':enabled')).waitFor();
      assert.equal(await page.getByRole('button', { name: '新增认证指标点', exact: true }).isDisabled(), false);
      if (name === 'director') assert.equal(await page.getByRole('button', { name: '审查锁定本版大纲', exact: true }).isDisabled(), false);
      results.push({ check: `${name}-history-ui`, result: 'PASS', defaultLatest: true, historicalReadonly: true, currentEditable: true });
      console.log(`PASS ${name} UI: default latest, historical snapshot switch/read-only, return to editable current version`);
    }
    fs.writeFileSync(path.join(evidence, 'history-scores-results.json'), JSON.stringify(results, null, 2)); if(results.some(r=>r.result==='FAIL')) throw new Error('Historical UI diagnostic reproduced failure');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });

