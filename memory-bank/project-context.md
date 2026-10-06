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

## 6. 五条用户故事人工实操演示与确认 (2026-09-21)
* **执行方式**：Playwright Headed 可视窗口自动化操作演示（Chromium 1228 + `slowMo: 300ms`）。
* **覆盖故事**：
  1. US-01 主任导入课程：JWT 鉴权模板下载、CSV 解析预检、整批入库与列表渲染（通过）。
  2. US-02 教师草稿与发布：草稿暂存、双窗口并发冲突 409 拦截、v1 正式发布、读者版本隔离、v2 正式发布（通过）。
  3. US-03 主任多教师排课：协同教师维护、选课名单保留、跨教室教师时段冲突 409 拦截弹窗、多维筛选与排除自身排课编辑（通过）。
  4. US-04 结课后历史人数：结课归档物理与按钮冻结、底层接口修改名单 409 拦截、教师端历史开课与累计人次看板、空查询人次归零（通过）。
  5. US-06 督导组合检索与权限：授权专业物理隔离下拉（仅 SE、CS）、四维复合 AND 检索、空条件规范提示、清空重置、越权请求 403 与匿名 401 拦截（通过）。
* **存证资产**：`scripts/tests/manual-demo-browser.cjs`、`docs/manual-demo-evidence/`（21 张高清截图）、`docs/20260921-五条故事人工演示与实际操作确认记录-v1.md`。

## 2026-10-06 用户确认的正式实验阶段
- 来源：用户明确说明当前实验仍处于 exp2，exp3 是提前做的。
- 当前正式实验阶段为实验二；实验三代码为提前开发成果。不要根据当前检出的 feat/exp3-sprint2 或功能实现情况推断已正式进入实验三。
- 后续任务默认保留阶段区别，按用户当次明确的实验范围安排维护、验收和交付。

## 2026-10-06 实验二 Git 分支收尾已完成
- 用户要求将实验二补充分支合入 main 并清理历史分支；已完成并同步远端，main 为 a4da3d8（合并前后代码文件树一致）。
- 保留分支：main（正式实验二维护基线）、feat/exp3-sprint2（提前开发）、backup/pre-exp3-20260923（阶段前备份）。
- 已合入的实验二补充分支、foundation-auth、us01-us02 与仅本地的 us03-us04-us06 已清理，历史仍可从 main 查到。
- 当前原工作区仍在 feat/exp3-sprint2，原有未提交界面和脚本改动已保护，未迁入 main。后续若调整工作区或回移修复，先核对改动阶段归属。
- 发布标签未调整；此前标签差异与人工验收要求仍需在正式发布任务中核对。过程记录：docs/20261006-exp2-branch-closeout-v1.md。

## 2026-10-06 main 启动核验后续进度
- main / origin/main 仍为 a4da3d8。实测发现全新 MySQL 整目录初始化顺序有误，归档迁移在开课表创建前运行，导致冷启动失败。
- 本地修复分支 codex/main-startup-fix-20261006 为 a962506，仅修正 Compose 基线挂载，已通过全新数据库及真实应用启动复测，尚未推送或合入 main。
- 修复分支工作区：runtime/worktrees/main-startup-check-20261006；保留供后续审阅，Git 工作区干净。原工作区仍为 exp3，原有未提交改动未混入本轮修复。
- 主线自带课程和开课缺少专业归属，影响默认督导查询；后续需要明确映射，不自动猜填。详见 docs/questions.md 和 docs/20261006-main-startup-verification-v1.md。
- 核验使用隔离合成数据，测试进程和临时数据库容器已清理；没有对既有业务库执行测试写入。
# 2026-10-06 实验三核验后续进度

- feat/exp3-sprint2 / 远端仍为 347a51a，正式实验阶段继续保持 exp2，实验三仍为提前开发。
- 已提交业务后端与本地新版界面的实验三 8 条故事、实验二五故事浏览器链路及隔离数据库/Office 转换通过；本轮没有修改后端权限业务代码。
- 本地新增未提交修正：DirectorDeskView.vue 评价 ID 定位、OfferingScheduleBoard.vue 输入 aria-label、两份真实浏览器脚本定位/学期兼容、CourseSchedulingMysqlTest 展示姓名前提与主讲编号授权回归。原有 UI 和两个启动脚本修改保留，后续发布应一起审阅而非覆盖。
- 远端 Windows PowerShell 5 默认启动入口仍有 MySQL 告警异常问题，本地已有脚本修复尚未同步。正式完成还需真实培养方案正文与评审/回顾事实记录核对。
- 核验过程与证据分别在 docs/20261006-exp3-readiness-verification-v1.md、docs/exp3-readiness-evidence-20261006-v1/。本轮临时服务及工作区清理后，原工作区和 main 启动修复工作区保留。

## 2026-10-06 用户授权的代码集成范围

- 用户明确要求完善 exp3 并提交 main，包含现有本地 UI、启动修复及验收修正；不再将 exp3 合并 main 视为未经授权的动作。
- 本轮 main 集成内容包含实验二五故事和 Sprint 2 八故事；实验二代码仍保存在 Git 历史。课程正式实验进度继续由用户教学安排决定。
- 验收使用 Playwright、独立 MySQL/Redis 与生产 Docker；源码/过程记录和本轮证据见 docs/20261006-exp3-main-integration-v1.md、docs/exp3-main-evidence-20261006-v1/。
- 已有设计记录、未跟踪旧源码包保留原位；真实培养方案与会议资料未提供，不用自动化测试代替事实核对。
## 2026-10-06 main 集成后的当前状态（以本节为准）

- 用户授权完成 exp3 并提交 main，已执行：exp3 完善提交 66b2d9e，main 集成提交 1a31d1e，远端两个分支已同步。本地原工作区已切换 main。
- main 集成了实验二与 Sprint 2；前文“main 仍为 a4da3d8、未合 exp3”的描述属于此前核验阶段。正式教学进度仍由用户决定，代码集成不证明实验三会议已经举办。
- 本轮修复和界面已提交，不再存在这批未提交 UI/启动修改；原有 design-system/ 与 output 两个旧源码包仍未跟踪，原位保留。
- main 全新数据库基线已包含课程专业归属；不会重导既有数据卷。已有数据不完整时仍应据真实资料修复，不猜填归属。
- 真实培养方案、评审/回顾材料与现场摄像头/GPU仍待事实复核。本轮软件验收、发布记录和证据见 docs/20261006-exp3-main-integration-v1.md、docs/exp3-main-evidence-20261006-v1/。
- 测试临时服务已清理；日常三个项目容器保留。本轮只提交代码，未用新镜像替换日常运行服务。
