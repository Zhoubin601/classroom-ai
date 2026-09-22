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

