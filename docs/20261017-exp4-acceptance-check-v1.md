# 当前 exp4：Sprint 2 与实验一规划全功能复验

检查日期：2026-10-17，Asia/Shanghai。分支：`exp4`；HEAD：`3dddcbdcdc4e86f2aaf06c38d0bb9b25cab38026`。

**结论：当前默认配置下的核心业务流程、Sprint 2 正式七套脚本和完整后端回归（含隔离 MySQL 集成）均通过，但不能认定 Sprint 2 或实验一全部验收标准已无条件满足。本轮发现并真实复现 US-07 的 P2 功能缺陷：服务端调大上传上限后，教师主上传入口仍固定拒绝超过 100MiB 的文件。另有 BOPPPS 与四维评分的 P3 展示歧义。建议修复上述问题并定向复验后，再申请全量功能签收。**

原规划的 21 条软件故事均有实现路径与本轮关联测试证据；关联测试通过不等于每个验收点已被独立断言。本轮没有修改业务源码，也没有将测试成功复现缺陷写成业务功能通过。

## 1. 本轮真实运行范围

- 只读重新核对实验一正式原件《第二组_项目规划方案.docx》的正文及表 3、4、5、6、7、11、12、16，使用当前源码重新构建 JAR 和前端产物。
- Playwright 1.57.0 驱动本机已安装的 Google Chrome **155.0.8059.80**，采用 headless 模式。真实业务测试访问重新构建的前端、Spring 后端及隔离 MySQL 8.0.36 / Redis；没有向日常演示数据库写入测试数据。
- 主业务浏览器没有模拟业务响应。登录竞态、资源界面竞态使用模拟接口；历史启动竞态仅控制真实接口返回时序。下表分别说明，不能把所有浏览器脚本都算作真实业务端到端测试。
- 测试使用初始化样例、明确标为合成的培养方案、Office 夹具、合成视频与本轮合成 PDF。本轮没有真人摄像头、多人识别、GPU/目标设备效果、生产负载或自然人签收实测。

运行产物哈希和浏览器版本：`20261017-exp4-acceptance-recheck-v1/runtime-artifacts.json`。资料原文与静态映射：同目录 `original-plan-extract.txt`、`requirements-audit.md`。

## 2. 本轮执行结果

| 检查 | 本轮结果 | 直接证据（相对本轮证据目录） |
| --- | --- | --- |
| 前端单元测试 | 23 通过，0 失败、跳过 | `frontend-tests.log` |
| 前端类型检查、生产构建 | 成功，退出 0；存在既有大 chunk 提示 | `frontend-build.log`、`frontend-exit.json` |
| 当前后端 JAR 重新打包 | 成功；本阶段跳过测试，完整测试另列下一行 | `backend-package.log` |
| 完整后端回归（含隔离数据库） | 39 类、284 项，失败 0、错误 0、跳过 0 | `full-regression/backend-full.log`、`full-regression/backend-full-xml/` |
| Sprint 1 真实业务浏览器 | 三角色、五故事流程通过，含导入、发布、排课、历史与组合检索 | `full-regression/legacy-browser/us0203-real-browser.log` |
| Sprint 2 正式入口 | 七套全部 PASS，退出 0 | `sprint2-runner.log`、`sprint2-exit.json`、`sprint2-browser/results.json` |
| Office/权限/大纲评分补验 | 六组通过，其中包含真实 DOCX/PPTX 转 PDF 与画布渲染、翻页、损坏文件拒绝及鉴权 | `sprint2-browser/office/fix-acceptance-results.json` 及截图 |
| 评分、历史、启动竞态 | 四组评分/历史通过；迟到默认加载未覆盖已选历史 ID 9 | `sprint2-browser/history/history-scores-results.json`、`sprint2-browser/history-race/results.json` |
| 原非默认配置专项 | PDF-only/1024 字节策略、未标注、小数权重和零延迟匿名反馈四组通过 | `sprint2-browser/configurable/results.json` |
| Sprint 3 真实业务浏览器 | 八组通过，页面错误 0；首轮和定向复跑均通过 | `full-regression/sprint3-browser/browser-results.json`、`full-regression/browser-rerun/sprint3-browser/browser-results.json` |
| 资源界面竞态补充 | 七组通过，页面错误 0；属于模拟接口的前端交互回归 | `full-regression/browser-rerun/resource-browser/results.json` |
| 补充评价闭环 | 旧脚本首轮超时；修订测试选择器/班次后真实闭环通过，保留原失败证据 | `full-regression/supplement.log`、`full-regression/browser-rerun/supplement/results.json` |
| 新增上传上限放大专项 | **探针执行成功，US-07 业务验收 FAIL，缺陷两次复现** | `upload-policy-probe/render-confirmation/results.json`、`upload-policy-probe/render-confirmation/runtime-metadata.json` 及两张截图 |

七套正式入口为 `login-probe-browser.cjs`、`login-start-browser.cjs`、`exp3-real-browser.cjs`、`fix-exp3-acceptance.cjs`、`exp3-history-scores.cjs`、`exp3-startup-history-browser.cjs`、`exp3-configurable-browser.cjs`。前两套主要验证模拟返回顺序，后续业务专项使用隔离真实后端。

这些分组有重叠，不相加成用例总数。旧汇总中的 55 个故事测试引用对应 **46 个不同方法**；本轮将其全部重新映射到本次生成的 XML，均找到实际执行通过的 case，结果见 `executed-story-evidence.json`。这不证明所有细分验收点都各有一个独立测试。

首轮补充脚本的超时不是省略项：10 月 16 日目录根的旧副本选中了 `SYN-EMPTY`，并等待教师页面不展示的听课主题。新副本采用既有最终版的适配，明确选择 `SYN-CORE` 并等待实际建议文本，在新的隔离环境完成复跑。业务代码没有为消除测试失败而修改。首轮 `full-regression/full-results.json` 保留 `supplement=FAIL`，定向结果由 `full-regression/browser-rerun/full-results.json` 记录，综合说明见 `full-regression/verification-summary.json`。

## 3. 本轮发现的验收问题

### R-01 / P2：US-07 主上传入口不遵循调大的上限

原件表 6 的验收标准是“允许类型与大小限制可配置；上传失败有提示；资源绑定课程并校验访问权限”。后端 `application.yml:63` 与 `CourseResourceController.java:35`、`:61` 按配置值处理；`TeacherDeskView.vue:1231`～`:1234` 却写死 `file.size > 100 * 1024 * 1024` 并返回。

本轮两次使用完全独立的真实环境复现，最终证据如下：

1. 后端实际启动参数为 `--classroom.resource.max-file-bytes=157286400`，即 150MiB；multipart 维持 200MB/220MB。参数与当前构建产物哈希保存在 `upload-policy-probe/render-confirmation/runtime-metadata.json`。
2. 教师通过真实页面登录，选择本人授权课程，主上传表单选入合法 **101MiB / 105906176 字节**的合成 PDF。
3. 页面提示“单文件大小不能超过 100MB 限制！”，应用没有接受文件，仍显示初始选文件提示且资源名为空，未发起上传请求。**按钮本身没有 disabled，本轮未点击失败上传，不能把现象写成按钮禁用。**
4. 同一教师、同一课程、同一文件直调上传 API，返回 HTTP 200 / code 200，保存大小为 105906176 字节。
5. 同一文件通过服务端 PDFBox 预览处理，随后在真实 Chrome 画布显示正文和身份水印，第 1/1 页、像素检查通过，排除了损坏 PDF 夹具。

证据图片：`upload-policy-probe/render-confirmation/01-chrome-rejects-101MiB-under-150MiB-server-policy.png` 与 `02-same-101MiB-pdf-rendered-after-api-upload.png`。

影响：默认 100MiB 配置下既有上传链路可用；部署方按规划调高大小上限后，教师仍无法从主入口上传符合规则的文件，因此 **US-07 只能判部分满足**。原来的非默认专项只将上限调小，且通过 API 验证上传，未覆盖这个前端边界。

建议让前端读取已生效的服务端上传策略，统一主上传、版本上传的校验和提示，补充上限调大、调小及边界值的真实表单回归。本轮仅检查，尚未修复此项。

### R-02 / P3：BOPPPS 与四维评分仍有展示歧义

- `SupervisorDeskView.vue:728` 仍称“BOPPPS 四维打分”。
- `TeacherDeskView.vue:345` 在教学态度、内容、方法、效果雷达旁标“BOPPPS 教学模型”；`:397` 将所有建议统一称为“BOPPPS 进阶教改建议”。
- 原件 US-13 规定上述四个示例评分维度，US-21 规定 BOPPPS 为可选教学环节标签、并不等同四维评分。本轮真实反馈页截图 `sprint2-browser/core/03-teacher-published-feedback.png` 可见该组合。

独立建议、环节标签和分值模型已有实现，当前证据未显示四维分值计算因此出错；问题是页面口径容易误导验收人。建议改为“教学评价四维”“教学改进建议”，有实际环节标签时单独显示。既有固定“24 小时”提示已经修正，本轮不再把它列为缺陷。

## 4. 对照实验一的 21 条故事

下表“核心流程通过”表示本轮有关联的实现、数据库和/或真实浏览器证据，未见该核心链路阻断，不表示所有 AC 分句都有独立断言，也不代表自然人签收。

| Sprint | 故事 | 本轮覆盖的主要功能 | 验收判断 |
| --- | --- | --- | --- |
| 1 | US-01 | 模板/导入预览、错误清单、确认写入与操作者 | 核心流程通过 |
| 1 | US-02 | 简介/考核/目标、草稿发布隔离、必填与版本 | 核心流程通过 |
| 1 | US-03 | 多教师、周次节次、冲突、文管 A447 / 95 人样例展示 | 核心流程通过 |
| 1 | US-04 | 按学期的历史、人数/累计人次区分与冻结归档 | 核心流程通过 |
| 1 | US-06 | 单条件/AND 组合检索、权限、数据量及耗时记录 | 核心流程通过 |
| 2 | US-05 | 合成方案派生任意数量指标、映射/锁定、独立历史快照 | 核心流程通过 |
| 2 | US-07 | 授权课程章节上传、失败提示、格式与大小限制 | **部分满足；R-01 配置放大边界失败** |
| 2 | US-08 | 多选标签维护、筛选、无标签明确展示 | 核心流程通过 |
| 2 | US-09 | 登录鉴权、限时水印预览、日志、到期/撤权 | 核心流程通过 |
| 2 | US-10 | 本室共享检索、非共享/外室隔离及撤销访问 | 核心流程通过 |
| 2 | US-13 | 草稿、缺项/零分、小数权重、正式提交与审核状态 | 核心流程通过；R-02 展示需完善 |
| 2 | US-14 | 审核/驳回/修改日志、延迟与即时匿名反馈 | 核心流程通过 |
| 2 | US-15 | 有效开课课程为分母、课程去重与明细追溯 | 核心流程通过 |
| 3 | US-11 | 资源新旧版本保留、当前版切换、固定 ID 与冲突/撤权 | 核心流程通过 |
| 3 | US-12 | 授权合成视频、服务端时长、片段起止校验与区间回看 | 核心流程通过 |
| 3 | US-16 | 阈值来源、零覆盖/低分预警与人工复核提示 | 核心流程通过 |
| 3 | US-17 | 授权反馈、样本/尺度/缺失/未知权重处理与雷达 | 核心流程通过；R-02 展示需完善 |
| 3 | US-18 | 已应用筛选的全量 CSV/XLSX、明细与权限 | 核心流程通过 |
| 3 | US-19 | 已审核标签、课程类别、分类版本/样本数与“仅频次，无排名”提示 | 核心流程通过 |
| 3 | US-20 | 权重合计 100%、不可变发布模板和历史快照 | 核心流程通过 |
| 3 | US-21 | 建议/证据/可选环节、教师接受或调整及行动反馈历史 | 核心流程通过；R-02 展示需完善 |

US-06 本轮记录的测试规模为 3 门课程、3 班次、2 教师、95 学生、2 专业，执行 20 次顺序请求；原始时间序列见 `full-regression/legacy-browser/us0406-query-performance.json`，不作为生产容量承诺。

默认反馈延迟验证包括审核后与发布时间相差 86400 秒、到期前不可见，然后只推进隔离测试记录的发布时间验证到期后的匿名 API 和页面；没有真实等待 24 小时。零小时配置分支自然即时发布，没有推进时间。US-12 使用合成视频验证挂载和区间回看，没有声称自动切片或真实课堂识别通过。

原件明确 AI 视音频识别、完整个性化推荐和完整案例库属于 E-01～04 后续扩展，不能把本轮 21 条基线故事的证据扩写成这些愿景已全部完成，也不将其列作 21 故事欠缺。

## 5. 资料、保护与交付范围

使用原始资料：`D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx`。原件 SHA256 前后一致，为 `299cd2d510e7a8da609ec2fe513e3810e616d3562673bce09ee16e81688437bb`。本项目 `raw/` 只有 README.md，其他业务原始资料未提供；沿用用户已确认的合成方案范围。未使用外部联网业务资料。

指定的 `minimax-docx` 当前不可用，采用已读取的 `officecli-docx` / `officecli` 技能只读核对原件。用户没有要求正式 Word/Excel/PPT/PDF，本轮交付为 `docs/` 下的检查过程与证据，不在 `output/` 放未确认成品。

本轮新增本记录、`docs/20261017-exp4-acceptance-recheck-v1/` 下的原件摘录、运行脚本、日志、截图、导出样例及结构化证据，追加 `docs/source-notes.md`、`docs/analysis-notes.md`、`docs/questions.md`、`docs/plan.md`。旧版本和首次失败证据保留。

最终核对：499 个受保护文件的 SHA256 全部不变，当前 JAR/前端构建产物指纹不变，原件与 HEAD 不变；测试专用容器及端口全部清理，仅保留原有三个日常容器运行。仅删除本轮已确认生成的运行临时目录与一份 Python 缓存，旧资料保留。详情见本轮 `verification-summary.json`。本轮不修改业务源码、raw、memory-bank 或既有 output，不提交、推送、合并或替换日常部署。

未完成项：R-01、R-02 尚未修复。自然人 D5 批准、真实评审/回顾沿用已确认的后续安排，不虚构已完成；若另行验收真实摄像头、多人识别、GPU/目标设备或生产容量，需要相应现场证据。本轮软件功能复核所列测试均已执行，没有把未执行项记为通过。
