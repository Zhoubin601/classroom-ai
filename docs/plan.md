# 爱教学平台分层解耦与功能完善执行计划 (含摄像头智能考勤) (plan.md)

## 2026-09-26 实验三 Sprint 2 代码补齐与 Chrome 验收
- 按实验一项目规划方案的 US-05、US-07～10、US-13～15 核对现有实现，保留真实培养方案数据由主任导入的边界。
- 补齐教师端教研室共享资源检索、资源标签与共享范围的后续维护；服务端校验更新字段并保留文件、版本、水印元数据。
- 用常规回归、隔离 MySQL 测试和本机 Google Chrome 的真实页面流程验收；测试过程、用例、结果写入 `docs/`，不制作正式提交文档。

## 1. 任务背景与目标
依据东北大学软件学院《软件项目管理》实验一交付报告（背景 B “爱教学”全链路数字化管理平台）及用户对“打开摄像头考勤等功能”的明确要求：
1. **架构解耦（Decoupled Architecture）**：
   - 将原散落在根目录的 Python 视觉推断与人脸识别脚本收拢解耦至 `services/edge-perception/`，保持其作为独立边缘微格与视觉感知模块；
   - 杜绝视觉脚本与中心业务的硬编码侵入，通过标准 HTTP/RESTful 接口通信；
2. **核心业务后端模块化领域分层（Backend Modular Layering）**：
   - `modules.course`：全量课程档案CRUD、开课排课防冲突看板、12条毕业要求指标点矩阵、多维复合筛选；
   - `modules.resource`：多版本课件教案上传（≤100MB）、理论/实验/研讨环节标签、免密只读预览与水印、微格切片标准挂载（US-12）；
   - `modules.supervision`：BOPPPS四维100分随堂评教、24h延迟脱敏流转、督导覆盖率大屏、<30%/<75分红黄预警、质量雷达图、报表导出；
   - `modules.attendance` & `modules.face`：摄像头考勤会话管理、1:N人脸识别比对、出勤率与抬头率时序汇聚、考勤数据与排课课程自动归档；
3. **前端多角色一体化门户（Vue 3 Frontend）**：
   - 👔 教研室主任工作台（排课看板/指标矩阵/报表一键导出）
   - 👨‍🏫 任课教师工作台（大纲编写/教案上传/质量雷达图）
   - 🕵️ 教学督导工作台（多维检索/免密预览/BOPPPS打分/覆盖率预警）
   - 🎥 课堂智能考勤与态势监控大屏（一键开启摄像头、画中画HUD、实时1:N人脸考勤点名、抬头率波形流、考勤归档）
   - 👤 学生档案与人脸库管理（网页端实时取景拍照秒级录入 / 文件上传）
4. **真实教务数据 PoC**：
   - 预置东北大学真实教学数据（如郭军老师《软件项目管理》文管A447 95人、姜琳颖老师《计算机组成原理》信息馆B201 120人、12条毕业要求指标点等）。

## 2026-09-16 运行、测试与修复
- 依据当前用户指令检查并启动现有应用，修复可复现缺陷，增加自动化回归测试。
- 顺序：基线构建和测试 → 服务启动与接口检查 → 修复及回归 → 记录运行入口与验证边界。
- 不改动 raw/，不使用已有真人脸样本做写入测试；测试使用独立合成数据。

## 2026-09-17 浅色简约风 UI 全量重构规划 (依据用户要求)
* **执行前置与安全基线**：
  - 阶段 0：**全量无损备份** —— 将 `frontend/src/`、`frontend/tailwind.config.js`、`frontend/src/style.css` 完整备份至 `backup/frontend_backup_20260917/`，保留随时一键回滚能力；
* **实施阶段推进**：
  - 阶段 1：**全局基础层重构**
    - `tailwind.config.js`：清理 cyber 暗色变量与发光 glow 阴影，配置 slate 中性调与微色温语义色；
    - `style.css`：切换 `color-scheme: light`，全局重置为纯白与柔和浅灰，实现 `.minimal-card` 与浅色滚动条；
  - 阶段 2：**门户框架与导航外壳重构**
    - `App.vue`：顶部导航栏转为纯白高透磨砂质感，集成 Lucide 矢量图标，优化全员工作台切换 Tabs 与状态指示器；
  - 阶段 3：**基础可视化与指标组件重构**
    - `MetricCard.vue`：白底简约指标卡，去渐变彩条与光晕；
    - `FocusTrendChart.vue`：ECharts 浅色主题适配（白底、浅灰虚线网格、精致阴影 Tooltip）；
    - `StudentStatusGrid.vue`：浅色网格矩阵，柔和语义胶囊标签与状态呼吸灯；
  - 阶段 4：**多角色工作台与大屏全量重构**
    - `DirectorDeskView.vue`、`TeacherDeskView.vue`、`SupervisorDeskView.vue`、`AttendanceDashboardView.vue`、`StudentManageView.vue`、`DashboardView.vue`；
    - 全面以 Lucide 矢量图标替换 Emoji，全面转换表格、表单输入、弹窗对话框为浅色高质感风格；
  - 阶段 5：**验证与合规交付**
    - 运行 `npm run build`（`vue-tsc -b && vite build`）保证 0 编译与类型错误；
    - 运行 Impeccable 质量检查，确认对比度、留白、边框与可访问性达标。


## 2026-09-18 项目目录重构
用户已明确前端、后端、Python 视觉的划分方向。执行：备份与文件清点 → 文件迁移 → 修复引用 → 测试验证 → 更新运行说明。详见 [重构记录](refactor-20260918.md)。


## 2026-09-18 根目录进一步收拢
用户指出根目录文件仍多，要求实际整理并写好说明。保留前端、后端、视觉、scripts 和五个规定目录；零散日志与上传数据集中 runtime/，部署和联调测试下沉 scripts/，backup 与旧入口归档 docs/archive/，根目录只保留 start_project.ps1 一个启动入口。移动 raw 以外文件，修正依赖路径，校验文件完整性与构建后更新 README 和详细迁移说明。不调用摄像头、不更改真实数据库记录。

本次根目录整理已完成：33 → 16 项，后端35/前端10/Python5项通过，构建和 Compose 配置校验通过。详细迁移和检查结果见 [目录整理说明](目录整理说明.md)。


## 2026-10-03 UI/UX Pro Max 全页面现代化重构执行计划
- 阶段 1：全局样式与配置升级 (frontend/src/style.css, frontend/tailwind.config.js)
  - 引入现代设计系统变量（阴影、边框、过渡色阶、精致表格与表单基准类）。
- 阶段 2：通用组件与导航外壳现代化重构
  - frontend/src/App.vue: 头部微磨砂 Header、品牌渐变徽章、现代胶囊式 Tab 控制器、系统状态与用户信息卡。
  - frontend/src/components/MetricCard.vue: 现代精致白底指标卡，带微妙阴影与渐变图标底色。
  - frontend/src/components/FocusTrendChart.vue: ECharts 现代化配色（平滑柔和渐变与精致 tooltip）。
  - frontend/src/components/OfferingScheduleBoard.vue & OfferingHistoryPanel.vue: 排课日程与开课历史面板视觉重塑。
  - frontend/src/components/StudentStatusGrid.vue: 学生状态网格芯片重构。
- 阶段 3：多角色工作台与大屏全量重构
  - frontend/src/views/LoginView.vue: 极具视觉冲击力的科技教务登录界面，Bento Grid 快捷直登面板。
  - frontend/src/views/DirectorDeskView.vue: 主任工作台（指标卡、课程档案表、排课统筹矩阵、培养方案导入、督导授权与审核流）。
  - frontend/src/views/TeacherDeskView.vue: 教师工作台（大纲简介草稿/发布、课件教案上传与多标签筛选、安全水印预览、BOPPPS雷达图与匿名评价）。
  - frontend/src/views/SupervisorDeskView.vue: 督导工作台（全院总课表日历矩阵、免密预审、BOPPPS滑块量化表单、覆盖率巡检大屏、红黄预警中心）。
  - frontend/src/views/AttendanceDashboardView.vue: 课堂智能考勤态势大屏（科技感大屏、波形图、学生状态网格）。
  - frontend/src/views/StudentManageView.vue: 学生档案与人脸库中心（指标看板、学生花名册、特征向量可视化、人脸采集模态框）。
- 阶段 4：编译检查与自动化测试验证
  - 执行 `npm run test` 与 `npm run build`，确保 0 报错。
  - 验证端到端测试契约完整无损。


## 2026-10-06 远端结构与分支分析执行记录
1. 已读取 raw、docs、memory-bank，确认现有规则及历史背景。
2. 已 fetch origin 并实时核对远端分支、默认分支和标签。
3. 已按远端 Git 文件树分析模块、部署、测试目录和提交关系，区分本地未提交内容。
4. 已写入 docs/20261006-remote-structure-and-branches-v1.md，追加来源、判断和待复核事项。
5. 本次范围为分析；后续分支整合与标签处理等待用户明确任务，不生成未经确认的正式交付文件。

## 2026-10-06 实验二分支收尾执行计划
- 用户已要求把实验二补充分支合入 main，并清理历史分支；exp3 仍为提前开发。
- 已完成远端更新、共同祖先与合并树预检；独立 main 工作区的合并结果与原 main 文件树相同，无冲突。
- 后续依次创建合并提交、验证阶段边界、同步远端、按祖先关系清理已合入的实验二分支、核对原工作区文件哈希。
- 保留 feat/exp3-sprint2 和 backup/pre-exp3-20260923，不处理标签或用户文件。

## 2026-10-06 实验二分支收尾完成状态
- 合并、远端同步、已合入历史分支清理和原工作区保护核对均已完成。
- main 与 origin/main 同为 a4da3d8；exp3 和实验三前备份保持原提交。
- 文件树不变，未重新打包或调整标签；既有发布前人工复核事项沿用 docs/questions.md。

## 2026-10-06 main 启动核验计划
- 用户要求核对 main 能否启动和实验二完善情况；已创建独立 main 工作区。
- 分别执行后端测试/打包、前端测试/构建、Python 无硬件回归，再验证隔离数据库及实际运行链路。
- 当前 Docker 原先未运行，已通过 docker desktop start 启动；不操作既有 MySQL 数据库。详细记录：docs/20261006-main-startup-verification-v1.md。

## 2026-10-06 main 启动核验完成状态
- 已完成源码检查、构建、普通回归、31 项真实 MySQL 测试和三角色五故事浏览器验收，以及生产 jar/前端实际启动核验。
- 原 main 冷启动阻塞已复现；本地修复分支 codex/main-startup-fix-20261006（a962506）已验证，尚未推送到 main。
- 自带课程/开课专业归属缺失已记录于 questions.md，未猜填。测试进程和隔离容器已清理，修复工作区保持干净并保留供审阅。
# 2026-10-06 实验三就绪核验计划

1. 按既有 Sprint 2 的 8 条故事核对，不改变用户确认的正式 exp2 阶段。
2. 在独立工作区核对远端已提交源码，执行构建、普通回归、真实 MySQL 和浏览器验收。
3. 单独验证本地未提交界面，修复验收发现的标签或定位问题，保留用户已有样式修改。
4. 使用冷启动隔离库与生产 Docker 的 LibreOffice 检查启动、权限和真实 Office 转换；不写入既有业务库。
5. 区分功能验证、真实资料核对、团队评审及正式发布状态，留过程记录并清理本次自建测试环境。

# 2026-10-06 实验三就绪核验执行结果

- 远端/本地版本区分、构建、8 条实验三故事、5 条实验二兼容故事、隔离 MySQL 与生产 Docker Office 转换核验完成。
- 校正既有规则下的旧权限测试、补齐排课输入名称、稳定审核卡片及历史脚本定位，本地复测通过。
- 证据保存至 docs/exp3-readiness-evidence-20261006-v1/；详细说明及正式完成缺口见 docs/20261006-exp3-readiness-verification-v1.md。
- 临时预览与隔离容器已清理，正式阶段仍为 exp2；本轮未提交、未推送、未改分支/标签。

## 2026-10-06 用户授权完善 exp3 并合入 main

- 用户已确认方向并要求真实 Playwright 验收；完成源码与本地 UI 收拢、独立合并候选测试、提交并推送 main，不再等待本轮代码合并确认。
- 保留真实资料缺口；不生成虚构的培养方案、会议或正式实验交付文件。详细过程：docs/20261006-exp3-main-integration-v1.md。

## 2026-10-06 exp3 main 集成完成

- 真实 Playwright 验收、144 项后端分轮回归、前端 10 项和 Python 5 项通过；生产 Docker 上全功能 24 项及 DOCX 水印预览通过。
- exp3 完善提交 66b2d9e，main 两父合并提交 1a31d1e，已原子推送并核对远端引用；原工作区切换 main。
- 临时服务已清理，原始资料、旧输出包、设计记录与既有业务数据保留。正式资料待复核项见 questions.md，本轮代码任务已完成。

