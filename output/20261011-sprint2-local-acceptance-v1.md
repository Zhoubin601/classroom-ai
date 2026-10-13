# fix-exp3 最新版 Sprint 2 本机验收

日期：2026-10-11，Asia/Shanghai。仓库：`D:/2026Autumn Semester File/classroom-ai-demo`。分支 `fix-exp3` 已从 `2c76376` 快进到 `f6c741fa9edccd71d801405779eab3a0679cbfa3`，收尾时核对远端同为该提交。

**结论：不能判定当前最新版的全部功能均已验收完成。原规划八故事的核心流程通过，但最新增加的教师历史大纲页面切换存在可复现的间歇性问题，历史专项没有全部通过。** 本次检查已经完成；该功能问题尚未修复。

用户已于 2026-10-10 确认：当前检查代码及验收证据，评审、回顾和 D5 自然人批准等指导书下发后再做。本报告沿用这一范围，不把这些后续流程列为本次代码阻塞，也不推定它们已发生。

## 范围与逐故事结论

只读重新核对实验一正式项目规划表 6、11、16。Sprint 2 为 US-05、07、08、09、10、13、14、15，原规划估算共 33 点。点数是计划估算，不代表团队实际签收点数。

| 故事 | 本机实测 | 判断 |
| --- | --- | --- |
| US-05 大纲与指标映射 | 合成目录导入、同版替换、重复拦截、不同版本快照、映射增删改、模板与目录分离、锁定拒绝修改通过；历史 API 和主任历史只读/返回当前版通过；教师历史切换多轮失败 | 主流程通过，最新历史页面功能未稳定通过 |
| US-07 上传 | DOCX/PPTX 实际上传及 LibreOffice 转 PDF、章节和课程绑定、越权/损坏文件/超 100MB 拒绝通过；改为只允许 PDF、1024 字节上限后规则实际生效 | 通过 |
| US-08 标签 | 多选标签上传、维护、检索、非法标签拒绝；空标签上传、未标注筛选及页面显示通过 | 通过 |
| US-09 受控预览 | Chrome 实际渲染、水印、PDF 保护、翻页、授权检查、访问日志、申请人绑定和过期链接拒绝通过 | 通过 |
| US-10 教研室共享 | 同室其他教师发现并预览，外室不可发现；撤销共享后搜索及旧票据立即失效 | 通过 |
| US-13 随堂评价 | 暂存和恢复、必填文本、缺失/空/部分评分拒绝、明确四个 0 分允许、提交进入待审核；权重改为 10/20/30/40 后页面上限和服务端校验实际生效，总分 100 | 通过 |
| US-14 审核反馈 | 提交、审核、默认 24 小时到期前不可见、到期后匿名反馈；驳回、修改、原 ID 重提、批准及 5 条审计记录通过；配置零小时后自然立即发布通过 | 通过 |
| US-15 覆盖统计 | MySQL 校验选定学期有效课程为分母、课程去重、其他学期/无效班次排除，2 门覆盖 1 门为 50%；浏览器覆盖明细关联评价 ID | 通过 |

摄像头和微格视频等扩展不在这八条故事的原始验收标准内，没有用它们扩大本次前置条件。培养方案按已确认的合成示例范围验证，不宣称为真实专业培养方案。

## 未通过项与复核方法

### 教师历史版本切换不稳定

使用真实 Chrome、当前源码构建的前后端和隔离 MySQL，创建两个版本：旧版首项权重 H、课程目标 v1；当前版首项权重 L、目标 v2。教师登录并选择该开课班次，在“查看大纲版本”选择旧版。

多轮观察到页面仍显示当前版，部分轮次切换成功，不能将其视为稳定通过。最终诊断中期望旧版 ID 9，页面最后选中 ID 10、显示 v2 目标。请求记录先出现 `indicators?syllabusId=9`，随后又出现 `indicators?syllabusId=10`；直接读取旧版 API 可取得正确的 H/v1 快照。增加页面网络空闲等待后仍可复现，不能仅归因为断言等待不足。

证据：`docs/20261011-sprint2-local-evidence-v1/history-diagnostic-final/teacher-failed-history.json`、`teacher-failed-history.png`、`history-scores-results.json`。入口和加载逻辑位于 `frontend/src/views/TeacherDeskView.vue` 第 463、896、951 行附近。请求证据显示当前版加载覆盖历史选择；具体触发路径仍需修复时定位，不将推断写成已确认根因。

**需要复核：历史切换不能被默认当前版加载覆盖；切换后显示对应版本的指标、权重及课程目标，历史版只读，返回当前未锁定版可维护。修复后重跑历史专项。** 本轮没有修复业务代码。

### 主任专项断言时序

原 `exp3-history-scores.cjs` 在返回当前版后立即检查新增按钮是否启用，但主任页面先赋值指标行，再异步加载目录。因此原脚本即使已看到当前版行，也可能在目录尚未完成时失败。

诊断副本保留全部业务断言，并等待新增按钮真正启用；最终主任默认当前版、历史只读及返回可维护当前版全部通过。该项属于自动化测试等待问题，应调整正式专项的就绪等待。尝试过的等待修改与诊断脚本保存在本轮证据目录，仓库原测试脚本已恢复。

## 实跑结果与证据

全部本轮证据位于 `docs/20261011-sprint2-local-evidence-v1/`；本报告没有把远端云端日志或昨日 PASS 冒称本机新实测。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 后端离线 package 与普通回归 | 237 项枚举，190 通过、47 条件跳过，0 失败/错误；JAR 构建成功 | `backend-package.log`、`backend-ordinary/`、`backend-counts.json` |
| Sprint 2 真实 MySQL | 4/4 通过，0 跳过；迁移执行两次并校验旧评价迁移 | `sprint2-mysql.log`、`sprint2-mysql.xml` |
| 后端去重计数 | 194 实际通过；43 项其他专用环境测试本轮未补跑，不计为通过 | `backend-counts.json` |
| 前端与视觉回归 | 前端 13/13、Python 无硬件 9/9；TypeScript 和生产构建成功 | `frontend-test.log`、`frontend-build.log`、`vision-tests.log`、`frontend-vision-exits.json` |
| 八故事核心浏览器流程 | 初轮与最终轮均退出 0，6 组 PASS 覆盖八故事；不是 8 个独立浏览器用例 | `eight-stories.log`、`eight-stories-final.log`、`browser-final/` |
| Office 严格补验 | 6 条结果记录全部 PASS，另包含真实超 100MB 拒绝断言 | `office-strict-final-final.log`、`office-strict-final/fix-acceptance-results.json` |
| 非默认配置补验 | 3 组全部 PASS：权重、反馈延迟及审核修改日志、上传限制及未标注显示 | `configurable-ac-final.log`、`configurable/results.json` |
| 最新评分/历史原专项 | 缺评分拒绝、明确零分及历史 API 通过；原脚本整体退出 1，多轮见教师历史异常或主任断言过早 | `history-scores.log`、`history-scores-final-final.log`、`browser-final-exits.json` |
| 最终历史诊断 | 评分 PASS、历史 API PASS、教师页面 FAIL、主任页面 PASS | `history-diagnostic-final.log`、`history-diagnostic-final/history-scores-results.json` |

配置专项前两次因本轮新增测试脚本未打开检索页或未等待弹窗完成而失败，修正测试导航/等待后 3 组通过。初轮日志分别保留为 `configurable-ac-attempt1.log`、`configurable-ac-attempt2.log`，没有把测试脚本缺陷算作业务缺陷，也没有覆盖历史失败证据。不同组存在重叠，不能将浏览器、单元测试和专项记录简单相加。

## 环境、限制和保全

- 本机 Google Chrome 154.0.8037.99、Playwright、Node v24.14.1、Java 21；独立 MySQL 8.0.36/Redis；Vue 3.5.42/Vite 5.4.21 已核对安装版本与 lock 一致。版本见 `versions.txt`。
- 原 Windows 浏览器入口需要原生 `soffice`，本机未安装。采用既有 Docker 测试方法：挂载本次新构建 JAR 到含 LibreOffice 的本机运行镜像，启动临时服务及真实 Vite 代理；没有替换业务 API 响应，不宣称已运行原 Windows runner。
- 默认 24 小时行为断言发布时间并推进隔离库中特定记录，不实际等待 24 小时；另以零小时配置验证自然即时发布。没有做生产负载或现场硬件验收。
- 前端构建存在超过 500kB 的 chunk 提示，构建成功；不把提示写成构建失败。
- `raw/README.md`、规划原件及既有 `stop_project.ps1` 修改的 SHA256 均保持不变；原件 SHA256 为 `299cd2d510e7a8da609ec2fe513e3810e616d3562673bce09ee16e81688437bb`。保护记录见 `protection.json`。
- 原始 `stop_project.ps1` 未提交修改含被拆开的 `SilentlyCo`/`ntinue`，本轮保留，未运行该脚本。它是已有本机运维问题，未计为八故事验收缺陷。
- 临时容器、网络及前端进程已停止清理，13317/13318/18081/18082/15173 无测试监听；日常三个容器 ID 与基线一致。未写入日常业务数据库，未更新 8080 日常后端镜像。见 `cleanup.json`、`containers-before.txt`、`containers-after.txt`。
- 业务源码及正式测试源码未改；新增本轮过程和证据，追加四份过程文档；未提交、推送、合并 main 或部署。日志中的 JWT 和生成密码已脱敏，源码与构建指纹另存。

## 来源与交付

来源名称：实验一正式项目规划；文件名：`D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx`；引用日期：2026-10-11。使用可用 `officecli-docx` / `officecli` Skill，只读读取原件表 6、11、16，摘录存于 `original-table6.txt`、`original-table11.txt`、`original-table16.txt`。项目约定的 `minimax-docx` 当前未提供，未生成或修改 DOCX。

来源名称：项目当前原始资料目录；文件名：`raw/README.md`；引用日期：2026-10-11。该目录只有说明文件，额外业务原始资料未提供；测试文件来自仓库 `scripts/tests/fixtures/sprint2.docx`、`sprint2.pptx` 及合成测试数据。

来源名称：当前实现、已确认背景及本轮实测；文件名：`fix-exp3 f6c741f`、`memory-bank/project-context.md`、`docs/20261011-exp3-two-bugs-fix-v1.md`、本轮证据目录；引用日期：2026-10-11。没有引入外部业务资料。

交付文件：`output/20261011-sprint2-local-acceptance-v1.md`。过程追加：`docs/source-notes.md`、`docs/analysis-notes.md`、`docs/questions.md`、`docs/plan.md`。待后续处理：教师历史切换业务问题及主任测试等待调整；评审/回顾/D5 沿用用户已确认的后续安排。
