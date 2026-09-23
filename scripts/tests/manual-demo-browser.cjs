// Manual Demonstration & Confirmation for Five Stories:
// US-01: 主任导入课程
// US-02: 教师草稿与发布 (双窗口并发冲突 409、读者版本隔离)
// US-03: 主任多教师排课 (多教师名单保留、文管A447排课、冲突拦截409、排除自身编辑)
// US-04: 结课后历史人数 (结课归档冻结、名单防篡改409、教师端历史人次与清零)
// US-05/06: 督导组合检索与权限 (仅授权专业、多维AND组合检索、清空与无结果、越权403拦截)

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.23.0-win32-x64/node_modules/playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { execSync } = require('node:child_process');

const baseURL = process.env.BASE_URL || 'http://127.0.0.1:5173';
const backendURL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const chromiumPath = process.env.US0102_CHROMIUM_PATH || 'C:/Users/a3185/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe';
const headless = process.env.HEADLESS === 'true';
const slowMo = process.env.SLOWMO ? parseInt(process.env.SLOWMO, 10) : 300;
const evidenceDir = path.resolve(__dirname, '../../docs/manual-demo-evidence');

if (!fs.existsSync(evidenceDir)) {
  fs.mkdirSync(evidenceDir, { recursive: true });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function resetDbState() {
  console.log('>>> [准备] 重置测试数据状态，确保演示可幂等重复执行...');
  try {
    const sqlStatements = [
      "DELETE FROM t_course_content_revision WHERE course_id = 1;",
      "DELETE FROM t_course WHERE course_code = 'DEMO-SE88';",
      "UPDATE t_course_offering SET is_snapshot_frozen = NULL, archived_at = NULL, archived_by = NULL WHERE id = 1;",
      "DELETE FROM t_course_offering_teacher WHERE offering_id = 1;",
      "INSERT INTO t_course_offering_teacher (offering_id, teacher_id, teacher_code, teacher_name, role_in_offering) VALUES (1, 1, 'T2024001', '郭军', 'PRIMARY');",
      "UPDATE t_course_schedule SET classroom = '文管 A447' WHERE id = 1;",
      "UPDATE t_user_account SET authorized_majors = 'SE;CS' WHERE username = 'supervisor';"
    ].join(' ');
    const cmd = `docker exec classroom-mysql mysql --default-character-set=utf8mb4 -uroot -proot classroom_ai -e "${sqlStatements}"`;
    execSync(cmd, { stdio: 'pipe' });
    console.log('>>> [准备] 数据库前置状态重置成功。');
  } catch (err) {
    console.warn('>>> [准备警告] 数据库重置异常:', err.message);
  }
}

async function capture(page, name, description) {
  const filePath = path.join(evidenceDir, name);
  await sleep(400);
  await page.screenshot({ path: filePath, fullPage: true });
  console.log(`  [存证截图] ${name} - ${description}`);
}

function attachPageHandlers(page, label) {
  page.on('dialog', async d => {
    console.log(`    [弹窗通知 - ${label}] [${d.type()}] ${d.message()}`);
    await d.accept();
  });
  page.on('pageerror', err => {
    console.error(`    [页面异常 - ${label}] ${err.message}`);
  });
}

async function login(page, username, roleLabel) {
  await page.goto(baseURL);
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('123456');
  await sleep(300);
  await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
  await page.getByRole('button', { name: '退出登录', exact: true }).waitFor({ timeout: 15000 });
  console.log(`  [登录成功] 身份：${roleLabel} (${username})`);
}

async function logout(page) {
  await sleep(300);
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page.getByPlaceholder('如 guojun, director, supervisor 等').waitFor({ timeout: 15000 });
  console.log('  [退出登录] 已返回教务登录首页');
}

(async () => {
  console.log('================================================================');
  console.log('    爱教学平台 · 五条故事人工演示与实际操作确认 (Playwright)');
  console.log(`    模式: ${headless ? 'Headless (后台)' : 'Headed (可视窗口慢速演示)'} | SlowMo: ${slowMo}ms`);
  console.log('================================================================\n');

  resetDbState();

  const browser = await chromium.launch({
    headless,
    slowMo,
    executablePath: chromiumPath,
    args: ['--start-maximized', '--window-size=1440,900']
  });

  const demoRecords = [];

  try {
    // =========================================================================
    // 故事一（US-01）：主任导入课程
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('【故事一：主任导入课程 (US-01)】开始演示');
    console.log('----------------------------------------------------------------');

    const context1 = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p1 = await context1.newPage();
    attachPageHandlers(p1, '主任页面');

    await login(p1, 'director', '教研室主任');
    await capture(p1, '01_director_workbench.png', '教研室主任工作台全貌');

    // 打开批量导入弹窗
    await p1.getByRole('button', { name: /批量导入课程/ }).click();
    await p1.getByText('专业编码速查字典 (CSV必填字段)').waitFor();
    await capture(p1, '02_director_import_dialog.png', '批量导入课程弹窗及专业编码字典');

    // 验证 JWT 鉴权下载模板
    console.log('  [操作] 点击“下载空白模板”并验证 JWT 鉴权响应...');
    const [templateResponse] = await Promise.all([
      p1.waitForResponse(r => r.url().includes('/api/v1/courses/import/template')),
      p1.getByRole('button', { name: /下载空白模板/ }).click()
    ]);
    assert.equal(templateResponse.status(), 200, '模板下载接口应返回 200');
    assert.match(templateResponse.request().headers().authorization, /^Bearer /, '模板下载必须附带合法 JWT Token');
    console.log('  [验证通过] 模板下载响应正常 (HTTP 200) 且携带有效 Bearer JWT 鉴权头');

    // 选择 CSV 文件进行预览解析
    console.log('  [操作] 选择符合规范的新增课程 CSV 文件进行预览解析...');
    const csvContent = 'courseCode,courseName,department,majorCode,credits,hours,theoryHours,practiceHours,courseType,prerequisites,description\nDEMO-SE88,新工科敏捷协同实战,软件工程教研室,SE,3,48,36,12,专业核心课,,东北大学软件工程特色专业方向实战课\n';
    const uploadInput = p1.locator('input[type=file][accept*="csv"]');

    const previewPromise = p1.waitForResponse(r => r.url().includes('/api/v1/courses/import/preview'));
    await uploadInput.setInputFiles({
      name: 'demo_courses.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent, 'utf-8')
    });
    const previewRes = await previewPromise;
    assert.equal(previewRes.status(), 200, 'CSV 预览接口应返回 200');

    // 预览校验
    await p1.getByText('有效课程数').waitFor();
    await p1.getByText('DEMO-SE88').waitFor();
    await p1.waitForFunction(() => {
      const b = [...document.querySelectorAll('button')].find(btn => btn.innerText.includes('确认导入并整批入库'));
      return b && !b.disabled;
    });
    await capture(p1, '03_director_import_preview.png', 'CSV 预检解析表格展示');

    // 确认入库
    console.log('  [操作] 点击“确认导入并整批入库 (US-01)”...');
    const confirmPromise = p1.waitForResponse(r => r.url().includes('/api/v1/courses/import/confirm'));
    const confirmBtn = p1.getByRole('button', { name: /确认导入并整批入库/ });
    await confirmBtn.click();
    const confirmRes = await confirmPromise;
    assert.equal(confirmRes.status(), 200, '整批入库接口应返回 200');

    // 验证弹窗关闭并在课程列表中展示新课程
    await p1.waitForFunction(() => !document.querySelector('.fixed.inset-0'));
    await p1.getByPlaceholder('搜索课程名称 / 代码 / 教师 / 先修...').fill('新工科敏捷协同实战');
    await sleep(500);
    await p1.getByText('新工科敏捷协同实战').first().waitFor();
    await capture(p1, '04_director_imported_course_in_list.png', '新课程整批入库成功并展示在档案列表中');
    console.log('  [验证通过] 故事一 (US-01) 主任导入课程全链路验证完成！');

    demoRecords.push({
      story: '故事一：主任导入课程 (US-01)',
      role: '教研室主任 (director)',
      steps: '登录 -> 批量导入弹窗 -> JWT鉴权模板下载 -> CSV预检解析 -> 确认整批入库 -> 列表检索验证',
      result: '通过',
      screenshot: '04_director_imported_course_in_list.png'
    });

    await logout(p1);
    await context1.close();

    // =========================================================================
    // 故事二（US-02）：教师草稿与发布 (双窗口并发冲突拦截、版本隔离)
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('【故事二：教师草稿与发布 (US-02)】开始演示');
    console.log('----------------------------------------------------------------');

    // 建立两个独立的浏览器上下文模拟两个并发操作窗口
    const ctxA = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ctxB = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();
    attachPageHandlers(pageA, '教师窗口 A');
    attachPageHandlers(pageB, '教师窗口 B');

    console.log('  [操作] 窗口 A 登录任课教师 (guojun)...');
    await login(pageA, 'guojun', '任课教师 A');
    console.log('  [操作] 窗口 B 登录同一任课教师 (guojun)...');
    await login(pageB, 'guojun', '任课教师 B');

    const introPlaceholder = '请输入课程背景、学科定位、主要授课内容概括...';
    const assessmentPlaceholder = '例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%';
    const objectivesPlaceholder = '明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标...';

    // 等待编辑器加载就绪
    await pageA.waitForFunction(() => {
      const b = [...document.querySelectorAll('button')].find(btn => btn.innerText.trim() === '暂存草稿');
      return b && !b.disabled;
    });
    await pageB.waitForFunction(() => {
      const b = [...document.querySelectorAll('button')].find(btn => btn.innerText.trim() === '暂存草稿');
      return b && !b.disabled;
    });

    // 窗口 A: 编写不完整草稿并保存
    console.log('  [操作] 窗口 A 编辑课程简介草稿并点击“暂存草稿”...');
    await pageA.getByPlaceholder(introPlaceholder).fill('【人工演示草稿】东北大学软件项目管理课程教学大纲与实践要求');
    const [draftRespA] = await Promise.all([
      pageA.waitForResponse(r => r.url().includes('/content/draft') && r.request().method() === 'PUT'),
      pageA.getByRole('button', { name: '暂存草稿', exact: true }).click()
    ]);
    assert.equal(draftRespA.status(), 200, '窗口 A 草稿保存应成功');
    await capture(pageA, '05_teacher_draft_saved.png', '窗口 A 课程简介草稿保存成功');

    // 窗口 B: 未刷新持有旧 lockVersion，尝试覆盖写
    console.log('  [操作] 窗口 B (陈旧锁版本) 尝试覆盖保存草稿...');
    await pageB.getByPlaceholder(introPlaceholder).fill('【陈旧覆盖尝试】旧页面未经刷新直接保存');
    const [draftRespB] = await Promise.all([
      pageB.waitForResponse(r => r.url().includes('/content/draft') && r.request().method() === 'PUT'),
      pageB.getByRole('button', { name: '暂存草稿', exact: true }).click()
    ]);
    assert.equal(draftRespB.status(), 409, '陈旧版本写入应被乐观锁拦截并返回 HTTP 409');
    await pageB.getByText(/检测到并发修改冲突/).waitFor();
    assert.equal(await pageB.getByRole('button', { name: '暂存草稿', exact: true }).isDisabled(), true, '并发冲突后暂存按钮应自动置灰');
    await capture(pageB, '06_teacher_concurrent_conflict_409.png', '窗口 B 触发并发修改冲突 409 告警并锁定提交');
    console.log('  [验证通过] 乐观锁并发冲突 409 拦截生效，成功阻止静默数据覆盖！');
    await ctxB.close();

    // 窗口 A: 补齐考核和目标，正式发布 v1
    console.log('  [操作] 窗口 A 补全考核方式与目标，执行“正式发布”...');
    await pageA.getByPlaceholder(assessmentPlaceholder).fill('平时作业与甘特图 30% + 敏捷Sprint答辩 30% + 期末综合评审 40%');
    await pageA.getByPlaceholder(objectivesPlaceholder).fill('掌握现代敏捷软件工程生命周期，具备跨角色协同与风险治理能力。');
    const [pubResp1] = await Promise.all([
      pageA.waitForResponse(r => r.url().includes('/content/publish') && r.request().method() === 'POST'),
      pageA.getByRole('button', { name: '正式发布', exact: true }).click()
    ]);
    assert.equal(pubResp1.status(), 200, '正式发布应返回 200');
    await pageA.getByText('已发布 v1').first().waitFor();
    await capture(pageA, '07_teacher_published_v1.png', '课程内容正式发布成功 (v1)');

    // 验证外部读者端版本隔离 (编辑新草稿，读者依然只读 v1)
    console.log('  [操作] 教师在 v1 基础上修改简介并再次保存草稿，验证读者版本隔离...');
    await pageA.getByPlaceholder(introPlaceholder).fill('【二次草稿编辑】新增敏捷大模型辅助开发章节说明');
    await Promise.all([
      pageA.waitForResponse(r => r.url().includes('/content/draft') && r.request().method() === 'PUT'),
      pageA.getByRole('button', { name: '暂存草稿', exact: true }).click()
    ]);

    // 发起读者公开接口查询，验证读者读取的依然是已发布的 v1，未受草稿影响
    const readerToken = await pageA.evaluate(() => localStorage.getItem('jwtToken'));
    const readerResp1 = await pageA.request.get(`${backendURL}/api/v1/courses/1/content/published`, {
      headers: { Authorization: `Bearer ${readerToken}` }
    });
    const publishedV1 = (await readerResp1.json()).data;
    assert.equal(publishedV1.publishVersion, 1, '读者接口读取的版本应严格为 1');
    assert.equal(publishedV1.description, '【人工演示草稿】东北大学软件项目管理课程教学大纲与实践要求', '读者读取的简介内容应保持 v1 隔离状态');
    await capture(pageA, '08_teacher_reader_isolation_v1.png', '读者版本隔离确认：读者仍读取 v1，草稿未对外泄露');
    console.log('  [验证通过] 读者版本隔离机制生效：草稿编辑期间对外公开接口严格返回稳定 v1');

    // 正式发布 v2
    console.log('  [操作] 教师执行第二次“正式发布”升级至 v2...');
    const [pubResp2] = await Promise.all([
      pageA.waitForResponse(r => r.url().includes('/content/publish') && r.request().method() === 'POST'),
      pageA.getByRole('button', { name: '正式发布', exact: true }).click()
    ]);
    assert.equal(pubResp2.status(), 200);
    await pageA.getByText('已发布 v2').first().waitFor();
    await capture(pageA, '09_teacher_published_v2.png', '课程内容升级发布为 v2，版本与发布人时间全留痕');
    console.log('  [验证通过] 故事二 (US-02) 教师草稿与发布、并发乐观锁409、读者版本隔离全流程通过！');

    demoRecords.push({
      story: '故事二：教师草稿与发布 (US-02)',
      role: '任课教师 (guojun)',
      steps: '草稿保存 -> 双窗口并发冲突409拦截 -> 正式发布v1 -> 二次草稿修改 -> 读者版本隔离验证 -> 正式发布v2',
      result: '通过',
      screenshot: '09_teacher_published_v2.png'
    });

    await logout(pageA);
    await ctxA.close();

    // =========================================================================
    // 故事三（US-03）：主任多教师排课
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('【故事三：主任多教师排课 (US-03)】开始演示');
    console.log('----------------------------------------------------------------');

    const context3 = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p3 = await context3.newPage();
    attachPageHandlers(p3, '排课看板');

    await login(p3, 'director', '教研室主任');
    await p3.getByRole('button', { name: /开课排课统筹看板/ }).click();
    const board = p3.getByRole('region', { name: '开课与排课' });
    await board.waitFor();
    await capture(p3, '10_director_scheduling_board.png', '开课与排课统筹看板全貌');

    // 1. 编辑班次：添加协同教师并核对名单完整保留
    console.log('  [操作] 编辑班次添加协同教师 (姜琳颖) 并确认名单...');
    const targetRow = board.locator('tr').filter({ hasText: '软件项目管理' }).first();
    await targetRow.getByRole('button', { name: '编辑班次' }).click();

    const offeringDialog = p3.getByRole('dialog', { name: '维护班次' });
    await offeringDialog.waitFor();
    await offeringDialog.getByText(/选课名单 · 已选 \d+ 人/).waitFor();
    await capture(p3, '11_director_edit_offering_modal.png', '班次维护弹窗：协同教师配置与已选名单');

    // 勾选协同教师 (姜琳颖)
    const collabCheckbox = offeringDialog.getByRole('checkbox', { name: /姜琳颖/ });
    if (!(await collabCheckbox.isChecked())) {
      await collabCheckbox.check();
    }

    const [updateOfferingRes] = await Promise.all([
      p3.waitForResponse(r => r.url().includes('/api/v1/courses/offerings') && r.request().method() === 'PUT'),
      offeringDialog.getByRole('button', { name: '保存', exact: true }).click()
    ]);
    assert.equal(updateOfferingRes.status(), 200, '更新班次应返回 200');
    await p3.waitForFunction(() => !document.querySelector('[role=dialog]'));
    await board.getByText(/郭军（主讲）、姜琳颖（协同）/).first().waitFor();
    await capture(p3, '12_director_collaborating_teachers_saved.png', '任课团队更新成功：展示主讲与协同双教师');
    console.log('  [验证通过] 班次任课团队已成功展示主讲+协同双教师，名单无损保留！');

    // 2. 演示排课冲突拦截 (HTTP 409 与详细冲突信息)
    console.log('  [操作] 演示排课冲突检测：尝试在同一时段不同教室为同一教师排课...');
    await board.getByRole('button', { name: '新增排课', exact: true }).click();
    const scheduleDialog = p3.getByRole('dialog', { name: '维护排课' });
    await scheduleDialog.waitFor();

    // 班次 1 已在周三第3-4节在文管 A447 排课；我们在同一时段新增到信息馆 B201
    await scheduleDialog.getByLabel('开课班次').selectOption({ index: 1 });
    await scheduleDialog.getByLabel('教室', { exact: true }).fill('信息馆 B201');
    await scheduleDialog.getByLabel('星期', { exact: true }).selectOption('3');
    await scheduleDialog.getByLabel('起始节').fill('3');
    await scheduleDialog.getByLabel('结束节').fill('4');

    const [conflictResp] = await Promise.all([
      p3.waitForResponse(r => r.url().includes('/api/v1/schedules') && r.request().method() === 'POST'),
      scheduleDialog.getByRole('button', { name: '保存', exact: true }).click()
    ]);
    assert.equal(conflictResp.status(), 409, '相同教师时段冲突必须返回 HTTP 409');
    const conflictArea = scheduleDialog.locator('[aria-label="冲突详情"]');
    await conflictArea.waitFor();
    const conflictText = await conflictArea.innerText();
    assert.match(conflictText, /教师冲突|冲突/);
    console.log(`  [冲突信息已展现] ${conflictText.replace(/\n/g, ' ')}`);
    await capture(p3, '13_director_schedule_conflict_409.png', '排课冲突拦截 409：弹窗展示完整冲突原因与占用信息');
    await scheduleDialog.getByRole('button', { name: '取消' }).click();
    await p3.waitForFunction(() => !document.querySelector('[role=dialog]'));
    console.log('  [验证通过] 排课冲突拦截生效，弹窗清晰显示教师冲突与已占时段！');

    // 3. 多维组合筛选与编辑排除自身逻辑
    console.log('  [操作] 组合筛选排课卡片并编辑排除自身验证...');
    await board.getByLabel('学期', { exact: true }).fill('2026-2027秋季');
    await board.getByLabel('教室', { exact: true }).fill('文管 A447');
    await board.getByRole('button', { name: '筛选排课' }).click();
    await board.locator('article').filter({ hasText: '文管 A447' }).first().waitFor();

    // 编辑文管 A448，验证更新排除自己不报冲突
    await board.locator('article').filter({ hasText: '文管 A447' }).first().getByRole('button', { name: '编辑排课', exact: true }).click();
    const editScheduleDialog = p3.getByRole('dialog', { name: '维护排课' });
    await editScheduleDialog.waitFor();
    await editScheduleDialog.getByLabel('教室', { exact: true }).fill('文管 A448');
    const [editSchedResp] = await Promise.all([
      p3.waitForResponse(r => r.url().includes('/api/v1/schedules') && r.request().method() === 'POST'),
      editScheduleDialog.getByRole('button', { name: '保存', exact: true }).click()
    ]);
    assert.equal(editSchedResp.status(), 200, '排除自身后保存应成功');
    await p3.waitForFunction(() => !document.querySelector('[role=dialog]'));

    // 再次改回文管 A447 保持数据清洁
    await board.getByRole('button', { name: '重置', exact: true }).click();
    await board.locator('article').filter({ hasText: '文管 A448' }).first().getByRole('button', { name: '编辑排课', exact: true }).click();
    const restoreDialog = p3.getByRole('dialog', { name: '维护排课' });
    await restoreDialog.waitFor();
    await restoreDialog.getByLabel('教室', { exact: true }).fill('文管 A447');
    await Promise.all([
      p3.waitForResponse(r => r.url().includes('/api/v1/schedules') && r.request().method() === 'POST'),
      restoreDialog.getByRole('button', { name: '保存', exact: true }).click()
    ]);
    await p3.waitForFunction(() => !document.querySelector('[role=dialog]'));
    await board.locator('article').filter({ hasText: '文管 A447' }).first().waitFor();
    await capture(p3, '14_director_schedule_edited_and_reset.png', '排课更新排除自身冲突逻辑验证通过');
    console.log('  [验证通过] 故事三 (US-03) 主任多教师排课、冲突拦截与自排除逻辑通过！');

    demoRecords.push({
      story: '故事三：主任多教师排课 (US-03)',
      role: '教研室主任 (director)',
      steps: '开课排课看板 -> 协同教师维护 -> 名单保留 -> 冲突排课409拦截与弹窗详情 -> 多维筛选与排除自身排课编辑',
      result: '通过',
      screenshot: '13_director_schedule_conflict_409.png'
    });

    // =========================================================================
    // 故事四（US-04）：结课后历史人数 (结课归档冻结、名单防篡改409、教师端历史人次)
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('【故事四：结课后历史人数 (US-04)】开始演示');
    console.log('----------------------------------------------------------------');

    // 主任执行结课归档
    console.log('  [操作] 主任对《软件项目管理》执行“结课归档”...');
    const archiveRow = board.locator('tr').filter({ hasText: '软件项目管理' }).first();
    await archiveRow.getByRole('button', { name: '结课归档' }).click();
    await archiveRow.getByText('已归档', { exact: true }).waitFor();

    // 验证界面完全冻结，编辑班次按钮消失
    assert.equal(await archiveRow.getByRole('button', { name: '编辑班次' }).count(), 0, '归档后编辑班次按钮必须消失');
    await capture(p3, '15_director_offering_archived_frozen.png', '结课归档成功：状态变为已归档，编辑操作已被物理锁定');
    console.log('  [验证通过] 前端班次已显示“已归档”，编辑按钮已完全冻结！');

    // 验证后端接口层名单防篡改拦截 (HTTP 409)
    console.log('  [操作] 尝试通过 API 对已归档班次添加学生名单...');
    const directorToken = await p3.evaluate(() => localStorage.getItem('jwtToken'));
    const tamperResp = await p3.request.post(`${backendURL}/api/v1/courses/offerings/1/students/add`, {
      headers: { Authorization: `Bearer ${directorToken}` },
      data: ['20246085']
    });
    assert.equal(tamperResp.status(), 409, '对已归档班次修改名单必须返回 409 拒绝');
    console.log('  [验证通过] 后端防篡改拦截生效：已归档班次修改名单返回 HTTP 409');

    await logout(p3);
    await context3.close();

    // 教师登录查看历史开课与人次
    console.log('  [操作] 教师 (guojun) 登录查看历史开课人次与归档记录...');
    const context4 = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p4 = await context4.newPage();
    attachPageHandlers(p4, '教师历史页面');
    await login(p4, 'guojun', '任课教师');

    const historySection = p4.getByRole('region', { name: '历史开课与人次' });
    await historySection.waitFor();
    await historySection.getByRole('cell', { hasText: '已归档' }).first().waitFor();
    const historyText = await historySection.innerText();
    assert.match(historyText, /累计人次 \d+/, '必须展示历史累计人次统计');
    await capture(p4, '16_teacher_historical_offerings_and_headcount.png', '教师端历史开课与人数、人次看板展示');
    console.log('  [验证通过] 教师端历史开课看板清晰呈现已归档班次及累计上课人次！');

    // 查询不存在的学期验证人次清零
    console.log('  [操作] 查询不存在的历史学期，验证人次清零与空表提示...');
    const termControl = historySection.getByLabel('历史学期');
    const isSelect = await termControl.evaluate(el => el.tagName.toLowerCase() === 'select').catch(() => false);
    if (isSelect) {
      await termControl.selectOption('2024-2025春季');
    } else {
      await termControl.fill('2099未知学期');
    }
    await historySection.getByRole('button', { name: '查询历史' }).click();
    await historySection.getByText('暂无历史开课记录', { exact: true }).waitFor();
    const zeroHistoryText = await historySection.innerText();
    assert.match(zeroHistoryText, /累计人次 0/, '无匹配时累计人次必须为 0');
    await capture(p4, '17_teacher_history_empty_query_zero.png', '历史学期空查询：展示暂无记录且累计人次归零');

    // 清空条件恢复
    await historySection.getByRole('button', { name: '清空条件' }).click();
    await historySection.getByRole('cell', { hasText: '已归档' }).first().waitFor();
    console.log('  [验证通过] 故事四 (US-04) 结课归档冻结、防篡改409与历史人次口径验证完成！');

    demoRecords.push({
      story: '故事四：结课后历史人数 (US-04)',
      role: '教研室主任 (director) + 任课教师 (guojun)',
      steps: '主任结课归档 -> 状态已归档且编辑按钮冻结 -> API修改名单409拒绝 -> 教师查看历史归档人次 -> 空查询人次清零',
      result: '通过',
      screenshot: '16_teacher_historical_offerings_and_headcount.png'
    });

    await logout(p4);
    await context4.close();

    // =========================================================================
    // 故事五（US-06）：督导组合检索与权限 (授权专业隔离、多维AND检索、越权403)
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('【故事五：督导组合检索与权限 (US-06)】开始演示');
    console.log('----------------------------------------------------------------');

    const context5 = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const p5 = await context5.newPage();
    attachPageHandlers(p5, '督导页面');

    await login(p5, 'supervisor', '教学督导');
    await p5.getByRole('button', { name: /待督导目标课程多维检索/ }).click();

    // 1. 验证授权专业下拉选项：只出现 SE、CS，绝不出现未授权的 AI、DS
    console.log('  [验证] 检查督导专业授权选项隔离...');
    const majorSelect = p5.getByLabel('授权专业', { exact: true });
    await majorSelect.waitFor();
    const majorOptions = await majorSelect.locator('option').allTextContents();
    console.log(`  [授权专业选项] ${majorOptions.join(', ')}`);
    assert.ok(majorOptions.includes('全部授权专业'));
    assert.ok(majorOptions.includes('软件工程'));
    assert.ok(!majorOptions.includes('人工智能'), '未授权专业 (人工智能) 严禁出现在选项中');
    await capture(p5, '18_supervisor_authorized_majors_only.png', '督导专业授权下拉框：严格物理隔离，仅展示已授权专业');
    console.log('  [验证通过] 督导授权专业选项隔离生效，未授权专业已完全剔除！');

    // 2. 多维复合 AND 条件检索
    console.log('  [操作] 执行专业 + 任课教师 + 学期 + 课程名多维 AND 组合检索...');
    await majorSelect.selectOption({ label: '软件工程' });
    await p5.getByLabel('任课教师', { exact: true }).selectOption({ index: 1 });
    await p5.getByLabel('检索学期', { exact: true }).selectOption({ index: 1 });
    await p5.getByPlaceholder('按课程名 / 代码检索...').fill('软件项目管理');
    await sleep(400);
    await p5.locator('h3').filter({ hasText: '软件项目管理' }).first().waitFor();
    await capture(p5, '19_supervisor_multidimensional_and_search.png', '多维 AND 组合检索结果精准呈现');
    console.log('  [验证通过] 多维 AND 检索生效，精准匹配目标开课！');

    // 3. 空条件与清空重置
    console.log('  [操作] 检索不存在的关键字与重置条件...');
    await p5.getByPlaceholder('按课程名 / 代码检索...').fill('不存在的课程XYZ');
    await p5.getByText('未检索到符合条件的待督导开课信息').waitFor();
    await capture(p5, '20_supervisor_no_results.png', '检索无匹配时展示规范空状态');

    await p5.getByRole('button', { name: '清空检索条件' }).click();
    await sleep(400);
    await p5.locator('h3').first().waitFor();
    const countBadge = p5.getByText(/共检索到 \d+ 门待督导开课/);
    await countBadge.waitFor();
    console.log(`  [列表恢复展示] ${(await countBadge.innerText()).trim()}`);

    // 4. 越权访问拦截验证 (HTTP 403 Forbidden)
    console.log('  [操作] 验证督导越权请求未授权专业/课程拦截 (HTTP 403)...');
    const supToken = await p5.evaluate(() => localStorage.getItem('jwtToken'));
    
    // 尝试越权按 AI 专业查询开课
    const forbiddenMajorResp = await p5.request.get(`${backendURL}/api/v1/courses/offerings?majorCode=AI`, {
      headers: { Authorization: `Bearer ${supToken}` }
    });
    assert.equal(forbiddenMajorResp.status(), 403, '查询未授权专业开课必须返回 HTTP 403');

    // 尝试越权按 majorId=3 (AI) 查询
    const forbiddenMajorIdResp = await p5.request.get(`${backendURL}/api/v1/courses/offerings?majorId=3`, {
      headers: { Authorization: `Bearer ${supToken}` }
    });
    assert.equal(forbiddenMajorIdResp.status(), 403, '查询未授权专业 ID 必须返回 HTTP 403');

    // 匿名请求历史开课必须 401
    const unauthResp = await p5.request.get(`${backendURL}/api/v1/courses/offerings/history`);
    assert.equal(unauthResp.status(), 401, '无 Token 访问受保护接口必须返回 HTTP 401');

    await capture(p5, '21_supervisor_security_check_passed.png', '督导越权请求已成功被安全拦截 (403/401)');
    console.log('  [验证通过] 故事五 (US-06) 督导组合检索与安全权限边界验证完成！');

    demoRecords.push({
      story: '故事五：督导组合检索与权限 (US-06)',
      role: '教学督导 (supervisor)',
      steps: '督导工作台 -> 仅展示授权专业选项 -> 专业/教师/学期/关键字多维AND检索 -> 清空重置 -> 越权请求403拦截',
      result: '通过',
      screenshot: '19_supervisor_multidimensional_and_search.png'
    });

    await logout(p5);
    await context5.close();

    console.log('\n================================================================');
    console.log('    [演示完毕] 五条故事全部人工演示并完成实际操作确认！');
    console.log('================================================================');
    console.table(demoRecords);

  } finally {
    await browser.close();
  }
})();
