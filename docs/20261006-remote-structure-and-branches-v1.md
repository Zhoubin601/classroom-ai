# 远端项目结构与分支分析

核对日期：2026-10-06（Asia/Shanghai）。远端：`https://github.com/Zhoubin601/classroom-ai.git`。本次执行 `git fetch origin` 和 `git ls-remote`，结构依据远端引用的 Git 文件树，而非本地未提交内容。

用户随后明确：当前正式实验阶段仍为 exp2，exp3 是提前开发。分支建议以此阶段为前提；代码所在分支和功能完成情况不代表正式实验阶段已切换。

后续状态：按用户要求已完成实验二补充分支合入 main 和历史分支清理，main 更新为 a4da3d8，远端现保留 3 个分支。以下分支表和数量是收尾前分析快照；最新执行结果见 [实验二分支收尾记录](20261006-exp2-branch-closeout-v1.md)。

## 项目结构

远端默认分支是 `main`，提交为 `7b1a589`，有 330 个跟踪文件；当前开发分支 `feat/exp3-sprint2` 为 `347a51a`，有 376 个跟踪文件。以下结构为两者共有的顶层组织：

```text
classroom-ai/
├── frontend/       Vue 3、TypeScript、Vite、Tailwind CSS、ECharts
├── backend/        Java 21、Spring Boot 3.3.4、JPA、Security、Redis
├── vision/         Python 视觉、注册、识别、诊断与测试
├── scripts/        启停、部署、数据库迁移、自动化验收
│   ├── deploy/     Docker Compose 与 MySQL 初始化/迁移 SQL
│   └── tests/      浏览器端到端与 HTTP/API 联调脚本
├── raw/            原始资料，目前远端仅 README.md
├── docs/           实施过程、审查、验收记录与截图
├── memory-bank/    长期项目背景
├── assets/         可复用素材目录，目前远端仅 README.md
├── output/         已跟踪的 Sprint 1 v2 源码包与 README.md
├── initialize.sql  根目录数据库初始化脚本
├── start_project.bat / start_project.ps1
└── stop_project.bat / stop_project.ps1
```

Sprint 2 分支文件分布：backend 179、frontend 34、vision 19、scripts 36、docs 94、assets 1、memory-bank 1、raw 1、output 2，其余 9 个为根目录文件；合计 376。

后端按 `modules/auth`、`modules/course`、`modules/resource`、`modules/supervision`、`modules/attendance` 分业务模块，并保留公共异常处理、学生、人脸、实时可视化接口。各业务模块主要采用 Controller / Service / Repository / Entity 分层。

前端由 App.vue 组织入口及角色页面，views 包含登录、主任、教师、督导、考勤大屏、学生管理及 Dashboard；components 保存指标卡、趋势图、排课板、历史开课和学生状态组件，api 保存接口与类型。

视觉核心依赖包括 OpenCV、InsightFace 和 ONNX Runtime GPU；YOLO/MediaPipe 位于可选分析依赖中。架构判断：这是前后端分离、Python 视觉进程独立运行的多模块仓库；Java 业务仍是单个 Spring Boot 应用，目录分模块不等同于独立微服务部署。

远端已有后端测试、前端 Node 测试、Python 回归、MySQL 集成及 Playwright 浏览器脚本。本轮只核对文件与提交，不执行应用、硬件或测试，不能据此认定这些测试当前通过。

本地 `.idea/`、`.venv1/`、`runtime/` 被忽略；`docker/` 未跟踪且远端无此目录，当前部署目录是 `scripts/deploy/`；本地 `design-system/` 为未跟踪目录，尚未进入远端。远端不含 node_modules、target、dist、模型缓存和本地运行上传资料，完整部署还需按启动脚本准备依赖。

## 远端分支

下表“主线独有 / 分支独有”来自 `git rev-list --left-right --count origin/main...分支`，计入合并提交，不代表文件数量或功能数量。

| 远端分支 | 提交 | 主线独有 / 分支独有 | 核对结果 |
| --- | --- | --- | --- |
| main | 7b1a589 | 0 / 0 | 默认主线，Sprint 1 与实验二阶段 |
| feat/exp3-sprint2 | 347a51a | 2 / 4 | Sprint 2 开发成果，尚未合入 main |
| backup/pre-exp3-20260923 | 36605fb | 2 / 1 | 实验三前代码备份，是 Sprint 2 的祖先 |
| feat/exp2-supplement-auth-fix | 64a88f2 | 6 / 1 | 实验二补充分支，存在独有合并提交 |
| feat/foundation-auth | 4e9ce00 | 23 / 0 | 所有提交已包含于 main 与 Sprint 2 |
| feat/us01-us02-course-content | 6e9642f | 21 / 0 | 所有提交已包含于 main 与 Sprint 2 |

main 与 Sprint 2 的共同祖先为 `40b9c3a`。两边的独有提交为：

```text
40b9c3a
├── main 一侧：2f19dbc → 7b1a589
└── Sprint 2 一侧：36605fb → 4f3f524 → 3a799ee → 347a51a
```

此图只表达共同祖先和独有提交序列，完整合并父节点以 `git log --graph --all` 为准。

特别核对：`origin/main` 与共同祖先 `40b9c3a` 的 tree SHA 相同，`git diff --quiet` 返回 0。因此 main 的两个独有提交增加了提交历史，未产生相对共同祖先的净文件变化。不能把“落后 main 两个提交”直接理解为缺少两个代码修复。

Sprint 2 相对 main 有 101 个文件变化，4700 行增加、2101 行删除（不包含本地未提交改动）。新增内容集中在培养方案指标与大纲映射、资源共享及多标签、安全预览、督导审核和反馈、覆盖率统计、`06_exp3_sprint2.sql`、启动部署及端到端验收。视觉目录和 README 相对 main 无差异。

`feat/exp2-supplement-auth-fix` 虽然不是 main 的祖先，但其 `backend/src/main/java`、`frontend/src`、`vision`、`scripts/tests` 与 main 的文件内容一致。差异位于 README、pom、后端配置、数据库初始化、启动脚本及过程文档。仅凭分支名不能认定它含有 main 缺失的新业务功能。

本地另有 `feat/us03-us04-us06-schedule-query`，提交为 `b315ee4`；实时远端分支列表没有它，且其提交已包含在 main 中。

## 本地工作区与远端的区别

当前本地分支为 `feat/exp3-sprint2`，已提交 HEAD 与远端同为 `347a51a`，领先/落后均为 0。

分析开始时已有 19 个跟踪文件被修改：3 个过程文档、14 个前端文件、2 个启动/验收脚本；另有未跟踪的 design-system 目录和 Sprint 1 v3/v4 两个源码 ZIP。已跟踪的本地差异共增加 2414 行、删除 1655 行，未跟踪文件未计入。它们均未包含在远端分支文件树中。

## 标签核对

| 标签 | 本地实际目标提交 | 远端实际目标提交 | 状态 |
| --- | --- | --- | --- |
| baseline-20260918 | cd8fd55 | cd8fd55 | 一致，远端为附注标签 |
| v0.1.0-sprint1 | 3d93ad7 | 688a47d | 同名异指；本地是轻量标签，远端是附注标签 |
| v0.1.0-sprint1-v3 | df21855 | 远端不存在 | 仅本地存在 |

远端目标使用 `git ls-remote` 的解引用结果核对。普通 fetch 未将已有本地同名标签替换为远端目标，因此分支引用同步不代表标签一致。

## 判断与建议

- 当前正式阶段为 exp2：main 作为实验二维护和交付的候选基线，只接收经核对属于实验二范围的修复；其具体交付提交仍需验收和标签核对。
- feat/exp3-sprint2 保持为提前开发分支，当前暂不合入 main；待正式进入实验三并完成相关验收，再评估整合。
- 本地未提交界面和脚本改动需先保存并区分阶段归属。共用修复可审查后单独回移至实验二；不能把实验三迁移和新增流程一并带入实验二交付。
- feat/exp2-supplement-auth-fix 在实验二收尾期间保留，用于核对补充修复；backup/pre-exp3-20260923 暂时保留。本轮没有进行分支切换、合并、提交或推送。
- foundation-auth 与 us01-us02 的提交已进入主线，可作为历史功能分支处理；是否删除分支不在本次分析范围。
- 待确认：`v0.1.0-sprint1` 应采用哪一个提交作为正式发布基准，以及本地 v3 标签是否需要发布。本轮只记录差异，不移动或推送标签。
- README 和 memory-bank 中“根目录只保留一个启动入口”的历史说明与当前远端四个启停入口不一致，应以当前文件树核对入口；正式整理时再更新说明。

## 来源与验证边界

- 原始资料：raw/README.md，仅为目录规则说明；本轮未提供新的业务原始资料。
- 本地背景：AGENTS.md、docs/source-notes.md、docs/analysis-notes.md、docs/plan.md、docs/questions.md、memory-bank/project-context.md。历史通过记录未作为当前测试结果使用。
- 来源名称：classroom-ai Git 远端；链接：`https://github.com/Zhoubin601/classroom-ai`；引用日期：2026-10-06。
- 代码依据：远端分支的文件树、frontend/package.json、backend/pom.xml、vision/requirements.txt、vision/requirements-analysis.txt、README.md、.gitignore，以及 Git 提交、分支和标签关系。
- 验证：实时远端查询、提交计数、祖先判断、文件树计数、指定路径差异和标签目标交叉核对。未验证线上部署、GitHub PR、保护分支规则或 CI 当前运行状态；这些不属于本次结构与分支分析。
- 本次只新增此过程记录并追加相关 docs 笔记，不修改业务代码或 raw，不生成正式 Office/PDF 文件，故不调用文件生成 Skill，也不新增 output 交付物。
