# 项目长期背景与基线 (Project Context)

## 1. 项目基本信息
* **项目名称**：基于具身机器人的智能课堂分析与教学辅助系统 (classroom-ai-demo)
* **核心目标**：以具身机器人为感知载体，通过计算机视觉实时感知课堂学生人数、出勤身份（人脸识别）、头部 3D 姿态（Pitch/Yaw/Roll）与专注度/抬头率（LookUp Rate），并输出教学评价数据。

## 2. 硬件与算力基线
* **开发平台**：
  * CPU: Intel Core i9-14900HX
  * GPU: NVIDIA GeForce RTX 4060 Laptop (8GB)
  * OS: Windows 11
* **运行环境**：
  * Python 环境：`.venv1` (Python 3.11)
  * 推理加速：ONNX Runtime GPU 1.21.1 + CUDA 12 + cuDNN 9
  * 状态：**GPU 推理链路已 100% 验证通过**，InsightFace 5 个模型全部由 GPU 驱动。
* **目标部署硬件**：NVIDIA Jetson Orin Nano Super

## 3. 已确认技术方案与架构
* **人体与目标检测**：Ultralytics YOLOv11n (`yolo11n.pt`)
* **人脸识别与注册**：InsightFace (`buffalo_l` 模型，RetinaFace + 512维 ArcFace 向量余弦相似度匹配)
* **头部姿态估计**：MediaPipe FaceLandmarker (`face_landmarker.task`) 提取 6 个三维关键点，通过 OpenCV `solvePnP` + `Rodrigues` + `RQDecomp3x3` 解算欧拉角
* **抬头状态判定规则**：Pitch > -10 为抬头（UP），<= -10 为低头（DOWN）
* **未来系统扩展**：Spring Boot 后端 + MySQL/Redis + Vue3/ECharts 教师大屏端

## 4. “爱教学”全链路教学质量数字化管理平台（当前最新架构）
* **项目定位**：依托东北大学软件学院《软件项目管理》背景 B“爱教学”全链路数字化管理平台（阶段1查课程 ➔ 阶段2善督导 ➔ 阶段3优课堂）。
* **架构分层解耦体系**：
  1. **独立边缘微格与视觉感知服务 (`services/edge-perception`)**：
     - OpenCV 摄像头驱动 + InsightFace (ArcFace 512D) + YOLO11 + MediaPipe solvePnP 头部姿态解算；
     - 独立运行，通过标准 RESTful 接口（US-12 规范）挂载微格视频切片与课堂录像元数据；
     - 提供画中画实时视频推断流（端口 8088）以及与核心业务解耦的通信。
  2. **核心业务平台后端 (Spring Boot 3.3.4 + JPA + Redis + MySQL)**：
     - `modules.course`：课程全量档案 CRUD、开课排课统筹看板（排课冲突智能检测）、12项工程教育认证毕业要求指标点矩阵、多维复合快速检索；
     - `modules.resource`：课件教案多版本按章挂载（单文件≤100MB限制）、环节标签（理论/实验/研讨）、在线免密只读预览与动态水印防盗链、微格切片标准挂载；
     - `modules.supervision`：BOPPPS 四维 100 分制随堂评教、24 小时延迟脱敏流转归档机制、全院督导覆盖率动态监控仪表盘、红黄预警引擎（<30% 覆盖率黄标、<75 分低分红标）、任课教师 4 维雷达图与评语词云、年度质量分析 CSV/Excel 报表一键导出；
     - `modules.attendance`：课堂智能考勤会话管理、1:N 毫秒级人脸考勤点名、实时出勤率与抬头率时序汇聚、考勤数据与排课课程历史自动归档闭环；
     - `TeachingDataInitializer`：系统启动自动灌入东北大学真实教学数据（郭军老师《软件项目管理》文管A447 95人、姜琳颖老师《计算机组成原理》信息馆B201 120人、12条毕业要求指标点等）。
  3. **前端多角色一体化门户 (Vue 3 + Tailwind CSS + ECharts)**：
     - 👔 **教研室主任工作台**：全量课程档案管理、开课统筹排课冲突防范看板、12 项指标点审查与版本锁定、全员年度质量报表一键导出；
     - 👨‍🏫 **任课教师工作台**：历史授课人次看板、大纲目标在线发布、课件教案上传分类、教学质量 4 维雷达图复盘、BOPPPS 改进闭环；
     - 🕵️‍♂️ **教学督导工作台**：待督导目标课程多维检索、听课前课件免密预审、BOPPPS 随堂打分（24h脱敏提示）、全院覆盖率大屏与红黄预警中心；
     - 🎥 **课堂智能考勤大屏**：开课课次关联、一键打开摄像头开启智能考勤、画中画现场实时人脸框与 3D 姿态角 HUD、实时出勤率、抬头率波形流、一键下课归档；
     - 👥 **学生档案与人脸库管理**：支持网页端实时取景拍照秒级录入特征与 1:N 检索测试。

## 5. 质量与验证基线
* 后端自动化单元测试：**12 个全部通过（100% PASS，0 Failures，0 Errors）**
  - `AttendanceServiceTest` (考勤会话启动与下课归档)
  - `CourseScheduleConflictTest` (排课冲突拦截校验)
  - `CourseResourceServiceTest` (100MB 限制与动态水印)
  - `SupervisionServiceTest` (BOPPPS 四维评分校验与 24h 延迟脱敏)
  - `SupervisionAnalyticsTest` (全院覆盖率仪表盘、红黄预警引擎与 CSV 报表导出)
* 前端类型检查与构建：**`vue-tsc -b && vite build` 0 错误构建成功 (100% PASS)**。

## 2026-09-18 当前代码目录
- 用户要求按前端、后端、Python 视觉划分，并进一步精简根目录；最终根目录 16 项。
- frontend/、backend/、vision/ 是三个源码模块；vision/ 为唯一在用视觉实现。
- scripts/ 集中启动与测试工具，scripts/deploy/ 保存 Compose、环境样例和 SQL，scripts/tests/ 保存联调测试。
- 根目录只保留 start_project.ps1 一个启动入口；其他入口改为 scripts/ 下相应命令。
- runtime/uploads/ 为共享图片位置，runtime/logs/ 为新日志/PID 位置；浏览器 /uploads/... URL 不变。
- 历史备份和旧文件在 docs/archive/；旧视觉副本在 docs/archive/previous-backups/structure_20260918/edge-perception/。
- raw/docs/output/memory-bank/assets 固定目录保留；.venv1/ 和 .idea/ 保持原位。
- 运行说明以 README.md 和 docs/目录整理说明.md 为准，旧记录中的路径是历史状态。
