// Sprint 2 acceptance additions: real files, rendered pixels, and server-side guards.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const frontend = process.env.EXP3_FRONTEND_URL || 'http://127.0.0.1:5173';
const backend = process.env.EXP3_BACKEND_URL || 'http://127.0.0.1:18081';
const evidence = path.resolve(process.env.FIX_EXP3_EVIDENCE_DIR || path.join(__dirname,'../../docs/fix-exp3-acceptance'));
const fixtures = path.join(__dirname,'fixtures');
const results = [];
fs.mkdirSync(evidence,{recursive:true});
async function rendered(page) {
  await page.waitForFunction(() => {
    const c = document.querySelector('[data-testid="pdf-page-canvas"]');
    if (!c || c.width < 20 || c.height < 20) return false;
    const data = c.getContext('2d').getImageData(0,0,c.width,c.height).data;
    let ink=0;
    for (let i=0;i<data.length;i+=4) if (data[i+3] && data[i]<230 && data[i+1]<230 && data[i+2]<230 && ++ink>200) return true;
    return false;
  });
  await page.getByRole('status').filter({hasText:'正在加载课件'}).waitFor({state:'detached'});
}
async function protectedViewer(page) {
  const viewer=page.getByTestId('protected-pdf-preview');
  await rendered(page);
  assert.equal(await viewer.locator('iframe,embed,a').count(),0);
  const guards=await viewer.evaluate(el => {
    const right=new MouseEvent('contextmenu',{bubbles:true,cancelable:true});el.dispatchEvent(right);
    const copy=new Event('copy',{bubbles:true,cancelable:true});el.dispatchEvent(copy);
    const save=new KeyboardEvent('keydown',{key:'s',ctrlKey:true,bubbles:true,cancelable:true});el.dispatchEvent(save);
    const print=new KeyboardEvent('keydown',{key:'p',ctrlKey:true,bubbles:true,cancelable:true});el.dispatchEvent(print);
    return [right.defaultPrevented,copy.defaultPrevented,save.defaultPrevented,print.defaultPrevented];
  });
  assert.deepEqual(guards,[true,true,true,true]);
  assert.equal(await page.getByRole('button',{name:'鉴权下载原件',exact:true}).count(),0);
  return viewer;
}
(async () => {
  const browser=await chromium.launch({headless:true,...(process.env.EXP3_CHROMIUM_PATH ? {executablePath:process.env.EXP3_CHROMIUM_PATH}:{})});
  try {
    async function login(username) {
      const c=await browser.newContext({viewport:{width:1440,height:1000}});
      const page=await c.newPage();page.on('dialog',d=>{console.log('DIALOG',username,d.message());return d.accept();});
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('123456');
      await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
      await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
      return page;
    }
    async function api(page,route,method='GET',options={}) {
      const jwt=await page.evaluate(()=>localStorage.getItem('jwtToken'));
      return page.request.fetch(backend+route,{method,headers:{Authorization:`Bearer ${jwt}`},...options});
    }
    const teacher=await login('guojun');const director=await login('director');const supervisor=await login('supervisor');
    const teacherOfferings=(await (await api(teacher,'/api/v1/courses/offerings')).json()).data;
    const common=(await (await api(supervisor,'/api/v1/courses/offerings')).json()).data.find(o=>teacherOfferings.some(t=>t.id===o.id));
    assert.ok(common);
    const selector=teacher.locator('select:has(option[value="'+common.id+'"])').first();
    await selector.selectOption(String(common.id));
    let docxResource;
    for (const extension of ['docx','pptx']) {
      const unique=`FIX-EXP3-${Date.now()}.${extension}`;
      await teacher.getByRole('button',{name:/上传新课件\/教案/}).click();
      await teacher.locator('input[type="file"]').last().setInputFiles(path.join(fixtures,'sprint2.'+extension));
      await teacher.getByPlaceholder('如 第1讲-需求估算与甘特图.pptx').fill(unique);
      await teacher.getByPlaceholder('如 第一章 软件项目管理概论').fill('Sprint2 验收章节');
      const [upload]=await Promise.all([
        teacher.waitForResponse(r=>r.url().includes('/api/v1/resources/upload') && r.request().method()==='POST'),
        teacher.getByRole('button',{name:'立即挂载'}).click()
      ]);
      assert.equal((await upload.json()).code,200,(await upload.json()).message);const resource=(await upload.json()).data;
      assert.ok(resource.version && resource.createdAt && resource.fileSizeBytes>0);
      const card=teacher.getByTestId('my-resource-list').locator(':scope > div').filter({hasText:unique});
      const [response]=await Promise.all([
        teacher.waitForResponse(r=>r.url().includes('/api/v1/resources/preview/')),
        card.getByRole('button',{name:'授权预览'}).click()
      ]);
      assert.equal(response.status(),200);const pdf=await response.body();assert.equal(pdf.subarray(0,5).toString(),'%PDF-');
      fs.writeFileSync(path.join(evidence,`protected-${extension}.pdf`),pdf);
      const viewer=await protectedViewer(teacher);
      if (extension==='docx') {
        docxResource=resource;
        assert.match(await viewer.getByTestId('pdf-page-counter').innerText(),/1 \/ 2/);
        const first=await viewer.getByTestId('pdf-page-canvas').evaluate(c=>c.toDataURL());
        await viewer.getByRole('button',{name:'下一页',exact:true}).click();await rendered(teacher);
        assert.match(await viewer.getByTestId('pdf-page-counter').innerText(),/2 \/ 2/);
        assert.notEqual(await viewer.getByTestId('pdf-page-canvas').evaluate(c=>c.toDataURL()),first);
        await viewer.getByRole('button',{name:'上一页',exact:true}).click();await rendered(teacher);
        assert.match(await viewer.getByTestId('pdf-page-counter').innerText(),/1 \/ 2/);
      }
      await teacher.screenshot({path:path.join(evidence,`protected-${extension}.png`)});
      results.push({story:'US07/09',format:extension,result:'PASS',realPdfBytes:pdf.length,renderedPixels:true,guards:true});
      console.log(`PASS ${extension.toUpperCase()}: real upload/conversion, rendered canvas, protection${extension==='docx'?', two-page navigation':''}`);
      await teacher.getByRole('button',{name:'关闭',exact:true}).click();
    }
    const deniedDownload=await api(supervisor,`/api/v1/resources/${docxResource.id}/download`);
    assert.equal(deniedDownload.status(),403);
    await supervisor.getByRole('button',{name:/待督导目标课程多维检索/}).click();
    await supervisor.getByPlaceholder('按课程名 / 代码检索...').fill(common.course.courseCode);
    await supervisor.getByRole('combobox',{name:'检索学期'}).selectOption(common.academicTerm);
    await supervisor.locator('select:has(option[value="'+common.className+'"])').selectOption(common.className);
    await supervisor.getByTestId(`offering-${common.id}`).getByRole('button',{name:'课件免密预审'}).click();
    const supervisorCard=supervisor.locator('div.p-3.bg-slate-50.border.rounded-xl').filter({hasText:docxResource.resourceName});
    await supervisorCard.getByRole('button',{name:'打开限时水印预览'}).click();
    await protectedViewer(supervisor);
    await supervisor.screenshot({path:path.join(evidence,'protected-supervisor.png')});
    await supervisor.getByRole('button',{name:'关闭',exact:true}).click();
    results.push({story:'US09',role:'supervisor',result:'PASS',originalDownload:403});
    const corrupt=await (await api(teacher,'/api/v1/resources/upload','POST',{multipart:{
      file:{name:'corrupt.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:Buffer.from([0,1,2,3])},
      courseId:String(common.course.id),chapter:'损坏格式验收',resourceName:'Synthetic corrupt document'}})).json();
    assert.equal(corrupt.code,200);
    const ticket=await (await api(teacher,`/api/v1/resources/${corrupt.data.id}/preview-ticket`,'POST')).json();
    assert.equal((await api(teacher,ticket.data.url)).status(),422);
    results.push({story:'US07',case:'corrupt DOCX with working converter',result:'PASS'});
    const oversize=await (await api(teacher,'/api/v1/resources/upload','POST',{timeout:120000,multipart:{
      file:{name:'oversize.pdf',mimeType:'application/pdf',buffer:Buffer.alloc(104857601)},courseId:String(common.course.id),chapter:'限制验收'}})).json();
    assert.equal(oversize.code,400);assert.match(oversize.message,/100MB/);
    console.log('PASS US07: corrupt DOCX rejected with working LibreOffice, real >100MB upload rejected');
    const version='FIX-TEMPLATE-'+Date.now();
    await teacher.getByRole('textbox',{name:'新大纲版本'}).fill(version);
    const [templated]=await Promise.all([
      teacher.waitForResponse(r=>r.url().includes('/from-plan') && r.request().method()==='POST'),
      teacher.getByRole('button',{name:'套用推荐示例模板（12类）',exact:true}).click()
    ]);
    const syllabus=(await templated.json()).data;assert.ok(syllabus.id);
    assert.equal(syllabus.planVersion,'RECOMMENDED-12');
    await teacher.getByText(/当前为推荐示例草案/).waitFor();
    const indicators=(await (await api(teacher,`/api/v1/syllabus/course/${common.course.id}/indicators`)).json()).data;
    assert.equal(indicators.length,12);
    const first=indicators[0];
    const edited=await (await api(teacher,`/api/v1/syllabus/indicators/${first.id}`,'PUT',{data:{supportWeight:'H',targetGoal:'目标1'}})).json();
    assert.equal(edited.data.supportWeight,'H');assert.equal(edited.data.targetGoal,'目标1');
    assert.equal((await (await api(teacher,`/api/v1/syllabus/indicators/${first.id}`,'PUT',{data:{supportWeight:'X'}})).json()).code,400);
    assert.equal((await (await api(teacher,`/api/v1/syllabus/indicators/${first.id}`,'DELETE')).json()).code,200);
    const add=await (await api(teacher,`/api/v1/syllabus/course/${common.course.id}/indicators`,'POST',{data:{
      indicatorCode:'11-2',requirementCategory:'项目管理',indicatorDescription:'按实际课程修订',supportWeight:'L',targetGoal:'目标2'}})).json();
    assert.equal(add.code,200);assert.equal(add.data.indicatorCode,'11-2');
    assert.equal((await (await api(teacher,`/api/v1/syllabus/course/${common.course.id}/indicators`,'POST',
      {data:{indicatorCode:'13-1',requirementCategory:'无效类别',supportWeight:'M'}})).json()).code,400);
    assert.equal((await (await api(director,`/api/v1/syllabus/${syllabus.id}/lock?lockedBy=fixture`,'POST')).json()).data.status,'LOCKED');
    assert.equal((await (await api(teacher,`/api/v1/syllabus/indicators/${add.data.id}`,'PUT',{data:{supportWeight:'M'}})).json()).code,409);
    const reserved=await (await api(director,`/api/v1/syllabus/plans/SE/RECOMMENDED-12/indicators`,'PUT',{data:[{}]})).json();assert.equal(reserved.code,400);
    results.push({story:'US05',result:'PASS',templateItems:12,weights:['H','L'],crud:true,lockedEdit:409});
    console.log('PASS US05: real template button, 12 draft items, goal/weight CRUD, invalid weight, director lock, reserved plan separation');
    const evaluationData={offeringId:common.id,listenTopic:'FIX-EXP3听课',scoreAttitude:20,scoreContent:20,scoreMethod:20,scoreEffect:20,
      suggestions:'BOPPPS后测环节增加练习',isDraft:false};
    for (const highlights of ['只有一条','第一条\n第二条','1. 相同内容\n2. 相同内容\n3. 相同内容','1. \n2. \n3. ']) {
      const rejected=await (await api(supervisor,'/api/v1/supervisions','POST',{data:{...evaluationData,highlights}})).json();assert.equal(rejected.code,400);
    }
    const draft=await (await api(supervisor,'/api/v1/supervisions','POST',{data:{...evaluationData,highlights:'一条尚未写完',isDraft:true}})).json();
    assert.equal(draft.data.status,'DRAFT');
    await supervisor.getByTestId(`offering-${common.id}`).getByRole('button',{name:'随堂评价 (US-13)',exact:true}).click();
    await supervisor.getByText('已恢复暂存评价，可继续编辑后提交。').waitFor();
    const highlightInput=supervisor.getByPlaceholder('例如：教学组织严密，能够结合实际敏捷项目案例启发学生...');
    assert.equal(await highlightInput.inputValue(),'一条尚未写完');
    await highlightInput.fill('1. 组织有序\n2. 案例充分\n3. 互动及时');
    const [submission]=await Promise.all([
      supervisor.waitForResponse(r=>r.url().endsWith('/api/v1/supervisions') && r.request().method()==='POST'),
      supervisor.getByRole('button',{name:'正式提交（待审核）',exact:true}).click()
    ]);
    const submitted=await submission.json();
    assert.equal(submitted.data.id,draft.data.id);
    assert.equal(submitted.data.status,'PENDING_REVIEW');assert.equal(submitted.data.totalScore,80);
    results.push({story:'US13/14',result:'PASS',insufficientOrDuplicate:400,draft:'DRAFT',submitted:'PENDING_REVIEW'});
    console.log('PASS US13/14: short/empty/duplicate highlights rejected, incomplete draft allowed, three distinct highlights submitted');
  } finally {
    fs.writeFileSync(path.join(evidence,'fix-acceptance-results.json'),JSON.stringify(results,null,2));
    await browser.close();
  }
})().catch(e=>{console.error(e);process.exitCode=1;});
