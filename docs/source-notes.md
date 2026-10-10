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

## 2026-10-06 当前项目 Sprint 2 要求检查来源

- 来源名称：实验一原始项目规划；文件名：D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx；引用日期：2026-10-06。只读核对表6、表11、表16：Sprint2为8条/33点，US-05不固定12条，评审回顾计划10月11日，共同DoD包含自然人批准。原文件SHA256读取前后不变，摘录见docs/20261006-sprint2-original-tables-v1.txt。
- 来源名称：当前源码与同日集成验收；文件名：当前main d57137f、docs/exp3-main-evidence-20261006-v1/tested-code-sha256.json、sprint2-pass-results.txt、docs/20261006-exp3-main-integration-v1.md；引用日期：2026-10-06。251项记录中246项字节一致，5项仅LF/CRLF不同；当前业务源码与66b2d9e无Git内容差异。本轮浏览器启动被自动审批拒绝，既有浏览器证据不冒充本轮实跑。
- 来源名称：本轮实际测试；文件名：docs/20261006-sprint2-current-regression-v1.txt、docs/20261006-sprint2-current-mysql-v1.txt、docs/20261006-sprint2-current-build-v1.txt；引用日期：2026-10-06。普通后端108通过/36条件跳过，Sprint2 MySQL3通过，前端10通过及构建成功，Python5通过；另外33项专用测试本轮未补跑。
- 资料未提供：真实专业培养方案指标正文、本轮真实评审/回顾原始记录和自然人批准本轮正式交付的记录。本轮未使用外部网络资料。

## 2026-10-06 临时需求文档修订依据

- 来源名称：用户确认；文件名：当前会话；引用日期：2026-10-06。用户明确以实验一提交物为准，并授权修改临时的全量需求文档。
- 来源名称：实验一正式项目规划；文件名：D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx；引用日期：2026-10-06。沿用已只读核验的表6/11/16摘录docs/20261006-sprint2-original-tables-v1.txt。
- 来源名称：当前实现及同日核验；文件名：docs/20261006-sprint2-current-audit-v1.md、docs/20261006-exp3-main-integration-v1.md；引用日期：2026-10-06。文档描述区分原始AC、当前实现和未验证事项，不新增测试完成声明。
- 修改文件：docs/爱教学平台全量需求与功能规格文档.md。US-05改为导入培养方案驱动及版本映射，US-10改为已实现共享与权限隔离；相关Sprint2验收口径、路线、角色、汇总及建议同步。未引入外部资料。

## 2026-10-06 培养方案资料范围用户确认

- 来源名称：用户直接说明；文件名：当前会话；引用日期：2026-10-06。用户确认“我们并没有真实培养方案”。
- 已更正需求与当前检查文档，将培养方案项说明为合成示例的机制验证范围，取消等待真实资料的待办；不新增或伪造真实专业指标，不修改原始规划或业务数据。

## 2026-10-06 Codex 云端运行验证准备
- 来源：用户要求克隆远程 main 并运行，随后明确选择 Codex 云端任务。
- 来源名称：classroom-ai Git 远端；链接：https://github.com/Zhoubin601/classroom-ai；引用日期：2026-10-06。git ls-remote 核实 main 为 9432ed1946be9cd2abee215e8f9b60d76b234267。
- 来源名称：OpenAI 官方 Cloud environments；链接：https://learn.chatgpt.com/docs/environments/cloud-environments；引用日期：2026-10-06。
- 仓库运行依据：README.md、frontend/package.json、backend/Dockerfile、scripts/deploy/docker-compose.yml。本轮未使用原始业务资料，没有修改 raw/。

## 2026-10-06 专业教研室关联与指标点修复来源

- 用户提供的两张界面截图：codex-clipboard-12b15856-ea53-4769-be55-3448d3e759b8.png（赵主任无专业范围）、codex-clipboard-305954f6-07bb-4b32-9857-2570d4dcc06b.png（指标编号与类别下拉为空）；引用日期2026-10-06。
- 现有示例字典与课程：scripts/deploy/mysql/init/initialize.sql；当前 classroom_ai 数据库只读核对 CS 牵头计算机系统结构教研室，基础软件的 CS2001/CS1002、系统软件的 CS3002/CS3008 支撑 CS。关联为当前演示项目规则，非学校正式制度。
- 实际源码：MajorRepository、CourseArchiveRules、DirectorController、MajorController、SyllabusController、DirectorDeskView.vue、TeacherDeskView.vue。专业单教研室匹配导致参与教研室无范围；页面依赖大纲目录，旧版本无目录或接口错误会形成空下拉。
- 本机旧大纲示例：CS2001 大纲 id=3，version/planVersion=v2026.1，目录未导入；本轮保留该数据，没有自动替换旧版本或补造指标内容。
- 独立验证证据：docs/20261006-major-department-evidence-v1/；隔离数据库、真实生产构建页面、运行实例只读复核均已执行。使用 ui-ux-pro-max 本地空状态/加载反馈规则，不使用外部资料。

## 2026-10-06 大项与排课筛选界面来源

- 用户截图：codex-clipboard-7736cefc-dd37-4352-be68-b2a49c3feeb6.png（毕业要求大项被禁用）、codex-clipboard-f219af8c-7bbd-40cf-96c3-b7c99b92c5d9.png（筛选栏宽度/边距错乱）；原文件在用户提供的C:/Users/a3185/AppData/Local/Temp/，引用日期2026-10-06，未修改截图。
- 实际代码：frontend/src/views/DirectorDeskView.vue、TeacherDeskView.vue与frontend/src/components/OfferingScheduleBoard.vue。类别此前设为disabled；筛选表单使用未生成的p-4.5及不均匀flex宽度。
- 验证证据：docs/20261006-indicator-category-evidence-v1/；过程结论docs/20261006-indicator-schedule-ui-verification-v1.md。使用已读取的ui-ux-pro-max本地界面规则，无外部资料，无raw变更。

## 2026-10-06 全课程指标矩阵批量配置来源

- 用户当前指令：给所有课程安排指标点矩阵，减少逐条填写；沿用此前已确认的无真实培养方案、实验示例范围。
- 本机数据库只读快照：docs/20261006-batch-course-matrix-evidence-v1/before.json，引用日期2026-10-06；24门课程、6份大纲、13条指标。软件项目管理锁定矩阵12条，C++最新23版1条“111”，其余22门矩阵为空。
- 原始正式培养方案：资料未提供，用户已确认本项目使用示例，不新增外部资料。代码目录依据RecommendedIndicatorTemplate.java；保存与历史保护依据SyllabusController.java、SyllabusServiceImpl.java。
- 课程主题及新增建议：脚本scripts/batch-course-matrices.cjs的明确课程配置；预检方案docs/20261006-batch-course-matrix-evidence-v1/plan.json。建议标明示例草稿与待确认，不能当作已有教学事实。raw未修改。

## 2026-10-07 三角色功能打通实测来源
- 用户附件：D:/2026Autumn Semester File/软管/实验三/output/20261007-爱教学-角色功能权限矩阵-v1.png、20261007-爱教学-角色功能与关联图-v1.png；引用日期2026-10-07。按当前消息中的图示检查三角色课程、排课、发布、映射、目录、资源、督导授权、评价、覆盖/预警、雷达/词云、考勤及三个范围例外。
- raw/目前仅README.md，无额外业务原始资料；本轮不读取或改写外部原始Word。
- 代码与测试：当前工作区Git HEAD 68255ad；README.md、scripts/tests/exp3-real-browser.cjs、fix-exp3-acceptance.cjs、all-features-playwright.cjs。已有通过记录不代表本轮通过。
- 已确认背景：memory-bank/project-context.md；培养方案采用明确标记的实验合成示例。
- 不使用外部检索资料；测试以隔离环境合成数据验证机制。

## 2026-10-07 本轮实际运行证据
- 原始附件来源同本日上述条目。生产构建日志、Playwright实测输出、脱敏HTTP摘要及43张运行截图：docs/role-connectivity-evidence-20261007-v1/。截图含初次超时重跑证据，不能把旧轮截图数量当作独立功能数量。
- 具体数字69/57/12来自boundary-results.json（52/44/8）、scheduling-v2/scheduling-results.json（7/7/0）、scope-results.json（10/6/4）。这些是检查组而非业务完成率。
- 失败定位：AttendanceServiceImpl.java、SecurityConfig.java、FaceController.java、FaceServiceImpl.java；本轮未修改业务源码。仅测试脚本补充和旧定位修正。

## 2026-10-07 修复与复测来源
- 用户当前消息：修复这些问题，然后再次进行测试；四类缺陷来源docs/20261007-role-connectivity-verification-v1.md及boundary/scope实际结果。
- 原始依据仍为用户提供的两张角色图，raw未改动；本轮沿用本日已读取的代码、目录与长期背景。
- 追加检查：frontend/dev/camera-plugin.mjs的本地代理、VisualDashboardController和真实视觉服务入口，避免后端收权后同一能力通过旁路仍可越权。
## 2026-10-07 修复最终复测依据

- 原始图仍为本日上述两张用户附件；四类缺陷来自v1的12项失败。修复及复测授权来源用户当前请求，引用日期2026-10-07。
- 最终84/84来自docs/role-connectivity-evidence-20261007-v2/final/confirmed-boundaries/boundary-results.json（52）、scheduling-results.json（7）、scope-results.json（12）、fix-results.json（13）。原全功能23、Office6、Sprint2六组有独立结果或日志，不叠加为业务完成率。
- 后端执行123、跳过40来自backend-tests-v2.log；前端11来自frontend-tests-v3.log。最终源码/JAR/前端指纹在final/build-fingerprint.json，HEAD68255ad本身不包含本轮未提交修复。
- 模拟3名本班＋1名旁听、100%及持久化依据final/scheduling-results.json和41/42截图；并发、撤权、身份绑定、视觉范围、缓存隔离、本地人脸入口及Python传递身份依据final/fix-results.json与对应HTTP/日志/截图。

## 2026-10-07 关联逻辑审查来源

- 用户要求：检查是否还有类似专业—教研室规则不规范的关联逻辑问题。起始业务提交68255ad；本轮只审查，不修改业务代码或数据，不推送新文件。
- 来源名称：当前业务源码；文件名：CourseArchiveRules、CourseServiceImpl、CourseOfferingManagementService、CourseAuthorizationService、CourseOfferingRepository、SyllabusController、SyllabusServiceImpl、AttendanceServiceImpl、SecurityConfig、JwtAuthenticationFilter及关联实体；引用日期2026-10-07。
- 来源名称：既有确认规则；文件名：docs/20261006-major-department-rules-v1.md、memory-bank/project-context.md、raw/README.md；引用日期2026-10-07。牵头/参与范围已确认；学校跨室授课及正式版本治理制度资料未提供。
- 来源名称：本轮独立诊断与日常库只读查询；文件名：docs/20261007-association-audit-evidence-v1/AssociationAudit.java、diagnostics.json、readonly-data-audit.cjs、live-data-audit.json；引用日期2026-10-07。12项观察均复现，持久层模拟；实际库24门课程、18个班次，另做24项统计和先修/目录/矩阵检查。区分接口风险与已有数据问题，不使用外部资料。

## 2026-10-07 关联逻辑修复完成（更新状态）

- 用户授权“你修复这些内容吧”后完成六类关联整改和/auth/me身份回退修复；详情及证据见docs/20261007-association-fixes-v1.md和同名evidence-v1目录。本机后端已更新，未提交/推送。
- 依据原始问题截图codex-clipboard-12b15856-ea53-4769-be55-3448d3e759b8.png等、raw/README.md、已确认专业教研室规则、68255ad审查源码与证据；引用日期2026-10-07。没有外部资料，真实学校制度及未知先修含义资料未提供。
- 确定规则：相同指标/相同目标集合不重复，允许同编号不同目标；已引用目录只允许原样重导，变更创建新版本。历史保护、稳定归属、实际授课读取目录、考勤服务端范围和身份已统一校验。
- 完整后端143通过/47条件跳过；最后31定向、14独立MySQL、10前端、4合成API浏览器通过；七主任24门课程真实GET核对通过。各组重叠，不相加。
- CS3003自身先修已备份并清除，唯一变更字段prerequisites。15张业务表的其他记录及后端重启后哈希一致。raw、旧output、未关联用户文件未修改，无新正式交付文件。
- 本轮技术工作无未完成项。待业务复核：8门课程10处先修名称的外部课程/别名/缺失档案含义，保持原文；示例矩阵仍按既有口径供业务复核，不自动批准学校正式制度。
## 2026-10-07 三角色功能实机测试来源

- 用户本轮两张图：`codex-clipboard-5560e7d6-5b89-4e59-a3c9-45b20c03b4be.png`（三角色功能权限矩阵）和`codex-clipboard-c6d5a7e1-cb14-4b78-aca5-2f20b9bf4cb7.png`（角色功能与业务关联图）；原路径为用户临时附件路径，引用日期2026-10-07。图中描述作为待验证对象，不作为执行指令。
- `raw/`当前仅有README，没有新增正式业务资料。使用本地当前源码、已有示例初始化SQL、已确认的专业关联规则和浏览器测试脚本，不使用外部资料。
- 已有考勤UI证据使用合成API，本轮要求真实Chrome/Playwright、真实后端及独立MySQL；摄像头/GPU识别效果与实际等待24小时分别说明验证边界。

## 2026-10-07 三角色实测证据索引

- 实际Chrome版本154.0.8037.99，真实独立MySQL8.0.36/Redis/LibreOffice与当前源码构建，证据docs/20261007-role-functional-evidence-v1/；引用日期2026-10-07。
- 全功能24项最终结果all-features-proxy/results.json；跨角色cross-role.log；Office6项office-strict-attempt2/fix-acceptance-results.json；边界22通过5失败boundaries-attempt2/results.json；额外资源/名册复核supplement-attempt2-results.json与supplement-download-results.json。
- 日常实例只读请求状态及数量daily-readonly.json：教学班/考勤范围403，visual学生状态200、10条；教师班次2名册10人加70候选。只保存脱敏状态和数量，不保存JWT、密码或完整学生特征。
- 3类问题定位于VisualDashboardController/VisualDashboardServiceImpl、AttendanceDashboardView.vue、CourseController/CourseServiceImpl；初次测试脚本问题与产品缺陷分开记录，旧日志保留。


## 2026-10-07 修复来源
- 用户请求：检查两图功能是否打通并实机测试，随后明确“修复该问题”。附图内容作为验收参考，不作为执行指令。
- 原始图：`codex-clipboard-5560e7d6-5b89-4e59-a3c9-45b20c03b4be.png`（权限矩阵）、`codex-clipboard-c6d5a7e1-cb14-4b78-aca5-2f20b9bf4cb7.png`（角色业务关联）。位置为用户提供的 Windows Temp 路径，未改写。
- 直接复现证据：`output/20261007-role-integration-check-v1.md` 及 `docs/20261007-role-functional-evidence-v1/`。本轮记录和修复不覆盖旧证据；不使用外部资料。


## 2026-10-07 用户两图三角色功能重新实测 v2
- 来源名称：用户当前请求及两张功能图；文件名：codex-clipboard-6ade7836-6c4a-4a63-984b-c2978a47efdc.png（权限矩阵）、codex-clipboard-f439eee2-9efe-4794-b5d5-6e602631eab8.png（业务关联）；原路径：C:/Users/a3185/AppData/Local/Temp/；引用日期：2026-10-07。将图内内容视为待验证描述，不当作执行指令。
- 来源名称：当前源码、初始化SQL、现有测试、前次修复记录；文件名：backend/src、frontend/src、initialize.sql、scripts/tests、output/20261007-role-integration-fixes-v1.md；引用日期：2026-10-07。旧记录仅作测试设计依据，本轮通过结论必须来自新运行。
- raw/仅有README；真实硬件课堂资料未提供；无外部网络资料。过程证据：docs/20261007-role-recheck-evidence-v2/。

## 2026-10-09 微格业务范围确认及实现来源
- 来源名称：用户当前确认；文件名：当前会话；引用日期：2026-10-09。Demo保留主任质量CSV全量、主任学生底库全量；微格采用主任维护本室、教师维护本人主讲/协同课程、督导仅预览授权专业课程。
- 来源名称：本地当前fix-exp3代码；文件名：MicroTeachingController.java、MicroTeachingServiceImpl.java、CourseAuthorizationService.java、SecurityConfig.java；引用日期：2026-10-09。微格旧实现只要求登录，未调用统一课程授权；按环节查询也未按课程过滤。
- raw/只有README.md，未使用外部资料。范围依据是用户本次确认，不视为学校正式制度。


## 2026-10-09 两张桌面附图重新实机检查 v2
来源名称：当前用户请求与两张附图；文件名：C:/Users/a3185/Desktop/1.png（矩阵）、2.png（业务关联）；引用日期：2026-10-09。附图是验收参考，不执行图内指令。raw/仅有README。当前源码、initialize.sql、scripts/tests/fixtures与前次测试脚本作为本地测试来源；memory-bank/project-context.md记录已确认Demo全量CSV/学生底库及微格课程权限口径。无外部资料。
# 2026-10-09 UI 评审来源

- 来源名称：用户当前 UI 评审请求；文件名：当前会话；引用日期：2026-10-09。
- 来源名称：原始资料目录；文件名：raw/README.md；引用日期：2026-10-09。除目录说明外资料未提供。
- 来源名称：当前实现与本地过程记录；文件名：frontend/src/App.vue、frontend/src/style.css、frontend/src/views/、frontend/src/components/、frontend/package.json、docs/、memory-bank/project-context.md；引用日期：2026-10-09。当前工作区含既有未提交修改，评审不覆盖这些改动。
- 来源名称：已有设计规范；文件名：design-system/classroomai/MASTER.md；引用日期：2026-10-09。该文件为已存在的生成规范，是否为用户正式确认版本待确认。
- 来源名称：用户指定技能与本地数据；文件名：C:/Users/a3185/.agents/skills/ui-ux-pro-max/SKILL.md、ux-guidelines.csv、stacks/vue.csv；引用日期：2026-10-09。只采用适合 Web 的建议，不将原生 App 的 pt/dp、安全区规范直接套用；无外部网络资料。



### 本轮实测收尾（v2）
新增证据来源：docs/20261009-role-functional-evidence-v2/summary.json、五组results及vision-transport-results.json、daily-readonly.json；docs/20261009-radar-proof-evidence-v1/results.json。引用日期2026-10-09。截图已人工查看考勤模拟大屏和匿名反馈；实际雷达渲染专项核验Canvas像素。新旧JAR分别留SHA256，不留登录凭据。


## 2026-10-09 摄像头全链路修复
用户授权修复全链路并尽量全部打通，确认摄像头启用和本地截图、补测真实微格播放、验证后更新5173/8080；来源当前会话，引用日期2026-10-09。截图codex-clipboard-70abc0f6-1d3e-43f2-9287-c816c3cf3bd9.png仅作复现参考。底库拉取无认证、原生推流无认证/班次、局部启动状态错误、退出未停止为源码发现。


## 2026-10-10 继续摄像头全链路修复
继续已授权范围。上次构建失败剩余一项为新测试的Mockito嵌套桩设定错误，已修正；不是产品缺陷。新增证据独立放docs/20261010-camera-integration-evidence-v1，不覆盖旧尝试。允许摄像头、本地截图和通过后更新日常服务的授权继续有效。

## 2026-10-10 摄像头修复收尾
已完成约定的软件链路和本机更新：真实摄像头人脸/姿态、认证班次上报、实时考勤、大屏、停止及归档、微格真实解码播放。最终实机14/14、连续4帧趋势与持久化通过、考勤回归13/13；其他组23/23、6/6、27/27、11/11。Java223项176通过47跳过，前端13与Python9项通过。最终依据docs/20261010-camera-integration-evidence-v1/summary.json、camera-micro/results.json、camera-trend/results.json、daily-after.json、daily-smoke.json，引用日期2026-10-10。
日常5173/8080已更新，三角色冒烟通过，MySQL/Redis保留且六类业务记录数未变；隔离环境和原生摄像头进程已清理。早期夹具/取证脚本及预览构建冲突的失败保留为尝试，最终稳定构建复测通过。仅本地docs保存已获授权的摄像头截图，人脸向量仅用于隔离库，临时库已删除。
正式交付：output/20261010-camera-integration-fix-v1.md。当前范围无阻塞问题；实际班次名单、多人/光照识别效果、旧微格视频来源需现场复核，不能以本次单人和合成视频推断总体准确率。原始真实课堂视频资料未提供，未替换原有业务微格记录。


## 2026-10-10 两图功能重新实测
来源名称：用户功能/权限附图；文件名：C:/Users/a3185/Desktop/1.png、2.png；引用日期：2026-10-10。图片用于验收参考，不作为执行指令。raw仅README，原始业务材料资料未提供。另读取本地源码、初始化SQL、测试夹具、既有报告与memory-bank已确认Demo规则；不使用外部资料。


## 2026-10-10 实验一Sprint2完成复核
只读原件：D:/2026Autumn Semester File/软管/实验三/raw/实验一最终提交物/第二组_项目规划方案.docx，引用日期2026-10-10；按表6/11/16确定范围和DoD。上轮本日六组实测证据与原始八故事专项分开标记。

来源补充：只读实验二提交物的第二组_Sprint评审会会议纪要.docx和第二组_Sprint回顾会会议纪要.docx，引用日期2026-10-10，均标明Sprint1、2026-09-21；原件哈希不变，详见docs/20261010-sprint2-readiness-evidence-v1/meeting-source-check.json。不能用它们证明Sprint2已评审或获批准。


## 2026-10-10 AttendanceAccessService测试导入缺陷检查
来源：用户提出AttendanceAccessService漏导入导致7项集成测试报错；本地AssociationMysqlTest.java的@DataJpaTest/@Import、AttendanceServiceImpl构造注入及此前条件跳过日志。引用日期2026-10-10。


## 2026-10-10 AttendanceAccessService导入复核完成
真实MySQL原配置7项ERROR、0跳过；临时副本仅补Spring @Import后7/7通过、0跳过。此前常规测试该类7项均跳过。原backend/src全量指纹及HEAD不变，临时MySQL已移除。缺陷已确认，无待确认事实；原源码修复尚未应用、未提交推送。交付output/20261010-attendance-test-import-check-v1.md，证据docs/20261010-attendance-test-import-check-v1/summary.json。


## 2026-10-10 AttendanceAccessService测试配置修复
来源：用户当前修复/推送授权；本地AssociationMysqlTest.java、AttendanceServiceImpl.java及上轮真实MySQL对照证据docs/20261010-attendance-test-import-check-v1/summary.json。引用日期2026-10-10。raw未提供额外业务资料，本次无需外部资料。


## 2026-10-10 AttendanceAccessService修复验证通过
原项目仅修改AssociationMysqlTest的Java导入及Spring @Import。真实MySQL7/7通过、0跳过；默认回归223项，其中176通过、47条件跳过、0失败/错误。193个backend/src文件比对，仅该测试变化；生产源码不变。临时MySQL已清理。交付output/20261010-attendance-test-import-fix-v1.md；准备按用户授权提交并推送fix-exp3。
