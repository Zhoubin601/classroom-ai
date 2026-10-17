import json
from pathlib import Path

evidence = Path(__file__).resolve().parent
root = evidence.parents[1]
summary = json.loads((evidence / 'verification-summary.json').read_text(encoding='utf-8'))
assert summary['realPolicyCases'] == 14 and summary['mockFaultInjectionCases'] == 3
assert not summary['changedAfterTestingStarted'] and not summary['listeningTestPorts']
report = '''# exp4 两项缺陷修复与 Playwright 验证

日期：2026-10-17，Asia/Shanghai。基于 exp4 / 3dddcbd 的本地未提交修复，当前源码与实际运行产物的指纹见本轮 fixed-source.json / runtime-artifacts.json。

**结论：前轮 R-01（上传上限配置未贯通前端）和 R-02（BOPPPS 与四维评分文案混淆）均已修复，相关真实浏览器回归通过。** 本轮不提交、推送或替换日常部署，不将软件回归等同于自然人正式签收。

## 修复内容

1. 新增已登录 GET /api/v1/resources/upload-policy，返回 maxFileBytes 和 allowedExtensions；后端 ResourceUploadPolicy 统一接口公布、Controller 上传和 Service 的校验。扩展名按空格、大小写、前导点规范化并去重。字节上限同时受资源配置、Servlet 单文件及请求总量限制；multipart 头和其他字段仍由 Servlet 做总量校验。
2. 教师主上传和同系列新版上传共用策略读取与校验。动态更新 accept、文件格式选项、精确字节提示；选择文件及提交前均检查。配置加载失败时禁止选取/提交并提供重试，不退回固定100MiB。重试与卸载使用取消信号和请求代次，迟到结果不能恢复已离开的界面。主上传超时调整为120秒以支持较大合法文件。
3. 教师雷达徽标改为“教学评价四维”，建议标题改为“教学改进建议”；督导说明、打分标题、通用建议标签同步改正。保留真正的 BOPPPS 六环节与可选标签，没有修改四维字段、权重、评分计算或建议数据模型。

## 本轮执行证据

以下路径相对 docs/20261017-exp4-two-fixes-v1/，所有通过数字均来自本轮实际执行，不沿用上一轮旧结果。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 前端测试、类型检查与生产构建 | 27项通过，构建退出0；有既有大chunk提示 | frontend-tests.log / frontend-build.log / frontend-exit.json |
| 后端策略/HTTP/兼容定向测试 | 4类30项通过，无失败、错误、跳过；与完整回归重叠，不相加 | backend/test-results.json |
| 当前JAR重新打包 | 成功 | backend-package.log / backend-package-exit.json |
| 完整后端回归（含隔离MySQL集成） | 41类294项通过，失败/错误/跳过均0 | full-regression/verification-summary.json / backend-full-xml（位于full-regression内） |
| Sprint1真实浏览器 | 三角色、五故事通过 | full-regression/legacy-browser/us0203-real-browser.log |
| Sprint2正式入口 | 七套全部PASS，退出0 | sprint2-browser/results.json / sprint2-exit.json |
| Sprint3真实浏览器、补充评价闭环 | 8组 / 4组通过，页面错误0 | full-regression/sprint3-browser/browser-results.json / full-regression/supplement/results.json |
| 资源界面竞态 | 7组通过，模拟接口交互回归 | full-regression/resource-browser/results.json |
| 新增上传策略与文案真实业务回归 | 14组通过，页面错误0 | policy-browser/retry-v5/results.json |
| 策略故障注入交互 | 3组通过，页面错误0；明确属于模拟503/延迟返回专项 | policy-browser/retry-v5/fixture-results.json |

Playwright 1.57.0 驱动本机 Google Chrome 155.0.8059.80，headless。真实业务使用重新构建的前后端及隔离 MySQL/Redis，不模拟上传、预览或评分响应。故障注入三组、既有登录/资源竞态脚本单独归类，不扩写成真实业务数据验证。

### 修复缺陷的直接验证

- 后端实际上限150MiB（157286400字节）：101MiB（105906176字节）合法合成PDF从真实教师主表单点击上传成功；同系列再上传同尺寸新版也成功，历史版本保留、当前版本切换正确，两处真实画布均显示正文及身份水印。
- PDF-only、1024字节上限：主入口与版本入口各验证900字节和恰好1024字节PDF上传成功；1025字节PDF及789字节DOCX均被前端拒绝，未新增上传请求，非法选取被清空，提交按钮保持禁用。accept、精确大小提示与后端策略一致。
- 两名教师真实页面显示准确四维/建议文案，旧误导标题不存在；督导真实表单以21、22、23、24分保存草稿，总分90，验证文案更改未影响评分。
- 六个教学环节仍可独立选择；保存B/P2/S标签和不选标签的建议均成功，重新查询四维分值保持不变。
- 仅故障注入专项模拟策略503或延迟：主上传、版本上传失败时禁用输入/提交且可重试恢复真实策略；关闭弹窗后切换页面、释放迟到响应，再进入教师页，旧弹窗不会被恢复。

最终截图在 policy-browser/retry-v5/：01/02 为101MiB两个入口预览；04/05 为大小/类型拒绝；06 为两名教师反馈；07/08 为督导四维和六环节；09～11为故障恢复。完整文件名见同目录。

### 测试脚本适配及首次失败留证

此前专项尝试保留在 policy-browser/ 根、retry-v2/、retry-v3/、retry-v4/，没有覆盖或将失败记录改为PASS。中断原因分别涉及读取到策略status而非toast、未进入课程检索页、未等待父评价建议异步加载完成便填写、以及在模态遮罩仍打开时点击侧栏。专项脚本已按真实操作及请求完成事件修正；并分开业务草稿DRAFT状态与用例PASS状态，避免统计字段覆盖。最终 retry-v5 从头完成14个真实业务检查和3个故障注入检查，退出0。业务源码在这些测试适配期间保持冻结，没有为消除脚本失败而继续修改。

## 文件与资料

业务/测试修改清单：

- 后端：CourseResourceController.java、CourseResourceServiceImpl.java，新增 ResourceUploadPolicy.java、ResourceUploadPolicyTest.java、ResourceUploadPolicyHttpTest.java（均位于 backend/src 对应 resource 包）。
- 前端：frontend/src/api/index.ts、types.ts；components/ResourceVersionsPanel.vue；views/TeacherDeskView.vue、SupervisorDeskView.vue；新增 ui/resourceUploadPolicy.mjs、ui/useResourceUploadPolicy.ts 和 frontend/tests/resource-upload-policy.test.mjs。
- 浏览器回归：新增 scripts/tests/resource-upload-policy-browser.cjs，给既有 scripts/tests/resource-versions-browser.cjs 补充新策略接口夹具。
- 过程记录：新增本报告和 docs/20261017-exp4-two-fixes-v1/ 下日志、截图、JSON、隔离runner及核对脚本；追加 docs/source-notes.md、analysis-notes.md、questions.md、plan.md。既有失败记录、用户未跟踪资料和旧报告保留。

依据前轮已校验的实验一《第二组_项目规划方案.docx》原文摘录和本轮用户修复授权。原件路径为 D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx；本轮没有重新解析DOCX，交付前再次核对SHA256仍为299cd2d510e7a8da609ec2fe513e3810e616d3562673bce09ee16e81688437bb。项目raw/只有README.md，其他业务原始资料未提供；测试使用已确认的合成方案、资源、视频，不引入外部联网业务资料。界面错误恢复检查采用ui-ux-pro-max技能。

最终核对：修复后505个受保护文件在测试期间未变，JAR和前端构建产物未变；raw、memory-bank和既有output没有修改。仅清理本轮生成的临时运行目录、专用容器和进程，专用端口无残留。本次汇总未记录运行中的日常容器，原因资料未提供；没有替换日常部署。具体指纹、修改清单和清理结果见 verification-summary.json。

R-01/R-02均可标记为本轮已关闭，无本轮授权范围内未完成修复或必需测试。请按真实阅读体验复核新文案；自然人签收与摄像头/多人识别/GPU/目标设备等既有后续范围不由本次软件回归替代。本轮代码仍在本地工作树，未提交、推送或部署。
'''
report_path = root / 'docs/20261017-exp4-two-fixes-verification-v1.md'
if report_path.exists():
    raise SystemExit('Existing report preserved; use a new version rather than overwrite.')
report_path.write_text(report, encoding='utf-8')
entries = {
 'source-notes.md': '本轮直接来源：docs/20261017-exp4-two-fixes-v1/verification-summary.json、full-regression/verification-summary.json、policy-browser/retry-v5/results.json与fixture-results.json；引用日期：2026-10-17。业务边界14组与故障注入3组分开；前几次脚本中断留证，不冒称首轮全部成功。',
 'analysis-notes.md': 'R-01/R-02已修复并验证：前端27项、后端41类294项无失败/错误/跳过，Sprint2正式七套通过；策略与文案14组真实业务、3组故障交互通过。150MiB下两个表单均真实上传/预览101MiB；1024字节两入口边界/类型正确；督导90分草稿与可选六环节独立保存。所有测试适配失败保留，业务源码冻结505文件未变。详细结论见docs/20261017-exp4-two-fixes-verification-v1.md。',
 'questions.md': '更新：此前本轮R-01/P2与R-02/P3待修复状态已由修复和Playwright验证关闭，无需再次确认实施方向。用户可复核新文案；自然人批准/真实评审回顾/硬件现场范围仍按既有安排。本轮未提交、推送、部署，不将其冒称完成。',
 'plan.md': '本轮四步全部完成。新增策略/文案实测最终14+3组通过，正式Sprint2、完整后端及其他业务回归通过，原件与源码保护、容器/端口清理已核对。过程见本轮verification-summary.json及docs/20261017-exp4-two-fixes-verification-v1.md。',
}
heading = '## 2026-10-17 两项缺陷修复结果'
for name, entry in entries.items():
    file = root / 'docs' / name
    if heading not in file.read_text(encoding='utf-8'):
        with file.open('a', encoding='utf-8', newline='\n') as handle:
            handle.write('\n\n' + heading + '\n\n' + entry + '\n')
print(json.dumps({'report': str(report_path.relative_to(root)), 'evidence': str(evidence.relative_to(root)), 'businessCases': summary['realPolicyCases'], 'faultInjectionCases': summary['mockFaultInjectionCases']}))
