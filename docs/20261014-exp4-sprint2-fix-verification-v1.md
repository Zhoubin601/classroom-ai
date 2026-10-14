# exp4 Sprint 2 修复、完善及验证记录

日期：2026-10-14，Asia/Shanghai。用户明确授权：“修复并完善，之后推送至远端仓库”。基线为本地及本轮 fetch 后的 origin/exp4 `1ea1f0323367fc12ef6830c7a7522e8e4b27746b`。发布目标是 origin/exp4，使用普通追加提交；最终提交和推送结果以 Git 核验及本地 publication.json 为准。

**结论：上轮两处确定缺陷已修复，本轮覆盖的 Sprint 2 八故事软件验收通过；匿名反馈页面和非默认配置补验已经完成。** 本轮全部执行项通过。没有将软件测试扩写为真实摄像头、多人/GPU效果、生产容量或自然人正式签收。

## 修复结果

| 问题 | 实现 | 验证 |
| --- | --- | --- |
| 旧身份成功先返回、新登录后完成时页面与Token不一致 | LoginView在交互登录/有效注册请求开始前发出auth-start，App立即取消独立启动探测；探测在CSRF之后和写入身份前均校验取消与会话代次；认证失效/成功/卸载也撤销探测；API支持探测AbortSignal | 有效旧Token的成功先/后返回、HTTP401、HTTP200业务401、失败登录五组通过，姓名/角色入口/Token一致；原三组及正式401撤销继续通过 |
| 慢启动链路后来才发起默认指标加载，覆盖用户历史选择 | 教师的整体课程加载链路使用代次，排课/开课请求返回后及启动后续加载前校验；指标响应同时校验请求代次、课程上下文及卸载；切课清除旧矩阵与写操作 | 同一真实后端复现顺序下，历史ID9在释放原启动响应后仍为9，旧目标行保留、当前版行不存在、历史写按钮禁用，页面错误0；普通教师/主任历史专项继续通过 |
| 旧Sprint2脚本不能运行当前exp4 | 正式三套脚本更新账号/页签/确认弹窗、必要模板版本与expectedVersion字段及就绪等待；缺评分断言匹配当前正权重规则；保留权限、预览、匿名、历史、零分等原业务断言 | 正式入口直接执行七套脚本全部通过，没有使用过程副本冒充正式脚本 |
| 时区与Office测试环境导致补验缺口 | 新run-sprint2-acceptance支持Auto/Native/Docker，缺少本机LibreOffice时使用已有运行镜像并挂载当前JAR；各实例顺序启动；服务与MySQL使用Asia/Shanghai；24h断言比较同域后端时间；仅接受新/空证据目录，恢复环境并可靠清理/脱敏 | Windows本机通过兼容run-exp3-browser-tests入口，显式Docker模式运行成功；24h到期匿名反馈页面及零延迟自然反馈页面均通过 |
| 既有UI脚本即时断言异步保存完成 | 使用原有assertEventually等待页签选中，同时保留实际保存数据断言 | 15组既有交互回归全部通过 |

注册代码的开始保护已纳入，但当前登录模板没有自助注册入口；不宣称五组浏览器覆盖了注册页面。Native模式本轮未实跑，本机实跑为Docker模式。

## 本轮执行结果

证据目录：`docs/20261014-exp4-sprint2-fix-v1/`。汇总：`verification-summary.json`。各数字只指本轮执行，测试组之间有重叠，不作简单相加。

| 验证 | 结果 | 主要证据 |
| --- | --- | --- |
| 前端单元、类型和生产构建 | 23通过，0失败/跳过；构建成功 | frontend-exits.json、frontend-tests.log、frontend-build.log |
| 后端package普通回归 | 成功；206通过、78条件跳过 | backend-package-exit.json、backend-ordinary-xml/ |
| 后端完整隔离回归 | **单次284通过，0失败/错误/跳过，Maven退出0** | full-exit.json、backend-full.log、backend-full-xml/ |
| Python无硬件回归 | 9通过，退出0 | vision-exit.json、vision-tests.log |
| Sprint2正式runner | 七套脚本全部通过，runner退出0 | sprint2-exit.json、browser/results.json |
| Sprint2主流程 | 目录导入/替换、上传/标签/共享、受控预览/过期/失败、评价/审核/延迟、到期后匿名反馈页面通过 | browser/exp3-real-browser.cjs.log、browser/core/03-teacher-published-feedback.png |
| Office严格补验 | 6条结果记录通过，另含真实超过100MB拒绝 | browser/office/fix-acceptance-results.json |
| 缺评分、明确0分、历史API/UI | 四组通过 | browser/history/history-scores-results.json |
| 慢启动历史专项 | 一组真实后端时序回归通过，页面错误0 | browser/history-race/results.json、requests.json |
| 登录顺序专项 | 五组通过，页面错误0；清理测试无效分支后再次五组通过 | browser/login-start/results.json、login-start-final/results.json |
| 原登录/正式401 | 三组通过，页面错误0 | browser/login-probe/results.json |
| 非默认配置专项 | PDF-only/1024字节限制、未标注筛选、小数模板权重、审核修改日志及自然零延迟匿名反馈四组通过 | browser/configurable/results.json |
| 实验二回归 | 三角色、五故事、10子流程通过 | legacy-browser/us0203-real-browser.log |
| Sprint3真实业务 | 八组通过，页面错误0 | sprint3-browser/browser-results.json |
| 资源版本专项 | 七组通过，页面错误0 | resource-browser/results.json |
| 既有UI与七问题回归 | 分别15和8组通过，页面错误0 | ui-browser/results.json、fixes-browser/browser-results.json |
| 静态检查/保护/清理 | CJS及PowerShell语法、git diff检查通过；原件不变，专用端口和测试容器无残留 | verification-summary.json、cleanup.json |

正常24h反馈检查先断言发布时长与发布前教师不可见，再只推进本次隔离库中该条评价的发布时间，验证到期匿名API与教师页面；没有实际等待24h。独立18083配置为0h，验证自然即时反馈，不改数据库发布时间。小数权重通过发布模板配置，符合当前模板版本机制；未增加新的评分制度。

本轮普通package与正式runner使用相同未改动的Java业务源码；随后完整数据库回归和Sprint3业务均使用本輪构建结果。Java业务源码、数据库迁移、培养方案口径、课程权限及日常数据均未改动。

## 变更范围与使用方式

业务源码：`frontend/src/App.vue`、`frontend/src/views/LoginView.vue`、`frontend/src/views/TeacherDeskView.vue`、`frontend/src/api/index.ts`。

正式验收：`scripts/run-sprint2-acceptance.ps1`、兼容入口`run-exp3-browser-tests.ps1`；新增`browser-helpers.cjs`、`login-start-browser.cjs`、`exp3-startup-history-browser.cjs`、`exp3-configurable-browser.cjs`、`sanitize-test-evidence.py`；修改三套Sprint2脚本与UI异步断言。README和测试说明同步更新。

```powershell
# 在项目根目录，先构建当前前后端，再执行正式兼容入口。
.\scripts\run-exp3-browser-tests.ps1 -EvidenceDir 'docs/runtime/sprint2-new-run'
# 本机没有原生LibreOffice时也可显式指定已有运行镜像。
.\scripts\run-sprint2-acceptance.ps1 -BackendMode Docker -BackendImage 'classroom-ai-demo-classroom-backend:latest' -EvidenceDir 'docs/runtime/sprint2-new-docker-run'
```

依赖现有Docker、本地mysql:8.0.36/Redis镜像、Playwright与Chromium。缺少运行镜像时需要先按项目构建，并用BackendImage指定正确的镜像名称；脚本不拉取镜像，也不替换日常服务。专用端口13318、16318、18081～18083、15173须空闲。Native模式需要原生LibreOffice与当前JAR；本轮只确认该路径语法与实现，未声称Native实跑通过。

## 来源、保护和发布边界

来源名称：用户当前修复/完善/推送授权与上轮检查；文件名：本聊天、docs/20261014-exp4-sprint2-acceptance-check-v1.md；引用日期2026-10-14。上轮两处复现结果复制到本轮`before-login.json`与`before-history.json`，保留原错误行为记录，未将它们改写成通过。

来源名称：实验一原始规划表6/11/16及已确认范围；文件名：`D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx`、memory-bank/project-context.md；引用日期2026-10-14。原件本轮只核对指纹，沿用上一轮officecli只读摘录；不重造需求。raw/只有README.md，其他业务原始资料未提供。

来源名称：本轮直接测试、initialize.sql初始化样例、scripts/tests/fixtures的DOCX/PPTX、既有纯合成视频；引用日期2026-10-14。没有联网补充业务事实、写入日常数据库或采集真人摄像头内容。使用ui-ux-pro-max Skill做交互检查，没有增加视觉方案。

原规划DOCX和raw/README.md的SHA256保持一致；309个上轮基线文件中仅本轮预期的4个前端源码、4个测试脚本和测试README改变，未改Java业务、vision或原始资料。当前源码指纹见source-hashes.json。日常三个容器ID与前轮一致；测试环境已清理。临时认证值在发布前已扫描/脱敏；运行环境、数据库卷、上传运行文件和完整旧检查原始证据继续仅存本机。

发布只纳入本轮修复、可复用正式验收脚本、必要过程记录及脱敏验收证据。当前目标是exp4，不合并main、不替换8080/5173日常部署。推送后其他环境需要获取当前exp4并重新构建/重启，Git不会同步数据库卷。

仍需用户现场复核：真实摄像头、多人数识别/GPU、完整Windows一键业务启动及生产网络/容量。正式评审、回顾和自然人批准按用户既有安排后续执行。前端构建仍有既有大包提示（主JS约1.94MB），本轮未开展性能架构优化；不据本轮自动测试保证未覆盖环境绝对无缺陷。
