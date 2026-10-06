# 原始资料与代码文件索引 (Source Notes)

本文档记录项目中已有核心源码与模型资产的来源与功能映射：

## 1. 算法与测试源码
* [camera_test.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/camera_test.py): 摄像头视频流读取与显示测试 (OpenCV `VideoCapture`)
* [check_ort.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/check_ort.py): ONNX Runtime GPU 环境检查与 ExecutionProvider 诊断
* [test_mp.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/test_mp.py): MediaPipe 模块接口与安装诊断
* [yolo_test.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/yolo_test.py): YOLOv11 目标检测基础验证脚本
* [face_test.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/face_test.py): 基于 MediaPipe Tasks API 的人脸 468 网格点绘制
* [head_pose_test.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/head_pose_test.py): 单人 3D 头部姿态解算 (solvePnP + Pitch/Yaw/Roll 计算)
* [face_register.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/face_register.py): 人脸特征注册录入 (InsightFace 提取 512维 embedding 保存为 `face_db.npy`)
* [face_recognition.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/face_recognition.py): 实时人脸识别 (余弦相似度比对，已验证全部 5 个模型由 GPU 驱动)
* [yolo_face_analysis.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/yolo_face_analysis.py): 课堂多人综合分析流水线 (YOLO11检测人体 -> 截取人脸关键点 -> 姿态估计 -> 抬头率统计)

## 2. 预训练权重与资产
* [yolo11n.pt](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/yolo11n.pt): YOLOv11 nano 预训练权重 (~5.6MB)
* [face_landmarker.task](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/face_landmarker.task): MediaPipe FaceLandmarker 模型包 (~3.7MB)
* [face_db.npy](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/face_db.npy): 已注册人脸特征向量库 (512 维 numpy 数组)

## 3. 云端后端与存储模块规划 (Backend & Storage)
* [backend/](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/backend): Spring Boot 3.x 后端项目主目录
* [backend/Dockerfile](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/backend/Dockerfile): Spring Boot 后端多阶段构建 Docker 镜像定义
* [docker-compose.yml](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/docker-compose.yml): 包含 MySQL 8.0、Redis 7.x 与 Spring Boot 后端的全套容器化编排
* [docker/mysql/init/01_schema.sql](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/docker/mysql/init/01_schema.sql): MySQL 初始化建表脚本（学生档案表与 512 维人脸特征表）
* [backend/README.md](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/backend/README.md): 后端系统架构、数据表字典、Redis 缓存与全套 RESTful API 说明
* [tests/test_backend_api.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/tests/test_backend_api.py): 后端人脸存取、Redis 缓存同步与前端大屏接口全链路自动化测试脚本
* [start_services.ps1](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/start_services.ps1): Docker 中间件与后端服务快速启动辅助脚本

## 4. 前端可视化大屏与管理系统 (Frontend & Dashboard)
* [frontend/](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/frontend): Vue 3 + Vite + TypeScript + Tailwind CSS + ECharts 前端工程主目录
* [frontend/src/views/DashboardView.vue](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/frontend/src/views/DashboardView.vue): 课堂大屏实时监控主页面（宏观指标卡片、抬头率波形时序折线图、学生在座与姿态网格卡片）
* [frontend/src/views/StudentManageView.vue](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/frontend/src/views/StudentManageView.vue): 学生档案与人脸库管理中心（特征录入、1:N 检索测试、学生增删改查）
* [frontend/src/components/FocusTrendChart.vue](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/frontend/src/components/FocusTrendChart.vue): Apache ECharts 抬头率与专注度时序平滑曲线组件
* [frontend/src/api/index.ts](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/frontend/src/api/index.ts): 对接 Spring Boot 后端全量 API 客户端封装
* [register_face.bat](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/register_face.bat): 交互式一键人脸特征录入批处理脚本
* [classroom_monitor.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/classroom_monitor.py): 智能课堂实时视觉感知与督导推断模块 (摄像头实时检测 + 1:N 人脸匹配 + Pitch 姿态解算 + 抬头率统计 + 异步上报后端)

## 5. 软件项目管理实验一交付报告 (背景 B “爱教学”平台)
* **资料来源**：用户提供的《东北大学软件学院《软件项目管理》实验报告 实验一：项目启动与规划交付报告 —— 依托背景 B“爱教学”教学质量数字化管理平台》
* **团队构成**：第二组，4 人自然人主体（周宇斌 20246085 / 郭振顺 20246074 / 王嘉伦 20245727 / 沈越 20245796）+ 3 个 AI 智能体（Agent-Req、Agent-Plan、Agent-Review）
* **项目愿景**：面向教研室主任、任课教师（如郭军、姜琳颖老师等）、教学督导，构建“让教学质量持续可测”全链路数字化管理平台。
* **业务演进路径**：阶段 1 查课程 (Sprint 1) ➔ 阶段 2 善督导 (Sprint 2) ➔ 阶段 3 优课堂 (Sprint 3)。
* **合规红线与剔除项**：
  1. 坚决排除未经师生授权的教室摄像头实时抓拍与人脸识别考勤等高违规风险特性（红线禁止）；
  2. 坚决排除基于计算机视觉的学生微表情、疲劳度、走神热力图等华而不实的伪需求（红线禁止）。
* **核心史诗与故事**：
  - Epic 1: 课程信息管理（US-01 课程档案CRUD、US-02 在线简介大纲、US-03 开课统筹排课看板、US-04 教师开课历史人次、US-05 12条毕业要求指标点矩阵、US-06 督导多维复合检索）
  - Epic 2: 教学资源管理（US-07 多版本课件教案上传单文件100MB、US-08 教学环节标签理论/实验/研讨、US-09 督导免密在线预览、US-10 教研室优秀资源共享、US-11 动态水印与版本树、US-12 微格视频切片与课堂录像元数据挂载 RESTful API）
  - Epic 3: 教学评价管理（US-13 BOPPPS四维随堂听课打分、US-14 质性评价亮点建议限500字与24小时延迟脱敏归档、US-15 全院督导覆盖率动态看板、US-16 零督导<30%或低分<75分黄红预警、US-17 教师质量四维雷达图与词云、US-18 教研室年度质量报表导出Excel/PDF、US-19 督导共性问题热力分布、US-20 评教权重与模板管理、US-21 BOPPPS持续改进建议与微格资源Webhook）
* **思考题 4 核心技术 PoC 目标**：
  - 跑通教务核心数据模型“课程档案 (Course) - 教师开课 (CourseOffering) - 排课时段教室 (CourseSchedule) - 教学大纲指标点 (CourseSyllabus & IndicatorMapping)”四表关联，灌入东北大学真实教学数据（郭军老师《软件项目管理》文管A447 95人、姜琳颖老师《计算机组成原理》信息馆B201 120人等）。


## 2026-09-16 本次检查来源
- raw/README.md：未提供额外原始需求资料。
- frontend/package.json、vite.config.ts、vite.config.js、src/：前端启动、接口与摄像头中间件实现。
- backend/pom.xml、src/main/、src/test/：后端实现、配置与既有测试。
- docker-compose.yml、start_services.ps1、start_frontend.ps1：服务启动依据。
- docs/ 与 memory-bank/project-context.md：历史背景，仅作参考，当前运行结果重新核实。

## 2026-09-17 UI 浅色简约风格重构输入与目标清单
* **用户输入要求**：
  - 风格定位：浅色系、简约风；明确拒绝暗黑科技风/赛博朋克风/荧光发光质感；
  - 安全原则：必须先完整备份现有前端源码与配置，再执行全量重构。
* **重构受控源文件范围**：
  - 全局配置与样式：`frontend/tailwind.config.js`, `frontend/src/style.css`, `frontend/index.html`
  - 核心外壳与导航：`frontend/src/App.vue`
  - 公共组件：`frontend/src/components/MetricCard.vue`, `frontend/src/components/FocusTrendChart.vue`, `frontend/src/components/StudentStatusGrid.vue`
  - 五大多角色视图：
    - `frontend/src/views/DirectorDeskView.vue` (教研室主任工作台)
    - `frontend/src/views/TeacherDeskView.vue` (任课教师工作台)
    - `frontend/src/views/SupervisorDeskView.vue` (教学督导工作台)
    - `frontend/src/views/AttendanceDashboardView.vue` (课堂智能考勤监控大屏)
    - `frontend/src/views/StudentManageView.vue` (学生人脸档案库)
    - `frontend/src/views/DashboardView.vue` (历史概览兼容视图)


## 2026-09-18 项目目录重构来源
详见 [本次重构记录](refactor-20260918.md)。依据现有前后端、根目录视觉源码、启动脚本和测试进行整理；raw/ 无新增需求资料，无外部资料。


## 2026-09-18 根目录整理来源
用户截图与当前项目根目录清单；scripts/ 启动脚本、Docker Compose、前端摄像头适配器、Java 图片上传/静态映射、vision/paths.py。截图用于识别目录杂乱问题，不作为额外操作指令。raw/README.md 未提供新增原始资料。

## 2026-09-18 全量教务数据底座与初始化脚本资产
* [initialize.sql](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/initialize.sql): 包含 5 专业、8 专任教师、10 账号、17 专业课程、8 班级 80 学生、80 条 512 维合规人脸向量底库、17 开课与排课日程（支持同时间段多教室并行）、80 选课真值及 12 项工程认证指标点的规范化初始化 SQL。
* [scripts/generate_initialize_sql.py](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/scripts/generate_initialize_sql.py): Python 自动化 SQL 生成器，可复现生成合规 L2 正则化 Float 特征向量与全量教务排课数据。
* [scripts/deploy/mysql/init/initialize.sql](file:///d:/2026Autumn%20Semester%20File/classroom-ai-demo/scripts/deploy/mysql/init/initialize.sql): 备份初始化脚本，供 Docker 自动化挂载部署。



## 2026-09-20 US-02 / US-03 实施来源
- 用户当前提供的第 3、4 步实施内容与通过条件为本轮验收范围。
- 原始文件：D:/2026Autumn Semester File/软管/实验二/raw/实验二规划.txt；沿用已整理背景中的文管 A447、95 人演示要求。未改写 raw，未使用外部资料。
- 实现来源：D:/2026Autumn Semester File/classroom-ai-demo 现有源码及上一轮 US-01/US-02 修复记录；所有既有未提交改动保留。
- 验证使用独立临时 MySQL 的合成师生名单；95 人用于复现样例展示，不代表核实真实在读名单。

## 2026-09-21 联合验收来源
- 当前用户提供的 US-04、US-06、兼容约定与第 7 步交付条件；未引入外部资料。
- 原始规划沿用实验二/raw/实验二规划.txt。旧七项交付来自实验二/output/，仅只读核对，原哈希保存在 docs/delivery-audit-20260920/original-sha256.json，复核无变化。
- 代码提交 25fd7850e652b3cc31a92aa41bc40dfc4def372b；实际测试与人工待核对事项见 docs/20260921-联合验收与AI代码审查-v1.md。
- 所有测试名单和未授权专业记录均为独立临时 MySQL 内合成数据，不证明业务库真实人数或归属。

## 2026-09-22 v3源码包新环境启动失败排查与v4包重构
- 资料来源：用户微信传输的启动报错日志 `新建 文本文档.txt` (由 `start_project.bat` 运行产生)。
- 关键现象：
  1. Docker 基础服务（MySQL 8.0, Redis 7.2）正常就绪；
  2. 后端检测到缺少预编译 jar 包，触发本地 `mvn package` 构建；
  3. Maven 编译阶段抛出 100 个编译错误（`找不到符号: 方法 <T>builder()`, `变量 log`, `方法 getStudentId()` 等），导致 `BUILD FAILURE`；
  4. 根因确认为 `backend/pom.xml` 中 `maven-compiler-plugin` 缺少 Lombok 注解处理器路径 `<annotationProcessorPaths>`，导致 Java 21 环境下编译期 Lombok 注解未执行代码生成。
- 重构交付范围：
  1. `backend/pom.xml` 显式引入 Lombok 注解处理器路径；
  2. 修复 `scripts/start_project.ps1` 中的 Maven 检索路径（补充内置 `backend/.tools` 兜底）；
  3. 执行 `clean package` 并在源码包中打包预编译的 `backend/target/classroom-backend-0.0.1-SNAPSHOT.jar`，实现开箱即用免编译直接启动；
  4. 产出 `20260922-第二组-Sprint1源代码-v4.zip`。

## 2026-09-22 live业务库数据固化为基准初始化数据来源
- 资料来源：用户当前开发机 Docker 运行容器 `classroom-mysql` 内实际运行库 `classroom_ai`。
- 提取工具与命令：
  - 容器内无损 UTF-8 导出：`docker exec classroom-mysql mysqldump -uroot -proot --default-character-set=utf8mb4 --single-transaction --hex-blob -r /tmp/dump.sql classroom_ai`；
  - 完整二进制传输：`docker cp classroom-mysql:/tmp/dump.sql ...`。
- 固化目标文件：
  - `initialize.sql`（仓库根目录基准初始化脚本，646,817 字节）
  - `scripts/deploy/mysql/init/initialize.sql`（部署挂载同步副本，646,817 字节）
- 关键业务数据规模：
  - 20 张业务表结构与全部数据，包括 `classroom_record`（1131 条）、`face_feature`（80 条）、`student`（80 条）、`t_user_account`（16 条，涵盖主任/督导/教师及 BCrypt 加密凭据）、`t_course`（18 条）、`t_course_offering`（17 条）、`t_course_schedule`（17 条）、`t_offering_student_enrollment`（170 条）。
- 部署与启动加固：
  - `scripts/deploy/docker-compose.yml` 调整为单文件挂载 `./mysql/init/initialize.sql:/docker-entrypoint-initdb.d/initialize.sql:ro`；
  - `scripts/start_project.ps1` 采用 `docker cp` + `source` 执行双层自动灌入。

## 2026-09-23 实验三 Sprint 2 实施来源
- 用户在当前任务中给出的实施计划：US05、US07–US10、US13–US15；默认反馈延迟 24 小时、预览链接 5 分钟、文件上限 100 MB；要求当前项目直接开发、运行、测试，不制作提交物文档。
- 原始资料目录 `raw/` 本轮只有 `README.md`，未提供新的培养方案指标正文；实际目录须由有权限的教研室主任导入，不在代码中编造指标内容。
- 代码与数据基线：本仓库原有源码、`initialize.sql` 和运行中的 `classroom_ai`。迁移前 SQL 备份位于 `runtime/backups/pre-exp3-20260923-classroom_ai.sql`；代码基线已推送至 `backup/pre-exp3-20260923`。
- 本轮未使用外部资料；浏览器与 MySQL 验收使用独立临时数据库。

## 2026-09-26 Sprint 2 补齐与验收来源
- `D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/实验指导书_实验1_项目启动与规划_4学时.docx`：指导书要求至少三个 Sprint 发布切片，并将“看课堂”列为后续产品进阶方向。
- `D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx`：表 6 明确 Sprint 2 为 US-05、US-07～10、US-13～15；表 11 列集成、安全验证与中期评审。
- 当前仓库源码、隔离 MySQL 和本机 Google Chrome 是本次代码与页面验收依据。真实培养方案指标正文仍未提供，浏览器用例采用明确标为合成的隔离测试指标。



## 2026-10-03 UI/UX Pro Max 资源与设计规范依据
- 设计工具与技能：ui-ux-pro-max (C:\Users\a3185\.gemini\config\skills\ui-ux-pro-max\SKILL.md)
- 生成的设计系统 Master 资产：design-system/classroomai/MASTER.md
- 视觉参考体系：Modern Academic SaaS / Precision Intelligence Platform / Swiss Modernism 2.0
- 图标库规范：统一遵循 Lucide 矢量图标，杜绝 Emoji 作为系统交互图标。
- 业务契约依据：scripts/tests/all-features-playwright.cjs 与已有各 Vue 单文件组件中的接口协议。

## 2026-10-06 远端结构与分支分析来源
- 用户当前要求：分析当前远端项目结构和分支。
- 原始资料：raw/README.md，仅目录规则说明；新增业务原始资料未提供。
- 来源名称：classroom-ai Git 远端；链接：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。
- 本轮执行 git fetch origin 和 git ls-remote，以实时远端分支、标签及 Git 文件树为依据；读取 AGENTS.md、现有 docs 笔记和 memory-bank/project-context.md 作背景。
- 代码依据：远端 frontend/package.json、backend/pom.xml、vision/requirements*.txt、README.md、.gitignore 及源码路径；没有引入额外网络资料。
- 详细结果：docs/20261006-remote-structure-and-branches-v1.md。未改动 raw，未将历史测试结果认定为本轮通过。

## 2026-10-06 实验阶段口径来源
- 来源：用户当前消息“我们现在的实验还处于 exp2 的状态，exp3 是提前做的”。
- 已确认：正式实验阶段为 exp2；exp3 是提前开发。此口径优先于根据分支名和代码功能作出的阶段推断。

## 2026-10-06 实验二分支收尾来源
- 用户当前要求：将 feat/exp2-supplement-auth-fix 合入 main，完成实验二收尾并清理其他历史分支。
- 来源名称：classroom-ai Git 远端；链接：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。
- 来源命令：fetch、ls-remote、merge-base、merge-tree、write-tree；本轮没有新增业务原始资料。
- 合并前 main 为 7b1a589、补充分支为 64a88f2；预检及独立工作区暂存合并 tree 与原 main 相同。
- 详细过程：docs/20261006-exp2-branch-closeout-v1.md；raw 不改写。

## 2026-10-06 实验二分支收尾结果依据
- 合并提交：a4da3d8de421bca9549d8ce302537dd084752fca；父提交：7b1a589、64a88f2。
- 合并前后 tree 均为 758eb9645a8474e603f461cf30511e09ff755e05，git diff --quiet 返回 0。
- 原子推送成功后 ls-remote 再次核对：main=a4da3d8、feat/exp3-sprint2=347a51a、backup/pre-exp3-20260923=36605fb，仅保留这三个远端分支。
- 18 个受保护文件合并前后 SHA256 一致；raw 无差异。过程记录：docs/20261006-exp2-branch-closeout-v1.md。

## 2026-10-06 main 启动核验来源
- 用户当前要求、main 提交 a4da3d8、主线启动/测试脚本及 docs/questions.md 中既有验收边界；没有新增原始业务资料。
- 来源名称：classroom-ai Git 远端；链接：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。
- 使用隔离 main 工作区，不读取 exp3 未提交代码作为主线验证依据，不修改 raw。

## 2026-10-06 main 启动核验实测依据
- 原 main a4da3d8 的整目录 MySQL 初始化在 05_us04_archive.sql 第 4 行复现 ERROR 1146，缺少 t_course_offering；隔离容器退出码 1。
- 本地修复 a962506 将 Compose 改为只读挂载完整 initialize.sql，配置校验、全新数据库和生产 jar 启动通过；远端 main 未改变。
- 后端报告分轮汇总 139 项、0 失败、0 错误、0 跳过；前端 10 项与构建通过；Python 5 项通过。
- 数据完整性依据为隔离冷启动库的 SQL 计数：17 门课程与 17 条开课均缺少专业 ID/编码，默认督导可见课程为 0。没有查询或修正真实业务库。
- 记录与证据：docs/20261006-main-startup-verification-v1.md、docs/main-startup-evidence-20261006-v1/；浏览器使用合成名单和真实 API，不保存登录凭据或令牌。
# 2026-10-06 实验三就绪核验来源

- 来源名称：用户当前实验阶段与本次核验要求；文件名：当前会话；引用日期：2026-10-06。正式阶段仍为 exp2，exp3 为提前开发。
- 来源名称：项目原始资料目录；文件名：raw/README.md；引用日期：2026-10-06。未提供新增的真实培养方案指标正文，本轮不据此判断指标内容准确性。
- 来源名称：既有实验三范围与历史验收；文件名：docs/20260926-实验三-Sprint2-Chrome验收记录.md；引用日期：2026-10-06。范围为 US-05、US-07～10、US-13～15，共 8 条；历史通过不代替本轮执行。
- 来源名称：classroom-ai 远端代码；链接：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。已 fetch、ls-remote 核对 feat/exp3-sprint2 为 347a51a0fe05838a1391b3127b97a66319a37989。
- 来源名称：当前实现与本轮独立验证；文件名：backend/src/main/java/com/classroom/ai/modules/course/service/CourseAuthorizationService.java、scripts/deploy/mysql/init/initialize.sql、scripts/deploy/mysql/init/06_exp3_sprint2.sql、scripts/tests/exp3-real-browser.cjs、docs/20261006-exp3-readiness-verification-v1.md；引用日期：2026-10-06。使用新建隔离数据库及合成输入，保留已提交版本和本地未提交界面的差别。

## 2026-10-06 exp3 完善并提交 main 的来源

- 来源名称：用户明确授权；文件名：当前会话；引用日期：2026-10-06。要求完善 exp3、提交 main，并以真实 Playwright 打通既有与 Sprint 2 功能。
- 来源名称：原始资料与当前核验；文件名：raw/README.md、docs/20261006-exp3-readiness-verification-v1.md、项目源码；引用日期：2026-10-06。真实培养方案和会议材料仍未提供。
- 来源名称：Vite 本地实现与配置；文件名：frontend/vite.config.ts、frontend/node_modules/vite/dist/node/chunks/dep-BK3b2jBa.js；引用日期：2026-10-06。确认 preview 默认继承 server.proxy，以环境指定测试后端，实现真实文件从浏览器到后端转发。
- 来源名称：本轮软件验证；文件名：docs/20261006-exp3-main-integration-v1.md、docs/exp3-main-evidence-20261006-v1/；引用日期：2026-10-06。合并候选构建、隔离 MySQL、角色页面与匿名发布、DOCX 转 PDF 为实测；时间推进及合成输入明确标注。

## 2026-10-06 当前 main Sprint 2 复验来源

- 用户当前要求：运行现在 main 的代码，核对是否符合 Sprint 2 要求。
- 代码来源：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。git fetch origin main 后，工作区 HEAD 与 origin/main 均为 9432ed1946be9cd2abee215e8f9b60d76b234267。
- 范围依据：本文件的 2026-09-23、2026-09-26 Sprint 2 来源记录，以及 docs/20260926-实验三-Sprint2-Chrome验收记录.md；八条故事为 US-05、US-07～10、US-13～15。
- 补充逐项对照依据：docs/爱教学平台全量需求与功能规格文档.md。该文档的实现状态含历史信息，不作为当前通过证据；其中更严格的细节与最新实施口径分别说明。
- 原始资料：raw/README.md；原始培养方案正文、实验一规划 DOCX 及真实会议资料在当前云端 raw 中未提供。历史来源路径不代表本轮已读取原文件。
- 本轮执行证据另存 docs/sprint2-main-recheck-20261006-v1/；不覆盖既有截图，不修改业务源码，不写正式交付物。

## 2026-10-06 fix-exp3 修复依据

- 用户要求：新建 fix-exp3 分支，修复复验发现的问题，使 Sprint 2 的代码验收要求满足。
- 分支起点：fetch 后的 origin/main 9432ed1。原有本轮复验过程文件保留。
- 需求来源：docs/爱教学平台全量需求与功能规格文档.md 的 US-05、US-07～10、US-13～15，及 docs/20261006-main-sprint2-recheck-v1.md 的运行缺口。
- 模板仅使用需求中给出的12个大项，生成明确标注为推荐草案的映射，不编造某专业的真实培养方案。
- 真实培养方案正文、团队评审/回顾资料仍未提供；本轮保证代码验收及自动化机制，不宣称缺失的业务资料已核实。

## 2026-10-06 fix-exp3 验收来源补充

- 仓库 docs/爱教学平台全量需求与功能规格文档.md 的US-05列出12类名称与模板条款，US-09列出倾斜透明单位/身份/只读凭证和禁右键复制，US-13/14列出草稿、至少3条亮点及BOPPPS建议。
- scripts/tests/fixtures/sprint2.docx、sprint2.pptx为本轮生成的合成测试文件，不是原始教学资料；仅验证格式转换与翻页。
- docs/fix-exp3-evidence-20261006-v1/为本轮实际构建、隔离MySQL/Redis、Chromium/LibreOffice运行证据；最终记录见 docs/20261006-fix-exp3-sprint2-verification-v1.md。
- raw未新增资料，培养方案正文和会议记录仍为“资料未提供”。
