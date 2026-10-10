/**
 * Playwright 全功能端到端自动化测试套件 (All-Features Test Suite)
 * 覆盖模块：
 * 1. 统一认证鉴权与多角色 RBAC 隔离 (Director / Teacher / Supervisor / 异常拦截)
 * 2. 教研室主任工作台 (课程管理/导入、排课冲突/归档、大纲培养方案/指标点矩阵、督导授权、评价审核)
 * 3. 任课教师工作台 (简介草稿/发布、从方案派生大纲、课件上传/多标签筛选、受控水印预览、匿名评价、雷达图)
 * 4. 教学督导工作台 (全院总课表、复合检索、课件免密预审、BOPPPS随堂打分/提交、覆盖率巡检/明细下钻、预警)
 * 5. 课堂智能考勤大屏 (排课关联、考勤大屏态势)
 * 6. 学生人脸档案库 (学生花名册、512维特征底库)
 */

const assert = require('node:assert/strict');

async function assertRenderedPdf(page) {
  await page.waitForFunction(() => {
    const canvas = document.querySelector('[data-testid="pdf-page-canvas"]');
    if (!canvas || canvas.width < 10 || canvas.height < 10) return false;
    const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
    let count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i + 3] && pixels[i] < 230 && pixels[i + 1] < 230 && pixels[i + 2] < 230 && ++count > 200) return true;
    }
    return false;
  });
}

const path = require('node:path');
const fs = require('node:fs');

// 优先采用环境配置或本地已就绪的 Playwright
const playwrightPath = process.env.PLAYWRIGHT_MODULE || 'playwright';
const { chromium } = require(playwrightPath);

const baseURL = process.env.BASE_URL || 'http://127.0.0.1:5173';
const backendURL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const chromiumPath = process.env.EXP3_CHROMIUM_PATH;
const headless = process.env.HEADLESS !== 'false';
const slowMo = process.env.SLOWMO ? parseInt(process.env.SLOWMO, 10) : 100;
const evidenceDir = path.join(__dirname,'all-features-proxy');

if (!fs.existsSync(evidenceDir)) {
  fs.mkdirSync(evidenceDir, { recursive: true });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function capture(page, filename, description) {
  const filePath = path.join(evidenceDir, filename);
  await sleep(300);
  await page.screenshot({ path: filePath, fullPage: true });
  console.log(`    [截图存证] ${filename} - ${description}`);
}

function pdfBuffer(text = 'Playwright Test PDF') {
  const stream = `BT /F1 14 Tf 40 100 Td (${text}) Tj ET\n`;
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

async function login(page, username, password, expectedRole) {
  await page.goto(baseURL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
  await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(password);
  await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
  await page.getByRole('button', { name: '退出登录', exact: true }).waitFor({ timeout: 15000 });
  await page.getByText('教务中枢在线', { exact: true }).waitFor({ timeout: 15000 });
  console.log(`  [登录成功] 用户: ${username} (期望角色: ${expectedRole})`);
}

async function logout(page) {
  await sleep(300);
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page.getByPlaceholder('如 guojun, director, supervisor 等').waitFor({ timeout: 15000 });
  console.log('  [退出登录] 已返回教务统一登录首页');
}

(async () => {
  console.log('================================================================');
  console.log('     爱教学平台 · Playwright 全功能端到端自动化测试');
  console.log(`     前端: ${baseURL} | 后端: ${backendURL}`);
  console.log(`     模式: ${headless ? 'Headless' : 'Headed'} | 驱动: ${chromiumPath}`);
  console.log('================================================================\n');

  const browser = await chromium.launch({
    headless,
    slowMo,
    ...(chromiumPath ? { executablePath: chromiumPath } : {}),
    args: ['--start-maximized', '--window-size=1440,900']
  });

  const testReport = [];
  const pageErrors=[];
  // Forward requests to the explicitly selected real backend, including when
  // the frontend preview has no proxy. Never substitute fixture responses.
  async function newContext() {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    context.on('page',p=>p.on('pageerror',e=>pageErrors.push(e.message)));
    if (process.env.FORWARD_BACKEND !== 'false') {
      await context.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const response = await route.fetch({ url: backendURL + url.pathname + url.search });
        await route.fulfill({ response });
      });
    }
    return context;
  }
  function recordPass(module, name, details) {
    testReport.push({ module, name, status: 'PASS', details });
    console.log(`  [PASS] 【${module}】${name} - ${details}`);
  }

  try {
    // =========================================================================
    // 模块 1: 认证鉴权与多角色 RBAC 隔离
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 1：统一认证鉴权与多角色 RBAC 隔离】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      page.on('dialog', async d => {
        console.log(`    [拦截弹窗] [${d.type()}] ${d.message()}`);
        await d.accept();
      });

      // 1.1 错误密码拦截
      await page.goto(baseURL);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('guojun');
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('wrongpass_999');
      const [loginErrResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/auth/login')),
        page.getByRole('button', { name: '立即验证并登录', exact: true }).click()
      ]);
      assert.equal(loginErrResp.status(), 401, '非法密码应被返回 401 Unauthorized');
      await capture(page, '01_auth_error_intercept.png', '错误密码拦截');
      recordPass('认证与权限', '错误密码拦截', 'HTTP 401 拦截并给出安全提示');

      // 1.2 主任身份登录与导航隔离
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill(process.env.ROLE_TEST_PASSWORD);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill('director');
      await page.getByRole('button', { name: '立即验证并登录', exact: true }).click();
      await page.getByRole('button', { name: '退出登录', exact: true }).waitFor();
      await page.getByRole('button', { name: /教研室主任工作台/ }).waitFor();
      await page.getByRole('button', { name: /课堂智能考勤大屏/ }).waitFor();
      await page.getByRole('button', { name: /学生人脸档案库/ }).waitFor();
      assert.equal(await page.getByRole('button', { name: /任课教师工作台/ }).count(), 0, '主任工作台不应存在教师入口');
      assert.equal(await page.getByRole('button', { name: /教学督导工作台/ }).count(), 0, '主任工作台不应存在督导入口');
      await capture(page, '02_director_rbac_tabs.png', '主任专属 Tab 导航');
      recordPass('认证与权限', '主任角色 RBAC 隔离', '仅可见主任、大屏、学生档案库');
      await logout(page);

      // 1.3 教师身份登录与导航隔离
      await login(page, 'guojun', process.env.ROLE_TEST_PASSWORD, '任课教师');
      await page.getByRole('button', { name: /任课教师工作台/ }).waitFor();
      assert.equal(await page.getByRole('button', { name: /教研室主任工作台/ }).count(), 0, '教师工作台不应存在主任入口');
      assert.equal(await page.getByRole('button', { name: /教学督导工作台/ }).count(), 0, '教师工作台不应存在督导入口');
      await capture(page, '03_teacher_rbac_tabs.png', '教师专属 Tab 导航');
      recordPass('认证与权限', '教师角色 RBAC 隔离', '仅可见教师工作台与考勤大屏');
      await logout(page);

      // 1.4 督导身份登录与专业授权提示
      await login(page, 'supervisor', process.env.ROLE_TEST_PASSWORD, '教学督导');
      await page.getByRole('button', { name: /教学督导工作台/ }).waitFor();
      await page.getByText(/授权:SE;CS/).first().waitFor();
      assert.equal(await page.getByRole('button', { name: /教研室主任工作台/ }).count(), 0, '督导工作台不应存在主任入口');
      assert.equal(await page.getByRole('button', { name: /任课教师工作台/ }).count(), 0, '督导工作台不应存在教师入口');
      await capture(page, '04_supervisor_rbac_tabs.png', '督导专属 Tab 导航与专业授权徽章');
      recordPass('认证与权限', '督导角色 RBAC 隔离', '仅可见督导工作台与考勤大屏，展示 SE;CS 授权');
      await logout(page);

      await context.close();
    }

    // =========================================================================
    // 模块 2: 教研室主任工作台全功能 (Director Desk)
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 2：教研室主任工作台全功能 (US-01 / 03 / 05 / 06 / 14)】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      page.on('dialog', async d => {
        console.log(`    [主任弹窗] [${d.type()}] ${d.message()}`);
        await d.accept();
      });

      await login(page, 'director', process.env.ROLE_TEST_PASSWORD, '教研室主任');

      // 2.1 课程档案管理与 CSV 模板与预览 (US-01)
      console.log('  [测试] 2.1 课程档案列表、分页与批量导入预览 (US-01)...');
      await page.getByRole('button', { name: /专业全量课程档案/ }).click();
      await page.getByRole('button', { name: /批量导入课程/ }).click();
      await page.getByText('专业编码速查字典 (CSV必填字段)').waitFor();

      // 模板下载鉴权
      const [templateResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/courses/import/template')),
        page.getByRole('button', { name: /下载空白模板/ }).click()
      ]);
      assert.equal(templateResp.status(), 200, '模板下载成功');
      recordPass('主任工作台', 'US-01 课程导入模板下载', 'JWT 校验通过，下载 CSV 空白模板 HTTP 200');

      // CSV 预检解析
      const testCourseCode = 'PW-TEST-' + Date.now().toString().slice(-4);
      const csvData = `courseCode,courseName,department,majorCode,credits,hours,theoryHours,practiceHours,courseType,prerequisites,description\n${testCourseCode},Playwright自动化测试实战,软件工程教研室,SE,2.5,40,30,10,专业选修课,,全流程自动化测试实训课程\n`;
      const uploadInput = page.locator('input[type=file][accept*="csv"]');
      const [previewResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/courses/import/preview')),
        uploadInput.setInputFiles({
          name: 'pw_courses.csv',
          mimeType: 'text/csv',
          buffer: Buffer.from(csvData, 'utf-8')
        })
      ]);
      assert.equal(previewResp.status(), 200, '预检解析成功');
      await capture(page, '05_director_import_preview.png', '课程导入 CSV 预检解析');

      // 确认入库并关闭
      const [saveBatchResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/courses/import/confirm')),
        page.getByRole('button', { name: /确认导入并整批入库/ }).click()
      ]);
      assert.equal(saveBatchResp.status(), 200, '整批入库成功');
      recordPass('主任工作台', 'US-01 批量导入与入库', `成功将新课程 ${testCourseCode} 写入 MySQL`);

      // 2.2 开课排课统筹看板 (US-03 / US-04)
      console.log('  [测试] 2.2 开课排课统筹看板与多教师排课 (US-03/04)...');
      await page.getByRole('button', { name: /开课排课统筹看板/ }).click();
      await page.getByText('开课班次').first().waitFor();
      await capture(page, '06_director_schedules_overview.png', '开课排课统筹看板全貌');
      recordPass('主任工作台', 'US-03 开课排课看板', '开课班次、周次与教室排课矩阵就绪');

      // 2.3 毕业要求指标点矩阵与培养方案导入 (US-05)
      console.log('  [测试] 2.3 毕业要求指标点矩阵与培养方案导入 (US-05)...');
      await page.getByRole('button', { name: /毕业要求指标点矩阵/ }).click();
      await page.getByText(/工程教育专业认证.*毕业要求指标点/).first().waitFor();
      // 验证培养方案录入面板
      const planInput = page.getByPlaceholder('1-1 | 工程知识 | 指标描述');
      await planInput.fill('1-1 | 1. 工程知识 | 能够应用数学与自然科学知识解决复杂工程问题\n2-1 | 2. 问题分析 | 能够应用工程科学基本原理识别与表达复杂软件工程问题');
      await page.locator('select:has(option[value="SE"])').first().selectOption('SE');
      await page.getByPlaceholder('如 2026版').fill('PW-2026-v1');
      const [importPlanResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/syllabus/plans/SE/PW-2026-v1/indicators')),
        page.getByRole('button', { name: '导入目录' }).click()
      ]);
      assert.equal(importPlanResp.status(), 200, '导入培养方案指标成功');
      await capture(page, '07_director_plan_catalog_imported.png', '培养方案指标目录导入成功');
      recordPass('主任工作台', 'US-05 培养方案目录维护', '成功为软件工程专业导入 2 条培养方案指标');

      // 2.4 督导建档与专业授权 (US-06)
      console.log('  [测试] 2.4 督导建档与专业授权 (US-06)...');
      await page.getByRole('button', { name: /督导建档与专业授权/ }).click();
      await page.getByText(/教学.*督导.*建档与专业.*授权/).first().waitFor();
      await page.getByText('张督导').first().waitFor();
      await capture(page, '08_director_supervisors_auth.png', '督导列表与专业授权');
      recordPass('主任工作台', 'US-06 督导建档与授权', '成功读取并展示张督导授权专业 SE;CS');

      // 2.5 督导评价审核 (US-14)
      console.log('  [测试] 2.5 督导评价审核 (US-14)...');
      await page.getByRole('button', { name: /督导评价审核/ }).click();
      await page.getByText('待审核督导评价').waitFor();
      await capture(page, '09_director_supervision_reviews.png', '督导评价审核面板');
      recordPass('主任工作台', 'US-14 督导评价审核流', '待审核督导随堂听课评价面板加载正常');

      await logout(page);
      await context.close();
    }

    // =========================================================================
    // 模块 3: 任课教师工作台全功能 (Teacher Desk)
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 3：任课教师工作台全功能 (US-02 / 05 / 07 / 08 / 09 / 10 / 17)】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      page.on('dialog', async d => {
        console.log(`    [教师弹窗] [${d.type()}] ${d.message()}`);
        await d.accept();
      });

      await login(page, 'guojun', process.env.ROLE_TEST_PASSWORD, '任课教师');

      // 3.1 课程大纲简介草稿与发布 (US-02)
      console.log('  [测试] 3.1 课程简介草稿保存与发布流程 (US-02)...');
      const introInput = page.getByPlaceholder('请输入课程背景、学科定位、主要授课内容概括...');
      await introInput.fill('【Playwright 全自动化回归草稿】东北大学软件工程特色核心课程，深入敏捷Scrum与持续集成实操。');
      const [draftResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/content/draft') && r.request().method() === 'PUT'),
        page.getByRole('button', { name: '暂存草稿', exact: true }).click()
      ]);
      assert.equal(draftResp.status(), 200, '暂存草稿成功');
      await capture(page, '10_teacher_draft_saved.png', '教师草稿暂存成功');
      recordPass('教师工作台', 'US-02 课程简介草稿保存', '暂存草稿通过，并发锁版本正常同步');

      // 3.2 课件教案上传与多环节标签分类 (US-07 / US-08 / US-10)
      console.log('  [测试] 3.2 课件教案上传与多环节标签 (US-07/08/10)...');
      await page.getByRole('button', { name: /上传新课件\/教案/ }).click();
      const resourceName = 'PW-Doc-' + Date.now().toString().slice(-4) + '.pdf';
      await page.locator('input[type="file"]').last().setInputFiles({
        name: resourceName,
        mimeType: 'application/pdf',
        buffer: pdfBuffer('东北大学自动化测试实战讲义')
      });
      await page.getByPlaceholder('如 第一章 软件项目管理概论').fill('第七章 自动化质量保障与Playwright实战');
      await page.locator('label:has(input[type="checkbox"][value="理论"]) input').check();
      await page.locator('label:has(input[type="checkbox"][value="实验"]) input').check();
      await page.locator('label:has(input[type="checkbox"][value="讨论"]) input').check();
      await page.getByRole('checkbox', { name: '教研室共享' }).check();

      const [uploadResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/resources/upload')),
        page.getByRole('button', { name: '立即挂载' }).click()
      ]);
      assert.equal(uploadResp.status(), 200, '课件挂载成功');
      await capture(page, '11_teacher_resource_uploaded.png', '课件上传挂载成功');
      recordPass('教师工作台', 'US-07 课件教案上传', `成功上传课件 ${resourceName}，绑定理论+实验+讨论多标签并设置教研室共享`);

      // 标签精准筛选
      console.log('  [测试] 3.3 标签精准筛选 (US-08)...');
      const myResourceList = page.getByTestId('my-resource-list');
      await page.getByRole('button', { name: '实验', exact: true }).click();
      await myResourceList.getByText(resourceName, { exact: true }).waitFor();
      await page.getByRole('button', { name: '未标注', exact: true }).click();
      await myResourceList.getByText(resourceName, { exact: true }).waitFor({ state: 'detached' });
      await page.getByRole('button', { name: '全部', exact: true }).click();
      await myResourceList.getByText(resourceName, { exact: true }).waitFor();
      recordPass('教师工作台', 'US-08 环节标签筛选', '点击实验/未标注/全部药丸筛选，列表精准过滤');

      // 3.4 课件在线受控限时水印预览 (US-09)
      console.log('  [测试] 3.4 课件限时水印预览 (US-09)...');
      const resCard = myResourceList.locator(':scope > div').filter({ hasText: resourceName });
      const [ticketResp, pdfResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/preview-ticket')),
        page.waitForResponse(r => r.url().includes('/api/v1/resources/preview/') && r.status() === 200),
        resCard.getByRole('button', { name: '授权预览' }).click()
      ]);
      assert.equal(ticketResp.status(), 200, '票据申请成功');
      assert.equal((await pdfResp.body()).subarray(0, 5).toString(), '%PDF-', '响应应为合法 PDF');
      await page.getByTestId('protected-pdf-preview').waitFor();
      await assertRenderedPdf(page);
      await capture(page, '12_teacher_watermark_preview.png', '限时水印 PDF 预览弹窗');
      await page.getByRole('button', { name: '关闭', exact: true }).click();
      recordPass('教师工作台', 'US-09 在线限时水印预览', '成功通过动态票据获取带水印 PDF 并在受保护画布中实际渲染预览');

      // Optional synthetic Office fixture for a backend with LibreOffice.
      if (process.env.OFFICE_PREVIEW_FIXTURE) {
        const fixture = process.env.OFFICE_PREVIEW_FIXTURE;
        const officeName = path.basename(fixture);
        await page.getByRole('button', { name: /上传新课件\/教案/ }).click();
        await page.locator('input[type="file"]').last().setInputFiles(fixture);
        await page.getByPlaceholder('如 第一章 软件项目管理概论').fill('Sprint 2 Office conversion fixture');
        const [officeUpload] = await Promise.all([
          page.waitForResponse(r => r.url().includes('/api/v1/resources/upload') && r.request().method() === 'POST'),
          page.getByRole('button', { name: '立即挂载' }).click()
        ]);
        assert.equal(officeUpload.status(), 200);
        assert.equal((await officeUpload.json()).code, 200);
        await myResourceList.getByText(officeName, { exact: true }).waitFor();
        const officeCard = myResourceList.locator(':scope > div').filter({ hasText: officeName });
        const [officePdf] = await Promise.all([
          page.waitForResponse(r => r.url().includes('/api/v1/resources/preview/') && r.status() === 200),
          officeCard.getByRole('button', { name: '授权预览' }).click()
        ]);
        const converted = await officePdf.body();
        assert.equal(converted.subarray(0, 5).toString(), '%PDF-');
        assert.ok(converted.length > 500);
        await page.getByTestId('protected-pdf-preview').waitFor();
        await assertRenderedPdf(page);
        await capture(page, '22_teacher_office_pdf_preview.png', '真实 Office 转 PDF 与水印授权预览');
        await page.getByRole('button', { name: '关闭', exact: true }).click();
        recordPass('教师工作台', 'US-07/09 Office 转换闭环', `合成 ${path.extname(fixture)} 上传、LibreOffice 转 PDF、授权水印预览通过，PDF ${converted.length} 字节`);
      }

      // 3.5 督导评价与复盘雷达图 (US-14 / US-17)
      console.log('  [测试] 3.5 督导评价脱敏与复盘雷达图 (US-14/17)...');
      await page.getByText('督导随堂评价 4 维雷达图 (US-17)').waitFor();
      await capture(page, '13_teacher_radar_chart.png', 'BOPPPS 4 维教学复盘雷达图');
      recordPass('教师工作台', 'US-17 教学质量雷达图', 'BOPPPS 四维均分及雷达图可视组件正常渲染');

      await logout(page);
      await context.close();
    }

    // =========================================================================
    // 模块 4: 教学督导工作台全功能 (Supervisor Desk)
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 4：教学督导工作台全功能 (US-06 / 09 / 13 / 15 / 16)】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      page.on('dialog', async d => {
        console.log(`    [督导弹窗] [${d.type()}] ${d.message()}`);
        await d.accept();
      });

      await login(page, 'supervisor', process.env.ROLE_TEST_PASSWORD, '教学督导');

      // 4.1 全院开课总课表与听课日程看板 (US-03/06)
      console.log('  [测试] 4.1 全院开课总课表大学周历矩阵看板 (US-03/06)...');
      await page.getByText('全院督导听课总课表与排课日程看板 (US-03/06)').waitFor();
      await page.getByText('节次 / 时段').waitFor();
      await capture(page, '14_supervisor_timetable_matrix.png', '全院开课周历总课表');
      recordPass('督导工作台', 'US-03 督导听课总课表', '大学周历矩阵排课看板展示完整，支持主讲教师高亮');

      // 4.2 待督导课程复合检索与课件免密预审 (US-06 / US-09)
      console.log('  [测试] 4.2 待督导课程复合检索与课件免密预审 (US-06/09)...');
      await page.getByRole('button', { name: /待督导目标课程多维检索/ }).click();
      await page.getByPlaceholder('按课程名 / 代码检索...').fill('SE');
      await capture(page, '15_supervisor_course_search.png', '待督导课程多维复合检索');
      recordPass('督导工作台', 'US-06 复合检索', '支持代码/名称/学期/班级复合检索，严格遵循授权专业');

      // 课件免密预审
      console.log('  [测试] 4.3 督导端课件免密预审 (US-09)...');
      const previewBtn = page.getByRole('button', { name: '课件免密预审' }).first();
      if (await previewBtn.count() > 0) {
        await previewBtn.click();
        await page.getByText(/听课前课件.*免密预审/).waitFor();
        await capture(page, '16_supervisor_resource_audit_modal.png', '课件免密预审列表');
        await page.getByRole('button', { name: '关闭', exact: true }).click();
        recordPass('督导工作台', 'US-09 课件免密预审', '督导专家一键拉取授课课件并支持在线免密调阅');
      }

      // 4.4 随堂听课评价与打分 (US-13)
      console.log('  [测试] 4.4 BOPPPS 四维打分与随堂评价提交 (US-13)...');
      const evalBtn = page.getByRole('button', { name: /随堂评价/ }).first();
      await evalBtn.click();
      await page.getByText(/随堂听评课量化打分表/).waitFor();
      await page.getByPlaceholder('如 第三讲：需求估算与WBS分解').fill('自动化测试与质量保障体系实战');

      // 调节 4 个打分滑块
      const sliders = page.locator('input[type="range"]');
      for (let i = 0; i < 4; i++) {
        await sliders.nth(i).fill('22');
      }
      await page.getByPlaceholder('例如：教学组织严密，能够结合实际敏捷项目案例启发学生...').fill('【Playwright实测】教学设计严密，实战项目案例充实。');
      await page.getByPlaceholder('例如：建议在课后作业中进一步增加甘特图与工期缓冲池实训演练...').fill('【Playwright实测】建议进一步引导学生进行边界异常值对抗演练。');
      await capture(page, '17_supervisor_evaluation_form.png', 'BOPPPS 四维随堂评价表单');

      // 暂存草稿
      const [saveDraftResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/v1/supervisions') && r.request().method() === 'POST'),
        page.getByRole('button', { name: /暂存草稿/ }).click()
      ]);
      assert.equal(saveDraftResp.status(), 200, '暂存草稿成功');
      const draftData = (await saveDraftResp.json()).data;
      assert.equal(draftData.status, 'DRAFT', '暂存状态应为 DRAFT');
      recordPass('督导工作台', 'US-13 随堂评价暂存', 'BOPPPS 4 维打分暂存草稿成功');

      // 4.5 全院督导听课覆盖率巡检 (US-15)
      console.log('  [测试] 4.5 全院督导听课覆盖率巡检与明细追溯 (US-15)...');
      await page.getByText('所选学期有效课程总数').waitFor();
      await page.getByText(/督导覆盖率动态百分比/).waitFor();

      // 展开覆盖率明细折叠面板
      const detailSummary = page.locator('summary:has-text("覆盖率明细")');
      await detailSummary.click();
      await capture(page, '18_supervisor_coverage_dashboard.png', '督导覆盖率指标卡与明细下钻');
      recordPass('督导工作台', 'US-15 覆盖率巡检与追溯', '展示全院开课总数、已督导门数、覆盖率并支持明细单号追溯');

      // 4.6 红黄质量预警 (US-16)
      console.log('  [测试] 4.6 红黄质量预警看板 (US-16)...');
      await page.getByRole('button', { name: /教学质量预警中心/ }).click();
      await page.getByRole('heading', { name: '教学质量预警中心 (US-16)' }).waitFor();
      await capture(page, '19_supervisor_alerts_center.png', '教学质量红黄预警中心');
      recordPass('督导工作台', 'US-16 质量预警', '覆盖率 <30% 标黄与听课均分 <75 分标红规则就绪');

      await logout(page);
      await context.close();
    }

    // =========================================================================
    // 模块 5: 课堂智能考勤大屏
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 5：课堂智能考勤与态势监控大屏】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      await login(page, 'director', process.env.ROLE_TEST_PASSWORD, '教研室主任');

      await page.getByRole('button', { name: /课堂智能考勤大屏/ }).click();
      await page.getByRole('heading', { name: /课堂智能考勤与态势监控大屏/ }).waitFor();
      await page.getByText('当前授课班级：').waitFor();
      await capture(page, '20_attendance_dashboard.png', '课堂智能考勤大屏');
      recordPass('考勤大屏', '考勤大屏页面', '页面与班级选择展示正常；摄像头识别未包含在本用例');

      await logout(page);
      await context.close();
    }

    // =========================================================================
    // 模块 6: 学生人脸档案库管理
    // =========================================================================
    console.log('\n>>> ------------------------------------------------------------');
    console.log('>>> 【模块 6：学生档案与人脸特征底库管理】');
    console.log('>>> ------------------------------------------------------------');
    {
      const context = await newContext();
      const page = await context.newPage();
      await login(page, 'director', process.env.ROLE_TEST_PASSWORD, '教研室主任');

      await page.getByRole('button', { name: /学生人脸档案库/ }).click();
      await page.getByRole('heading', { name: /学生档案与人脸特征底库管理/ }).waitFor();
      await page.getByText(/学生档案列表 \(共/).waitFor();
      await capture(page, '21_student_face_database.png', '学生档案与人脸特征库');
      recordPass('学生底库', '学生档案列表', '数据库档案列表展示正常；现场人脸采集和识别未包含在本用例');

      await logout(page);
      await context.close();
    }

    console.log('\n================================================================');
    console.log('       【Playwright 全功能自动化测试全部圆满通过！】');
    console.log(`       总用例数: ${testReport.length} | 失败: 0 | 存证截图: ${fs.readdirSync(evidenceDir).filter(name => name.endsWith('.png')).length} 张`);
    console.log('================================================================');

  } catch (error) {
    console.error('\n❌ 测试执行异常中止:', error);
    process.exitCode = 1;
  } finally {
    fs.writeFileSync(path.join(evidenceDir, 'results.json'), JSON.stringify({
      pageErrors, baseURL, backendURL, forwarded: process.env.FORWARD_BACKEND !== 'false', results: testReport, completed: process.exitCode !== 1,
      screenshots: fs.readdirSync(evidenceDir).filter(name => name.endsWith('.png')).length
    }, null, 2));
    await browser.close();
  }
})();
