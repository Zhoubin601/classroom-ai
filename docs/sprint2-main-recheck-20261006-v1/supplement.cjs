// Read-only code audit plus synthetic writes only to this run's disposable database.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = __dirname;
const frontend = 'http://127.0.0.1:15173';
const backend = 'http://127.0.0.1:18081';
const results = process.env.RECHECK_EVALUATION_ONLY ? JSON.parse(fs.readFileSync(path.join(root,'supplement-results.json'),'utf8')) : [];
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:'/usr/bin/chromium'});
  try {
    async function login(username) {
      const context = await browser.newContext({viewport:{width:1440,height:1000}});
      const page = await context.newPage();
      page.on('dialog', d => d.accept());
      await page.goto(frontend);
      await page.getByPlaceholder('如 guojun, director, supervisor 等').fill(username);
      await page.getByPlaceholder('请输入登录密码 (默认 123456)').fill('123456');
      await page.getByRole('button',{name:'立即验证并登录',exact:true}).click();
      await page.getByRole('button',{name:'退出登录',exact:true}).waitFor();
      await page.getByText('教务中枢在线',{exact:true}).waitFor();
      return page;
    }
    async function api(page, route, method='GET', options={}) {
      const jwt = await page.evaluate(() => localStorage.getItem('jwtToken'));
      return page.request.fetch(backend+route,{method,headers:{Authorization:`Bearer ${jwt}`},...options});
    }
    const teacher = await login('guojun');
    const director = await login('director');
    const supervisor = await login('supervisor');
    const course = (await (await api(teacher,'/api/v1/courses')).json()).data[0];
    if (!process.env.RECHECK_EVALUATION_ONLY) {
    // Browser file chooser -> actual Vite proxy -> backend -> LibreOffice -> PDF.
    for (const extension of ['docx','pptx']) {
      const filename = `sprint2.${extension}`;
      await teacher.getByRole('button',{name:/上传新课件\/教案/}).click();
      await teacher.locator('input[type="file"]').last().setInputFiles(path.join(root,'fixtures',filename));
      await teacher.getByPlaceholder('如 第一章 软件项目管理概论').fill('Sprint2 synthetic chapter');
      const uniqueName=`Supplement-${Date.now()}-${filename}`;
      await teacher.getByPlaceholder('如 第1讲-需求估算与甘特图.pptx').fill(uniqueName);
      const [uploaded] = await Promise.all([
        teacher.waitForResponse(r => r.url().includes('/api/v1/resources/upload') && r.request().method()==='POST'),
        teacher.getByRole('button',{name:'立即挂载'}).click()
      ]);
      const resource = (await uploaded.json()).data;
      assert.equal(uploaded.status(),200); assert.ok(resource.id);
      assert.equal(resource.fileType,extension.toUpperCase());
      assert.ok(resource.version && resource.fileSizeBytes>0 && resource.createdAt);
      const card = teacher.getByTestId('my-resource-list').locator(':scope > div').filter({hasText:uniqueName});
      await card.waitFor();
      const [response] = await Promise.all([
        teacher.waitForResponse(r => r.url().includes('/api/v1/resources/preview/')),
        card.getByRole('button',{name:'授权预览'}).click()
      ]);
      assert.equal(response.status(),200,'Office preview must return a successful PDF response');
      const pdf = await response.body(); assert.equal(pdf.subarray(0,5).toString(),'%PDF-');
      const pdfPath=path.join(root,`converted-${extension}.pdf`); fs.writeFileSync(pdfPath,pdf);
      const text=execFileSync('pdftotext',[pdfPath,'-'],{encoding:'utf8'});
      assert.match(text,/Sprint2/); assert.match(text,/PREVIEW guojun/);
      await teacher.getByTitle('课件 PDF 预览').waitFor();
      const contextMenuPrevented = await teacher.getByTitle('课件 PDF 预览').evaluate(element => {
        const event = new MouseEvent('contextmenu',{bubbles:true,cancelable:true});
        element.dispatchEvent(event); return event.defaultPrevented;
      });
      await teacher.screenshot({path:path.join(root,`preview-${extension}.png`),fullPage:true});
      results.push({check:'Office browser conversion',format:extension,result:'PASS',pdfBytes:pdf.length,
        metadata:{version:resource.version,fileSizeBytes:resource.fileSizeBytes,createdAt:resource.createdAt},
        previewText:text.trim(),contextMenuPrevented});
      await teacher.getByRole('button',{name:'关闭',exact:true}).click();
      console.log(`PASS ${extension.toUpperCase()}: browser upload, stored metadata, LibreOffice conversion, preview body and viewer watermark`);
    }
    // Actual file-size guard, rather than testing only client-provided metadata.
    const tooLarge = await api(teacher,'/api/v1/resources/upload','POST',{timeout:120000,multipart:{
      file:{name:'too-large.pdf',mimeType:'application/pdf',buffer:Buffer.alloc(104857601)},
      courseId:String(course.id),chapter:'Synthetic size limit probe',resourceName:'Oversized synthetic fixture'
    }});
    const tooLargeBody=await tooLarge.json(); assert.equal(tooLargeBody.code,400);
    assert.match(tooLargeBody.message,/100MB/);
    results.push({check:'100MB file limit',result:'PASS',bytes:104857601,businessCode:tooLargeBody.code});
    console.log('PASS real multipart file exceeding 100MB rejected');
    // Current plan-based syllabus maintenance, including immutable locked baseline.
    const plan='RECHECK-'+Date.now();
    const planItems=[{indicatorCode:'R-1',requirementCategory:'工程知识',indicatorDescription:'Synthetic indicator one'},
      {indicatorCode:'R-2',requirementCategory:'沟通',indicatorDescription:'Synthetic indicator two'}];
    assert.equal((await (await api(director,`/api/v1/syllabus/plans/SE/${plan}/indicators`,'PUT',{data:planItems})).json()).code,200);
    const syllabusBody=await (await api(teacher,`/api/v1/syllabus/course/${course.id}/from-plan`,'POST',{
      data:{syllabusVersion:plan,planVersion:plan}})).json(); assert.equal(syllabusBody.code,200);
    const route=`/api/v1/syllabus/course/${course.id}/indicators`;
    const original=(await (await api(teacher,route)).json()).data.find(i=>i.indicatorCode==='R-1');
    assert.ok(original);
    assert.equal((await (await api(teacher,`/api/v1/syllabus/indicators/${original.id}`,'DELETE')).json()).code,200);
    const added=await (await api(teacher,route,'POST',{data:{...planItems[0],supportWeight:'H',targetGoal:'Goal 1'}})).json();
    assert.equal(added.code,200);
    const updated=await (await api(teacher,`/api/v1/syllabus/indicators/${added.data.id}`,'PUT',{
      data:{...planItems[0],supportWeight:'L',targetGoal:'Goal 2'}})).json();
    assert.equal(updated.data.supportWeight,'L'); assert.equal(updated.data.targetGoal,'Goal 2');
    const locked=await (await api(director,`/api/v1/syllabus/${syllabusBody.data.id}/lock?lockedBy=synthetic`,'POST')).json();
    assert.equal(locked.data.status,'LOCKED');
    const blocked=await (await api(teacher,`/api/v1/syllabus/indicators/${added.data.id}`,'PUT',{data:{supportWeight:'M'}})).json();
    assert.notEqual(blocked.code,200);
    results.push({check:'US05 add/edit/delete, H/L goal mapping and director locking',result:'PASS',lockedEditCode:blocked.code});
    console.log('PASS US05 indicator add/edit/delete, weights, course goals, locking and locked-edit rejection');
    const templateButtons=await teacher.getByRole('button',{name:/12.*模板|国标.*模板|套用.*模板/}).count();
    results.push({check:'Older specification: built-in 12-item template',templateButtons,
      currentBehavior:'Create syllabus from director-imported versioned training plan'});
    }
    // Compare old specification's at-least-three highlights with real input validation.
    const teacherOfferings=(await (await api(teacher,'/api/v1/courses/offerings')).json()).data;
    const offering=(await (await api(supervisor,'/api/v1/courses/offerings')).json()).data.find(o=>teacherOfferings.some(t=>t.id===o.id));
    assert.ok(offering);
    const draft=await (await api(supervisor,'/api/v1/supervisions','POST',{data:{offeringId:offering.id,
      listenTopic:'Synthetic draft probe',scoreAttitude:20,scoreContent:20,scoreMethod:20,scoreEffect:20,
      highlights:'',suggestions:'',isDraft:true}})).json();
    assert.equal(draft.code,200); assert.equal(draft.data.status,'DRAFT');
    const invalidScore=await (await api(supervisor,'/api/v1/supervisions','POST',{data:{offeringId:offering.id,
      listenTopic:'Synthetic out-of-range score probe',scoreAttitude:26,scoreContent:20,scoreMethod:20,scoreEffect:20,
      highlights:'Synthetic highlight',suggestions:'Synthetic suggestion',isDraft:false}})).json();
    assert.equal(invalidScore.code,400);
    const single=await (await api(supervisor,'/api/v1/supervisions','POST',{data:{id:draft.data.id,offeringId:offering.id,
      listenTopic:'Synthetic single-highlight probe',scoreAttitude:20,scoreContent:20,scoreMethod:20,scoreEffect:20,
      highlights:'仅一条亮点',suggestions:'增加练习',isDraft:false}})).json();
    assert.equal(single.code,200); assert.equal(single.data.status,'PENDING_REVIEW');
    assert.equal(single.data.totalScore,80);
    assert.equal(single.data.id,draft.data.id);
    results.push({check:'US13 evaluation draft -> formal submission, score sum and score bound',result:'PASS',
      totalScore:single.data.totalScore,outOfRangeCode:invalidScore.code});
    results.push({check:'Older specification: >=3 highlights',inputHighlights:'仅一条亮点',businessCode:single.code,status:single.data.status});
    console.log('OBSERVED US14: one highlight accepted for formal submission');
  } finally {
    fs.writeFileSync(path.join(root,'supplement-results.json'),JSON.stringify(results,null,2));
    await browser.close();
  }
})().catch(e=>{console.error(e);process.exitCode=1;});
