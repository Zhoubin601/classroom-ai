# 基于具身机器人的智能课堂分析与教学辅助系统
## 项目技术方案与开题汇报草稿

### 一、 项目背景与需求分析

#### 1.1 项目背景
在智慧教育与数字化课堂的推进过程中，教学质量评估与学生状态感知一直是关键环节。传统课堂主要依赖教师的人工观察，存在以下痛点：
* **精力分配受限**：教师在授课时难以兼顾教室边缘及后排学生的听课状态；
* **主观性强、量化缺失**：课堂专注度、抬头率等指标缺乏连续、客观的数据支撑；
* **课后反馈滞后**：缺乏过程性教学数据积累，课后难以回溯具体的教学环节反响；
* **感知维度单一**：静态摄像头盲区大且易受遮挡，难以进行主动追踪与多角度观察。

因此，本项目提出以**具身机器人（Embodied Robot）**作为移动感知终端，结合前沿端侧深度学习与计算机视觉技术，构建实时、客观、非侵入式的课堂行为感知与辅助决策系统。

#### 1.2 用户与系统需求分析
* **教师端需求（教学辅助与状态洞察）**：
  * **宏观感知**：实时掌握班级出勤人数、整体抬头率、注意力变化趋势；
  * **微观洞察**：能够结合人脸识别识别特定学生，统计其个体听课时长与异常状态（如长期低头打瞌睡）；
  * **教学反思**：课后自动生成可视化分析报告，为优化教学节奏提供客观依据。
* **学生端需求（无感化与合规性）**：
  * **非侵入式**：无需佩戴任何穿戴设备，无需学生主动交互，不干扰正常课堂秩序；
  * **隐私保护**：视频流本地即时推理，仅传输脱敏后的行为特征与元数据。
* **系统性能需求（实时性与稳定性）**：
  * 视频流需支持 >= 20 FPS 的实时多目标检测与姿态解算；
  * 移动端/边缘端具备高能效比推理能力，支持长时间稳定运行。

---

### 二、 系统总体目标

1. **多目标动态感知**：实现教室内学生人体与人脸的快速检测定位，动态统计在座人数；
2. **精准身份识别**：基于深度特征向量比对，实现毫秒级学生考勤与身份匹配；
3. **3D 头部姿态解算与状态量化**：基于透视 n 点算法（PnP）解算头部空间三维姿态角（Pitch/Yaw/Roll），精确区分抬头听课、低头看书/玩手机、侧头走神等行为；
4. **端-边-云-网协同**：打通机器人端视觉感知、云端业务管理与前端监控报表，实现数据全链路闭环。

---

### 三、 整体系统架构设计

```
                  教室实际教学场景
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 1. 具身机器人端 (Edge Sensing & Inference)              │
│  - 视频采集: OpenCV (多分辨率缓冲流)                     │
│  - 目标检测: YOLOv11 (人体定位与区域切块)                │
│  - 身份识别: InsightFace (buffalo_l + 512维特征比对)     │
│  - 关键点估计: MediaPipe Face Landmarker (468点稠密网格) │
│  - 姿态解算: OpenCV solvePnP (Pitch/Yaw/Roll 欧拉角)     │
│  - 硬件加速: RTX 4060 Laptop (目标平台: Jetson Orin)    │
│  - 推理引擎: ONNX Runtime GPU (CUDA 12 + cuDNN 9)       │
└────────────────────────┬───────────────────────────────┘
                         │ WebSocket / HTTP
                         ▼
┌────────────────────────────────────────────────────────┐
│ 2. 云端业务平台 (Backend Services & Storage)           │
│  - 核心框架: Spring Boot 3.x                           │
│  - 缓存加速: Redis (实时课堂心跳、高频状态缓存)          │
│  - 持久化层: MySQL (学生档案、人脸库元数据、课堂记录)    │
└────────────────────────┬───────────────────────────────┘
                         │ RESTful API / SSE
                         ▼
┌────────────────────────────────────────────────────────┐
│ 3. 教师端可视化大屏 (Teacher Dashboard & Analytics)    │
│  - 前端框架: Vue 3 + TypeScript + Pinia + Tailwind CSS │
│  - 图表可视: Apache ECharts (抬头率折线、出勤分布)       │
└────────────────────────────────────────────────────────┘
```

---

### 四、 核心技术栈与算法原理

| 层次/模块 | 选用技术 / 算法 | 核心职责与技术优势 |
| :--- | :--- | :--- |
| **视频采集** | OpenCV (Python) | 摄像头驱动与帧缓存流拉取，ROI 动态切块 |
| **目标检测** | Ultralytics YOLOv11n | 极低延迟识别课堂场景中的人体，输出边界框与总人数 |
| **特征与识别** | InsightFace (buffalo_l) | 包含 RetinaFace 检测与 ArcFace 512维特征抽取，高精度余弦相似度匹配 |
| **关键点定位** | MediaPipe FaceLandmarker | 获取人脸 3D 几何特征点（鼻尖、眼角、嘴角、下巴等基准点） |
| **姿态解算** | PnP 空间解算 (solvePnP + Rodrigues) | 结合真实世界 3D 人脸模型与 2D 图像关键点，解算三维欧拉角（俯仰角 Pitch、偏航角 Yaw） |
| **加速运行库** | ONNX Runtime GPU (CUDA 12 + cuDNN) | 消除 CPU 瓶颈，实现毫秒级端侧并行推理 |
| **后端业务** | Spring Boot + MyBatis-Plus | 学生档案管理、课堂会话生命周期维护、API 数据分发 |
| **数据存储** | MySQL 8.0 + Redis 7.x | 关系型业务数据存储 + 实时课堂流式数据缓存 |
| **前端呈现** | Vue 3 + TypeScript + ECharts | 教师大屏端实时抬头率波形监控、历史报表导出 |

---

### 五、 已完成内容与阶段性成果（核实确认）

1. **异构计算与 GPU 加速环境搭建 ✅**：
   * 已配置 ONNX Runtime GPU (v1.21.1) 与 CUDA 12 / cuDNN 9 链路；
   * 实测验证 InsightFace 全系列 5 个子模型（detection, landmark_3d_68, landmark_2d_106, genderage, recognition）全部运行在 NVIDIA GPU 上，无 CPU 回退。
2. **人脸注册与实时识别系统（InsightFace）✅**：
   * 完成 `face_register.py`，支持人脸捕捉、512 维 Embedding 提取并持久化为 `face_db.npy`；
   * 完成 `face_recognition.py`，实现视频流人脸检测、余弦相似度比对与身份标签实时渲染。
3. **头部姿态估计与低头/抬头算法（PnP）✅**：
   * 完成 `head_pose_test.py`，构建 3D 面部物理模型与 6 关键点映射；
   * 通过 OpenCV solvePnP 和 RQDecomp3x3 输出三轴欧拉角，以 Pitch < -10 度判定低头（LOOK DOWN）。
4. **多人场景综合分析流水线（YOLO11 + MediaPipe Pipeline）✅**：
   * 完成 `yolo_face_analysis.py`；
   * 闭环实现：视频帧 -> YOLO11 人体检测 -> 人体 ROI 裁剪 -> MediaPipe 人脸关键点 -> solvePnP 姿态解算 -> 动态统计班级人数与抬头率（LookUp Rate）。

---

### 六、 当前未完成内容与工程规划

1. **算法级深度融合与工程优化（待完成）**：
   * 流水线统一：合并人脸识别与抬头率分析为单一直播流处理；
   * 多学生人脸库扩展：由单人 face_db.npy 升级为支持多学生元数据的数据库/索引；
   * 时间窗口平滑滤波：加入移动平均滤波以避免瞬间动作误判。
2. **云端后端服务体系（待完成）**：
   * Spring Boot 接口开发、数据表设计（学生、课堂记录、专注度时序）、WebSocket 实时推送通道。
3. **教师端可视化系统（待完成）**：
   * 基于 Vue 3 + ECharts 开发实时监控面板与课后反馈报表。
4. **具身机器人边缘平台迁移（待完成）**：
   * 部署至 NVIDIA Jetson Orin Nano Super 开发板，并进行 TensorRT FP16 优化量化。

---

### 七、 当前项目完成度矩阵

| 模块类别 | 核心功能项 | 现状说明 | 完成状态 |
| :--- | :--- | :--- | :--- |
| **环境与算力** | GPU 驱动 / CUDA / cuDNN | 已完全配置就绪 | **100%** ✅ |
| | ONNX Runtime GPU 推理链路 | 5 大模型已全链路通过 GPU 校验 | **100%** ✅ |
| **基础算法验证** | 摄像头多帧捕获 | OpenCV 驱动流畅捕获 | **100%** ✅ |
| | YOLO11 人体与目标检测 | 轻量模型实时定位人体 | **100%** ✅ |
| | MediaPipe 人脸关键点估计 | 468 网格与关键点检测正常 | **100%** ✅ |
| **高级分析算法** | 3D 头部姿态解算（PnP） | 欧拉角解算与高低头判定算法跑通 | **100%** ✅ |
| | 人脸注册与特征提取 | 512维 Embedding 提取入库跑通 | **90%** ✅ |
| | 实时身份比对与识别 | 余弦相似度比对原型跑通（待扩充多人群库） | **85%** ✅ |
| | 课堂人数与抬头率流水线 | 端到端统计与动态百分比计算已跑通 | **90%** ✅ |
| **系统与应用** | 视觉全链路融合（识别+姿态） | 需合流为生产级代码 | **20%** ⏳ |
| | Spring Boot 后端与数据表 | 正在搭建基于 Spring Boot 3 + Redis + MySQL + Docker 架构 | **进行中** 🔄 |
| | Vue3 + ECharts 教师看板 | 界面原型规划中，待开发交互组件 | **0%** ❌ |
| | Jetson 边缘设备部署与转换 | 待模型 TensorRT 量化与真机联调 | **0%** ❌ |

---

### 八、 Java 后端（Spring Boot + MySQL + Redis + Docker）人脸数据存储技术方案

#### 8.1 数据存储模型设计
1. **学生主表 (`student`)**：
   * `id`: 自增主键 (`BIGINT`)
   * `student_id`: 学号 (`VARCHAR(64)`，唯一索引 `uk_student_id`)
   * `name`: 姓名 (`VARCHAR(64)`)
   * `class_name`: 班级名称 (`VARCHAR(64)`)
   * `gender`: 性别 (`VARCHAR(16)`)
   * `created_at` / `updated_at`: 时间戳
2. **人脸特征表 (`face_feature`)**：
   * `id`: 自增主键 (`BIGINT`)
   * `student_id`: 关联学号 (`VARCHAR(64)`，普通索引 `idx_student_id`)
   * `feature_dim`: 特征向量维度 (`INT`，固定 512)
   * `feature_vector`: 512 维特征向量 (`MEDIUMTEXT`，采用 JSON Float 数组格式存储，兼顾跨平台通用性与可读性)
   * `image_path`: 人脸底库快照图片本地存储路径或 URL (`VARCHAR(255)`)
   * `created_at` / `updated_at`: 时间戳

#### 8.2 Redis 缓存架构设计
* **热数据缓存**：
  * Key 命名：`face:feature:{student_id}` -> 存储 512 维 float 数组 JSON 字符串与学生简要信息。
  * 注册/更新时：双写 MySQL 并刷新 Redis；
  * 删除时：删除 MySQL 记录并清除 Redis 缓存。
* **内存预热与全量同步**：
  * 服务启动时（`ApplicationRunner`）自动将 MySQL 中的有效学生人脸向量预热入 Redis。
  * 提供 `GET /api/face/all` 接口，支持 Python 边缘端/机器人启动时或定期一键全量同步特征库，无需频繁查询数据库。
* **高频比对支持**：
  * 提供 `POST /api/face/search`，由后端基于 Redis 缓存直接在内存中执行 1:N 余弦相似度计算，返回匹配的学生信息和置信度，供轻量级客户端调用。

#### 8.3 容器化与 Docker 编排
* **MySQL 8.0 容器**：配置 `utf8mb4` 字符集，挂载 `docker/mysql/init/01_schema.sql` 实现容器初次启动自动建表，挂载独立 volume 保证数据持久化。
* **Redis 7.x 容器**：开启 AOF/RDB 持久化，端口映射 6379。
* **Spring Boot 后端容器**：采用多阶段构建，打包为轻量级 JRE 镜像，通过环境变量连接同网络下的 `mysql` 和 `redis` 服务。
* **`docker-compose.yml`**：一键编排并定义服务依赖健康检查（`depends_on` + `condition: service_healthy`）。

#### 8.4 预留前端可视化大屏与管理 API 体系
1. **全局跨域配置 (`CorsConfig`)**：
   * 允许前端控制台（如 Vite 开发端口 `5173` 等）跨域请求。
2. **大屏实时监控接口 (`/api/visual/*`)**：
   * `GET /api/visual/overview`：返回宏观看板指标（总应到人数、当前实到人数、出勤率、实时平均抬头率 LookUp Rate、低头预警人数等）。
   * `GET /api/visual/trend`：返回时序数据点（最近 30~60 个时间窗口的抬头率、专注度曲线，供前端 ECharts 折线图平滑渲染）。
   * `GET /api/visual/students/status`：返回学生实时状态网格/卡片列表（姓名、学号、班级、考勤状态、最新姿态 Pitch 状态 UP/DOWN）。
   * `POST /api/visual/report/stream`：供 Python 视觉端/机器人定时向后端上报单帧/周期聚合数据。
3. **学生与人脸管理接口 (`/api/student/*`, `/api/face/*`)**：
   * 支持前端后台对学生档案、人脸注册状态的增删改查。

---

### 九、 前端可视化大屏系统（Vue 3 + Vite + ECharts + Tailwind CSS）方案

#### 9.1 技术栈选型
* **核心框架**：Vue 3 (Composition API, `<script setup>`)
* **构建与开发工具**：Vite 5
* **图表可视化库**：Apache ECharts (5.x)，提供平滑波形曲线、渐变填充与动态动画
* **样式框架**：Tailwind CSS (响应式暗色科技感大屏主题，深蓝/科技青为主色调)
* **网络请求**：原生 Fetch / Axios 封装，直连 `http://localhost:8080`

#### 9.2 页面与功能模块规划
1. **大屏指挥中心 (`DashboardView.vue`)**：
   * **顶部状态栏**：当前授课课程、班级标识、实时数字时钟、机器人推断心跳信号指示灯；
   * **核心指标卡片 (Metric Cards)**：在座实到人数、应到总数、出勤率、实时平均抬头率、低头预警人数、专注度等级评定；
   * **时序趋势图 (`FocusTrendChart.vue`)**：实时波形折线图，X 轴为时间戳，Y 轴为抬头率百分比与在座人数双曲线，支持动态平滑流动更新；
   * **学生实时姿态网格 (`StudentStatusGrid.vue`)**：卡片式矩阵展示班级每位学生，支持头像、学号、姓名、考勤状态及当前姿态徽章（绿色 UP 抬头听课 / 红色 DOWN 低头走神）；
   * **推流模拟与交互面板**：内置“启动演示推流 / 暂停”、“单帧触发”控制器，即便在脱离摄像头时也能直观演示动态大屏效果。
2. **学生档案与人脸库管理 (`StudentManageView.vue`)**：
   * **学生列表表格**：包含学号、姓名、班级、人脸录入状态、头像与操作列（编辑/删除）；
   * **人脸注册对话框**：支持录入新学生基本信息并录入特征或上传底库图片；
   * **1:N 云端检索测试工具**：直接调用后端 `/api/face/search`，验证特征向量识别准确率与相似度匹配得分。

---

### 十、 人脸录入摄像头无窗口/无响应根因分析与双引擎闭环架构

#### 10.1 现象与根因诊断
1. **摄像头灯亮但无桌面窗口**：
   - 之前前端在 Windows 环境下通过 Node.js 子进程启动 `face_register.py` 时，由于前端 Vite 服务是以非前台交互终端守护进程运行，操作系统会话安全隔离（Session Isolation）导致派生的 OpenCV HighGUI 视窗无法投射至用户的前台交互桌面（WinSta0\Default），窗口悬挂在后台，用户屏幕不可见。
2. **终端打印 Emoji 引发 UnicodeEncodeError 崩溃**：
   - `face_register.py` 中使用了 Emoji 字符（如 `🎉`），在 Windows 默认终端代码页为 GBK 时，Python 标准输出抛出 `UnicodeEncodeError: 'gbk' codec can't encode character` 导致程序瞬间崩溃退出。
3. **Docker 端口与后端状态**：
   - 宿主机运行着本地 MySQL 3306，导致 Docker 映射端口冲突，现已平滑将 Docker 容器的 MySQL 映射调整为 3308，Spring Boot 后端（8080）与 MySQL/Redis 已完全恢复健康通信。

#### 10.2 终极解决方案：双引擎架构（网页端实时取景 + 桌面端独立窗口）
1. **引擎一：网页端实时摄像头取景 & 秒级录入（默认推荐，100% 画面可见）**：
   - 前端弹窗原生集成 HTML5 `navigator.mediaDevices.getUserMedia`，直接在网页弹窗内渲染 1280x720 实时视频流；
   - 画面配备科幻 HUD 人脸对准虚线框、四角绿光准星、实时状态条；
   - 用户正对镜头后，点击【📸 立即拍照并录入】（或在页面上按键盘快捷键 `S`）；
   - 前端捕获帧数据转 Base64 提交给 `/api/face/register-webcam`；
   - 宿主机后台极速调用 `face_register.py --image`，通过 InsightFace `buffalo_l` 提取 512 维特征向量，裁剪高质量头像存入 `uploads/faces/{id}_snapshot.jpg`，并同步持久化至 MySQL 和 Redis；
   - 弹窗展示成功提示后自动关闭，列表秒级刷新出真实头像与学生档案。
2. **引擎二：桌面独立 OpenCV 窗口调起**：
   - 参数通过 `register_config.json` 传递，杜绝中文字符与括号特殊字符截断；
   - Python 输出流强制配置为 UTF-8，杜绝 Emoji 乱码；
   - 通过 PowerShell `Start-Process cmd.exe -ArgumentList '/k run_register.bat'` 保证在前台活动桌面为用户唤起终端与 OpenCV 窗口。

---

### 十一、 智能课堂实时视觉督导系统（classroom_monitor.py）架构设计

#### 11.1 系统定位与核心指标流
本模块为具身机器人/课堂智能视觉督导系统的推断核心：
1. **输入端**：接入实时 USB/机器人前视摄像头（640x480 或 1280x720 视频流）；
2. **人脸识别与身份锁定（1:N）**：
   - 启动时自动从 Java Spring Boot (`GET /api/face/all`) 拉取已注册学生的 512 维 ArcFace 向量特征底库与学生档案（支持本地 `models/face_db.npy` 兜底）；
   - 视频帧通过 InsightFace `buffalo_l`（CUDA GPU 加速）实时检测人脸并提取特征向量；
   - 与底库学生特征向量计算余弦相似度（阈值 ≥ 0.42），实现精准学号与姓名匹配，未匹配者标记为“访客/未录入学生”；
3. **头部姿态估计与抬头/低头判定（PnP 欧拉角）**：
   - 基于 MediaPipe FaceLandmarker（模型 `models/face_landmarker.task`）提取人脸关键点；
   - 匹配 3D 头部几何模型与摄像头内参，通过 `cv2.solvePnP` 求解旋转向量；
   - 经由 Rodrigues 与 RQDecomp3x3 解算 Pitch 俯仰角：
     - 若 `Pitch >= -12°`：判定为【抬头听课 (UP)】；
     - 若 `Pitch < -12°`：判定为【低头走神/看书/看手机 (DOWN)】；
4. **指标聚合与高频上报通道**：
   - 周期性（1.0 ~ 1.5 秒）将聚合统计数据（检测总人数、抬头人数、低头人数、实时抬头率、实到学号列表、各学生实时 UP/DOWN 姿态）通过 HTTP POST 提交给 Spring Boot 后端：
     `POST /api/visual/report/stream`
   - Spring Boot 更新 Redis 缓存宏观指标，推送折线点与网格卡片；
5. **前端一键督导控制与大屏实时联动**：
   - 前端大屏顶部提供“开始智能督导”控制器；
   - 点击后通过 Node.js/Vite 中间件拉起 `classroom_monitor.py`；
   - 督导过程中前端轮询大屏数据，ECharts 时序曲线与学生状态网格实时随摄像头真实画面同步流动；
   - 点击“停止督导”可随时终止监控进程，释放硬件设备。

#### 11.2 故障排查与终极解决（双屏可视 + 零转义原生拉起）
1. **排查与根因分析**：
   - **配置被覆盖**：前端目录同时存在 `vite.config.js`（早期构建产生的旧文件）与 `vite.config.ts`。Vite 默认优先读取 `.js` 文件，导致对 `.ts` 的所有更新被完全忽略！
   - **路径空格截断**：由于项目路径包含空格（`D:\2026Autumn Semester File\...`），通过 PowerShell `-Command` 包装传递字符串命令时，Windows Shell 会将外层引号脱落，解析为截断的非法路径报错闪退。
2. **终极解决措施**：
   - **清除干扰文件**：彻底删除 `vite.config.js` 与 `vite.config.d.ts`，确保开发服务精准生效 `vite.config.ts`。
   - **零转义原生拉起**：在 Vite 中间件直接使用 Node.js `child_process.spawn(pythonExe, [script, '--port', '8088'])`，通过底层 OS 数组传参，100% 免疫路径空格与引号转义。
   - **内置 HTTP MJPEG 视频流服务**：`classroom_monitor.py` 内置多线程轻量 HTTP 服务器（端口 8088），提供 `/video_feed`（MJPEG 视频流）、`/health`（健康探活）与 `/stop`（优雅释放）。
   - **大屏实时画中画**：在前端可视化大屏左侧新增【🤖 具身智能机器人·摄像头感知视窗】，点击“开始智能视觉督导”后，不仅调起摄像头硬件，而且大屏中央实时显示带人脸识别框、学生姓名学号、3D 姿态角 HUD 的现场画面！
   - **平滑停止与硬件释放**：点击“停止视觉督导”时，前端直接向 8088 端口下发 `/stop`，Python 优雅关闭视频捕获设备并释放资源。

---

### 十二、 背景 B“爱教学”全链路教学质量平台分层解耦与功能完善方案

#### 12.1 解耦驱动与系统定位
根据东北大学软件学院《软件项目管理》实验一交付报告，项目核心愿景聚焦于高校教学质量数字化管理（查课程 -> 善督导 -> 优课堂），同时确立两项刚性合规红线：
1. 坚决排除未经师生授权的教室摄像头实时抓拍与人脸识别考勤（红线禁止）；
2. 坚决排除基于计算机视觉的学生微表情、疲劳度、走神热力图等伪需求（红线禁止）。
因此，必须对现有项目进行**物理与逻辑层面的彻底解耦**：
- **边缘微格/视觉感知层（Edge Service）**：将原根目录下的 Python 视觉原型剥离归拢至独立微格感知模块（`services/edge-perception`），仅通过 US-12 规范的 RESTful API 提供“微格视频切片与课堂录像元数据”异步挂载，杜绝其侵入核心业务；
- **核心业务平台后端（Core Backend）**：Spring Boot 采用高内聚低耦合的领域模块化架构（Domain-Driven Clean Architecture），分为 `course`（课程排课认证）、`resource`（教学资源与微格切片）、`supervision`（督导评教预警与雷达图）三大核心模块；
- **现代前端多角色工作台（Frontend Portal）**：Vue 3 + Vite + Tailwind CSS + ECharts 构建面向教研室主任、任课教师、教学督导三大角色的专业工作台，实现全链路功能闭环。

#### 12.2 后端领域分层与四表关联核心 Schema
对齐实验一思考题 4 要求的教务核心四表关联概念验证（PoC）：
1. **`Course`（课程档案表）**：`course_code`（全局唯一）、`course_name`、`credits`、`hours`、`course_type`、`prerequisites`、`objectives`、`assessment_method`；
2. **`CourseOffering`（教师开课表）**：关联 `course_id`、`teacher_name`、`academic_term`、`student_count`、`status`；
3. **`CourseSchedule`（排课时段教室表）**：关联 `offering_id`、`classroom`（如文管 A447、信息馆 B201）、`week_range`（如 1-16周）、`day_of_week`、`period`、`conflict_check`；
4. **`CourseSyllabus` 与 `GraduationIndicatorMapping`（教学大纲与12条毕业要求指标点映射表）**：锁定指标点权重、支撑度矩阵与大纲版本。

#### 12.3 督导评教、预警与分析模型
1. **`SupervisionEvaluation`（随堂听课评价表）**：
   - BOPPPS 四维打分：教学态度 (0-25)、教学内容 (0-25)、教学方法 (0-25)、教学效果 (0-25)，总分 100 分；
   - 质性评语：教学亮点与改进建议（限 500 字）；
   - 脱敏归档与延迟流转机制：填报后状态为 `PENDING_DESENSITIZE`，24 小时后脱敏归档至 `PUBLISHED` 并对任课教师开放。
2. **督导覆盖率与预警引擎**：
   - 动态计算学院课程总门数、已督导门数与督导覆盖率；
   - 自动生成预警：覆盖率 < 30% 标记黄色预警，评分均分 < 75 分标记红色低分预警。
3. **教学质量 4 维雷达图与报表一键导出**：
   - 支撑任课教师与教研室主任查看各维度均分雷达图；
   - 支持一键导出符合高校教务规范的年度质量分析 Excel/PDF 报表。

## 2026-09-16 检查记录
- 前端基线构建通过；构建会重新生成 vite.config.js，历史“删除旧配置”方案未消除根因。
- Docker 初始未运行；本机 Maven 未配置 PATH，但存在本地 Maven 分发包。
- 当前目录没有 Git 元数据，不能依靠 git diff 验证变更，使用逐文件记录与测试。
- 摄像头视觉监控“视觉进程已退出”故障排查与修复：
  1. 故障原因：原 Vite 开发服务器子进程继承了旧终端的沙箱限制标记，调用 virtualenv 包装器时触发 Windows `CreateProcessW` 限制（退出码 101 / EPERM）；同时原生 `classroom_monitor.py` 在首帧摄像头读取轻微延迟时缺乏帧重试，且 `camera-plugin.mjs` 原使用 `stdio: ignore` 吞掉了真实报错。
  2. 修复措施：
     - `classroom_monitor.py` 增加连续读取帧重试缓冲机制（容忍前序暂态空帧）与 FutureWarning 抑制。
     - `frontend/dev/camera-plugin.mjs` 增加标准输出与标准错误管道捕获，将异常详细原因回传并写入 `docs/runtime/monitor_spawn.log`。
     - `frontend/src/api/index.ts` 优化启动轮询状态判定，精确上报底层进程退出原因。
     - `start_project.ps1` 修正启动脚本中含空格路径的参数引号转义。
     - 重启 Vite 独立服务并完成全链路接口闭环压测：`start-monitor` 状态成功转为 `running: true`，摄像头与模型加载顺利，`stop-monitor` 可正常下线。
- 教学工作台面板假数据排查与真实数据库接入治理：
  1. 根因剖析：
     - Windows 环境下 Maven 编译时缺少 UTF-8 源码与输出编码配置，导致 `TeachingDataInitializer.java` 常量在编译为字节码时转为包含乱码字符（`??????`）并灌入 MySQL，导致前端按学期/教师名称无法检索。
     - 前端视图中存在防御性硬编码三元表达式（如 `currentTeacher === '郭军' ? '《软件项目管理》' : '《计算机组成原理》'`、`'95 人次' : '120 人次'`、指标卡片 `|| 4`、`|| 3`、`|| 75.0` 等），引发“面板造假”质疑。
  2. 治理措施：
     - `backend/pom.xml` 配置 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` 与 `maven-compiler-plugin` 的 `-encoding UTF-8`。
     - 清洗 MySQL 数据库并重新使用 UTF-8 启动后端，重新灌入无乱码的东北大学真实课程（CS3001、CS2002、CS2001、CS3002）、开课班次、排课时段及随堂评教。
     - `TeacherDeskView.vue` 移除硬编码三元，教师下拉框改为动态提取，课程名、班级名、排课教室（联动 `scheduleApi.getAll` 真实排课时段）与班额全动态绑定真实开课实体。
     - `SupervisorDeskView.vue` 彻底移除 `|| 4`、`|| 3`、`|| 75.0`、`|| 1` 等假数据 fallback；动态加载 `availableTerms`，全量展现真实开课卡片。
     - 前端执行 `npm run build` 校验，0 编译错误；后端全部接口经真实调用验证通过。

## 2026-09-17 UI 浅色简约风重构方案设计 (Operate 模式高阶工艺)

### 1. 现状痛点诊断 (Heuristic & Craft Audit)
* **暗黑赛博过度包装**：全站采用 `#0a0f1d`（暗黑高对比底色）+ `#00f2fe`（高饱和霓虹青）+ 强烈发光阴影（`shadow-glow-cyan`, `shadow-glow-green`），属于典型的“极客科技风大屏”，不符合高校教务日常办公、教学督导、课程管理所需的“温和、护眼、专业、高可读性”诉求。
* **图标系统混乱**：大量使用系统 Emoji（如 👔、👨‍🏫、🕵️‍♂️、🎥、👥、📊、➕、📁 等）替代规范矢量图标，跨平台渲染参差不齐，破坏学术级管理系统的严谨质感。
* **信息层级与留白不足**：玻璃拟态卡片（`glass-card`）过度使用毛玻璃滤镜与多层嵌套边框，造成视觉噪音；表单输入控件缺乏统一的浅色态交互轮廓。

### 2. 浅色简约设计语言系统 (Minimalist Light Design Tokens)
* **模式定位**：Operate 模式（任务驱动、高频扫描、极简克制、信息层级清晰）。
* **色彩基调 (Calm & Restrained Palette)**：
  - 应用主背景：`#f8fafc` (Tailwind `slate-50`)，自然柔和，无视觉压迫感。
  - 卡片与工作区表面：`#ffffff` (纯白 Surface)，搭配 1px 精致边框 `#e2e8f0` (slate-200)。
  - 辅助浅灰容器：`#f1f5f9` (slate-100)，用于导航药丸底色、搜索框底色与表格表头。
  - 核心字色层级：
    - 一级主标题/重要数据：`#0f172a` (slate-900)，对比度 > 10:1。
    - 二级正文与标签：`#334155` (slate-700)。
    - 三级弱化说明/占位符：`#64748b` (slate-500)。
    - 四级极弱注脚：`#94a3b8` (slate-400)。
  - 品牌与功能色 (语义化微色温)：
    - 主功能/主行动：学术沉稳靛蓝 `indigo-600` (`#4f46e5`)，激活背景 `indigo-50`，弱边框 `indigo-200`。
    - 正常出勤/就绪：健康翡翠绿 `emerald-600` (`#059669`)，激活背景 `emerald-50`。
    - 待办/提示：暖色琥珀 `amber-600` (`#d97706`)，激活背景 `amber-50`。
    - 缺勤/冲突/停止：质感玫红/红 `rose-600` (`#e11d48`)，激活背景 `rose-50`。
* **深度与投影 (Elevation & Shadows)**：
  - 彻底剥离霓虹光晕（`glow-*`）与硬边投影。
  - 采用多层微弥散投影：卡片默认 `box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05)`，悬浮轻微提升 `box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.05)`。
* **图标规范化**：
  - 全面引入工程中已有的 `lucide-vue-next` 现代矢量线框图标，笔触统一为 1.75px 或 2px，替代全部 Unicode Emoji。
* **ECharts 图表浅色化重构**：
  - 折线趋势图（`FocusTrendChart.vue`）：背景透白，横纵网格线替换为柔和浅灰虚线（`#f1f5f9`），主趋势线使用雅致靛蓝（`#4f46e5`）辅以平滑透明微弱浅蓝渐变面积（`rgba(79, 70, 229, 0.12)` 至 `rgba(79, 70, 229, 0)`），提示框改为浅色悬浮白卡配阴影。
  - 督导雷达图（`TeacherDeskView.vue`）：移除暗黑蛛网背景，采用浅灰等距同心环（`#e2e8f0`），轴线与刻度字色适配深灰（`#475569`），覆盖面以柔和半透靛蓝描摹。

## 2026-09-18 项目目录重构
采用 frontend/、backend/、vision/ 三个主要模块，scripts/ 集中运行工具，旧视觉副本归档。共享 uploads/ 与已有 API URL 保持稳定。详细映射及验证见 [重构记录](refactor-20260918.md)。


## 2026-09-18 根目录进一步收拢
用户指出根目录文件仍多，要求实际整理并写好说明。保留前端、后端、视觉、scripts 和五个规定目录；零散日志与上传数据集中 runtime/，部署和联调测试下沉 scripts/，backup 与旧入口归档 docs/archive/，根目录只保留 start_project.ps1 一个启动入口。移动 raw 以外文件，修正依赖路径，校验文件完整性与构建后更新 README 和详细迁移说明。不调用摄像头、不更改真实数据库记录。

## 2026-09-18 教学督导工作台【全院开课总课表 / 听课日程看板】全面落地
- **业务诉求背景**：
  教学督导专家（Supervisor）的核心日常业务为随堂听评课。此前督导工作台缺少全局开课总课表与听课日程看板，且开课卡片中未注明上课教室与节次时段，无法支持督导实地进班听课的实际工作需求。
- **架构与功能设计落地**：
  1. **工作台一级 Tab 重构升级**：
     - 新增第一核心 Tab：【全院开课总课表 / 听课日程看板 (US-03/06)】，默认直接聚焦课表日程。
     - 保留【待督导目标课程多维检索 (US-06)】与【教学质量预警中心 (US-16)】。
  2. **双视图模式无缝切换**：
     - **高校标准周历矩阵课表**：横向周一至周日 7 列，纵向 5 个标准大节次时段（08:00-09:35、10:05-11:40、13:30-15:05、15:35-17:10、18:30-20:05）。单元格内高亮突出主讲教师徽章（第一视觉焦点）、课程名称代码、教室（翠绿标签）、班级与班额。
     - **多维排课日程卡片看板**：卡片式流式呈现，突出任课教师第一视觉，具备 P1 级分页控制栏（支持每页 3 / 6 / 9 条）。
  3. **多维快捷组合筛选工具栏**：
     - 星期几筛选（周一至周日）
     - 节次时段筛选（1-2节至9-10节）
     - 课程性质筛选（专业核心课、专业基础课、通识必修课、专业选修课）
     - 授课教师检索（高亮模糊过滤）
     - 教室筛选（文管 A447、信息馆 B201 等）
     - 一键重置筛选
  4. **全链路业务闭环联动**：
     - 课表卡片直接提供【随堂评课 (US-13)】（一键唤起 BOPPPS 四维打分表并自动回填课程教师信息）、【进班监控】（进入实时考勤姿态大屏）、【预审课件】（免密查阅大纲与课件）。
  5. **开课检索卡片补全时空信息**：
     - 在待督导开课复合检索卡片中关联并直观展示教室地点（如 `📍 文管 A447`）与排课时段（如 `⏰ 周三 第3-4节 (1-16周)`）。

## 2026-09-18 全量教务底座初始化 (initialize.sql) 与全系教师快捷登录矩阵上线
- **业务诉求背景**：
  1. 用户要求生成全量基础课程数据的 SQL 脚本，命名为 `initialize.sql` 并注入 MySQL 数据库；
  2. 包含 8 个班级学生档案，每个班严格恰好 10 名学生（全院共计 80 人），且人脸数据库（`face_feature`）512 维特征向量与底库文件完整关联；
  3. 涵盖 8 位专任教师（郭军、姜琳颖、赵广生、王伟、董晓梅、刘博、陈立新、孙志刚）及李主任、张督导等管理账号，统一密码 `123456`；
  4. 满足“一个老师有多门课程，分布在不同时间段”，并在周三第3-4节、周二第1-2节等时段实现多班级、多教师在不同教室的并行排课；
  5. 在平台登录首页（`LoginView.vue`）显式呈现所有老师的快捷登录入口，具备一键直登与凭证回填，并配置带有持久化存储（localStorage）的显隐折叠开关。
- **架构与实现落地**：
  1. **SQL 自动化生成器与初始化脚本**：
     - 开发 `scripts/generate_initialize_sql.py`，产出 `initialize.sql`（457KB）并备份至 `scripts/deploy/mysql/init/initialize.sql`。
     - 包含：5 个专业（SE、CS、AI、DS、SEC）、8 位教师档案、10 个教务权限账号、17 门精品专业课程、8 个班级（80 名学生档案 + 80 条 512 维人脸特征向量）、17 个开课班次及排课日程（包含周三3-4节三门并行、周二1-2节两门并行）、80 名学生选课记录、12 项工程认证国标指标点矩阵及督导评教记录。
     - 解决 Windows PowerShell 管道编码截断问题，通过 Docker 内部 `SOURCE /tmp/initialize.sql;` 保证 UTF-8 字符集无乱码写入。
  2. **后端数据持久化保护 (TeachingDataInitializer.java)**：
     - 修改学生数校验阈值，尊重 MySQL 现有 80 名学生真实持久化数据，避免启动时重复篡改。
     - Maven 重新编译打包并重启 daemon 后端服务，API 运行平稳。
My code document has the following content:

---

### 十、 人脸录入摄像头无窗口/无响应根因分析与双引擎闭环架构

#### 10.1 现象与根因诊断
1. **摄像头灯亮但无桌面窗口**：
   - 之前前端在 Windows 环境下通过 Node.js 子进程启动 `face_register.py` 时，由于前端 Vite 服务是以非前台交互终端守护进程运行，操作系统会话安全隔离（Session Isolation）导致派生的 OpenCV HighGUI 视窗无法投射至用户的前台交互桌面（WinSta0\Default），窗口悬挂在后台，用户屏幕不可见。
2. **终端打印 Emoji 引发 UnicodeEncodeError 崩溃**：
   - `face_register.py` 中使用了 Emoji 字符（如 `🎉`），在 Windows 默认终端代码页为 GBK 时，Python 标准输出抛出 `UnicodeEncodeError: 'gbk' codec can't encode character` 导致程序瞬间崩溃退出。
3. **Docker 端口与后端状态**：
   - 宿主机运行着本地 MySQL 3306，导致 Docker 映射端口冲突，现已平滑将 Docker 容器的 MySQL 映射调整为 3308，Spring Boot 后端（8080）与 MySQL/Redis 已完全恢复健康通信。

#### 10.2 终极解决方案：双引擎架构（网页端实时取景 + 桌面端独立窗口）
1. **引擎一：网页端实时摄像头取景 & 秒级录入（默认推荐，100% 画面可见）**：
   - 前端弹窗原生集成 HTML5 `navigator.mediaDevices.getUserMedia`，直接在网页弹窗内渲染 1280x720 实时视频流；
   - 画面配备科幻 HUD 人脸对准虚线框、四角绿光准星、实时状态条；
   - 用户正对镜头后，点击【📸 立即拍照并录入】（或在页面上按键盘快捷键 `S`）；
   - 前端捕获帧数据转 Base64 提交给 `/api/face/register-webcam`；
   - 宿主机后台极速调用 `face_register.py --image`，通过 InsightFace `buffalo_l` 提取 512 维特征向量，裁剪高质量头像存入 `uploads/faces/{id}_snapshot.jpg`，并同步持久化至 MySQL 和 Redis；
   - 弹窗展示成功提示后自动关闭，列表秒级刷新出真实头像与学生档案。
2. **引擎二：桌面独立 OpenCV 窗口调起**：
   - 参数通过 `register_config.json` 传递，杜绝中文字符与括号特殊字符截断；
   - Python 输出流强制配置为 UTF-8，杜绝 Emoji 乱码；
   - 通过 PowerShell `Start-Process cmd.exe -ArgumentList '/k run_register.bat'` 保证在前台活动桌面为用户唤起终端与 OpenCV 窗口。

---

### 十一、 智能课堂实时视觉督导系统（classroom_monitor.py）架构设计

#### 11.1 系统定位与核心指标流
本模块为具身机器人/课堂智能视觉督导系统的推断核心：
1. **输入端**：接入实时 USB/机器人前视摄像头（640x480 或 1280x720 视频流）；
2. **人脸识别与身份锁定（1:N）**：
   - 启动时自动从 Java Spring Boot (`GET /api/face/all`) 拉取已注册学生的 512 维 ArcFace 向量特征底库与学生档案（支持本地 `models/face_db.npy` 兜底）；
   - 视频帧通过 InsightFace `buffalo_l`（CUDA GPU 加速）实时检测人脸并提取特征向量；
   - 与底库学生特征向量计算余弦相似度（阈值 ≥ 0.42），实现精准学号与姓名匹配，未匹配者标记为“访客/未录入学生”；
3. **头部姿态估计与抬头/低头判定（PnP 欧拉角）**：
   - 基于 MediaPipe FaceLandmarker（模型 `models/face_landmarker.task`）提取人脸关键点；
   - 匹配 3D 头部几何模型与摄像头内参，通过 `cv2.solvePnP` 求解旋转向量；
   - 经由 Rodrigues 与 RQDecomp3x3 解算 Pitch 俯仰角：
     - 若 `Pitch >= -12°`：判定为【抬头听课 (UP)】；
     - 若 `Pitch < -12°`：判定为【低头走神/看书/看手机 (DOWN)】；
4. **指标聚合与高频上报通道**：
   - 周期性（1.0 ~ 1.5 秒）将聚合统计数据（检测总人数、抬头人数、低头人数、实时抬头率、实到学号列表、各学生实时 UP/DOWN 姿态）通过 HTTP POST 提交给 Spring Boot 后端：
     `POST /api/visual/report/stream`
   - Spring Boot 更新 Redis 缓存宏观指标，推送折线点与网格卡片；
5. **前端一键督导控制与大屏实时联动**：
   - 前端大屏顶部提供“开始智能督导”控制器；
   - 点击后通过 Node.js/Vite 中间件拉起 `classroom_monitor.py`；
   - 督导过程中前端轮询大屏数据，ECharts 时序曲线与学生状态网格实时随摄像头真实画面同步流动；
   - 点击“停止督导”可随时终止监控进程，释放硬件设备。

#### 11.2 故障排查与终极解决（双屏可视 + 零转义原生拉起）
1. **排查与根因分析**：
   - **配置被覆盖**：前端目录同时存在 `vite.config.js`（早期构建产生的旧文件）与 `vite.config.ts`。Vite 默认优先读取 `.js` 文件，导致对 `.ts` 的所有更新被完全忽略！
   - **路径空格截断**：由于项目路径包含空格（`D:\2026Autumn Semester File\...`），通过 PowerShell `-Command` 包装传递字符串命令时，Windows Shell 会将外层引号脱落，解析为截断的非法路径报错闪退。
2. **终极解决措施**：
   - **清除干扰文件**：彻底删除 `vite.config.js` 与 `vite.config.d.ts`，确保开发服务精准生效 `vite.config.ts`。
   - **零转义原生拉起**：在 Vite 中间件直接使用 Node.js `child_process.spawn(pythonExe, [script, '--port', '8088'])`，通过底层 OS 数组传参，100% 免疫路径空格与引号转义。
   - **内置 HTTP MJPEG 视频流服务**：`classroom_monitor.py` 内置多线程轻量 HTTP 服务器（端口 8088），提供 `/video_feed`（MJPEG 视频流）、`/health`（健康探活）与 `/stop`（优雅释放）。
   - **大屏实时画中画**：在前端可视化大屏左侧新增【🤖 具身智能机器人·摄像头感知视窗】，点击“开始智能视觉督导”后，不仅调起摄像头硬件，而且大屏中央实时显示带人脸识别框、学生姓名学号、3D 姿态角 HUD 的现场画面！
   - **平滑停止与硬件释放**：点击“停止视觉督导”时，前端直接向 8088 端口下发 `/stop`，Python 优雅关闭视频捕获设备并释放资源。

---

### 十二、 背景 B“爱教学”全链路教学质量平台分层解耦与功能完善方案

#### 12.1 解耦驱动与系统定位
根据东北大学软件学院《软件项目管理》实验一交付报告，项目核心愿景聚焦于高校教学质量数字化管理（查课程 -> 善督导 -> 优课堂），同时确立两项刚性合规红线：
1. 坚决排除未经师生授权的教室摄像头实时抓拍与人脸识别考勤（红线禁止）；
2. 坚决排除基于计算机视觉的学生微表情、疲劳度、走神热力图等伪需求（红线禁止）。
因此，必须对现有项目进行**物理与逻辑层面的彻底解耦**：
- **边缘微格/视觉感知层（Edge Service）**：将原根目录下的 Python 视觉原型剥离归拢至独立微格感知模块（`services/edge-perception`），仅通过 US-12 规范的 RESTful API 提供“微格视频切片与课堂录像元数据”异步挂载，杜绝其侵入核心业务；
- **核心业务平台后端（Core Backend）**：Spring Boot 采用高内聚低耦合的领域模块化架构（Domain-Driven Clean Architecture），分为 `course`（课程排课认证）、`resource`（教学资源与微格切片）、`supervision`（督导评教预警与雷达图）三大核心模块；
- **现代前端多角色工作台（Frontend Portal）**：Vue 3 + Vite + Tailwind CSS + ECharts 构建面向教研室主任、任课教师、教学督导三大角色的专业工作台，实现全链路功能闭环。

#### 12.2 后端领域分层与四表关联核心 Schema
对齐实验一思考题 4 要求的教务核心四表关联概念验证（PoC）：
1. **`Course`（课程档案表）**：`course_code`（全局唯一）、`course_name`、`credits`、`hours`、`course_type`、`prerequisites`、`objectives`、`assessment_method`；
2. **`CourseOffering`（教师开课表）**：关联 `course_id`、`teacher_name`、`academic_term`、`student_count`、`status`；
3. **`CourseSchedule`（排课时段教室表）**：关联 `offering_id`、`classroom`（如文管 A447、信息馆 B201）、`week_range`（如 1-16周）、`day_of_week`、`period`、`conflict_check`；
4. **`CourseSyllabus` 与 `GraduationIndicatorMapping`（教学大纲与12条毕业要求指标点映射表）**：锁定指标点权重、支撑度矩阵与大纲版本。

#### 12.3 督导评教、预警与分析模型
1. **`SupervisionEvaluation`（随堂听课评价表）**：
   - BOPPPS 四维打分：教学态度 (0-25)、教学内容 (0-25)、教学方法 (0-25)、教学效果 (0-25)，总分 100 分；
   - 质性评语：教学亮点与改进建议（限 500 字）；
   - 脱敏归档与延迟流转机制：填报后状态为 `PENDING_DESENSITIZE`，24 小时后脱敏归档至 `PUBLISHED` 并对任课教师开放。
2. **督导覆盖率与预警引擎**：
   - 动态计算学院课程总门数、已督导门数与督导覆盖率；
   - 自动生成预警：覆盖率 < 30% 标记黄色预警，评分均分 < 75 分标记红色低分预警。
3. **教学质量 4 维雷达图与报表一键导出**：
   - 支撑任课教师与教研室主任查看各维度均分雷达图；
   - 支持一键导出符合高校教务规范的年度质量分析 Excel/PDF 报表。

## 2026-09-16 检查记录
- 前端基线构建通过；构建会重新生成 vite.config.js，历史“删除旧配置”方案未消除根因。
- Docker 初始未运行；本机 Maven 未配置 PATH，但存在本地 Maven 分发包。
- 当前目录没有 Git 元数据，不能依靠 git diff 验证变更，使用逐文件记录与测试。
- 摄像头视觉监控“视觉进程已退出”故障排查与修复：
  1. 故障原因：原 Vite 开发服务器子进程继承了旧终端的沙箱限制标记，调用 virtualenv 包装器时触发 Windows `CreateProcessW` 限制（退出码 101 / EPERM）；同时原生 `classroom_monitor.py` 在首帧摄像头读取轻微延迟时缺乏帧重试，且 `camera-plugin.mjs` 原使用 `stdio: ignore` 吞掉了真实报错。
  2. 修复措施：
     - `classroom_monitor.py` 增加连续读取帧重试缓冲机制（容忍前序暂态空帧）与 FutureWarning 抑制。
     - `frontend/dev/camera-plugin.mjs` 增加标准输出与标准错误管道捕获，将异常详细原因回传并写入 `docs/runtime/monitor_spawn.log`。
     - `frontend/src/api/index.ts` 优化启动轮询状态判定，精确上报底层进程退出原因。
     - `start_project.ps1` 修正启动脚本中含空格路径的参数引号转义。
     - 重启 Vite 独立服务并完成全链路接口闭环压测：`start-monitor` 状态成功转为 `running: true`，摄像头与模型加载顺利，`stop-monitor` 可正常下线。
- 教学工作台面板假数据排查与真实数据库接入治理：
  1. 根因剖析：
     - Windows 环境下 Maven 编译时缺少 UTF-8 源码与输出编码配置，导致 `TeachingDataInitializer.java` 常量在编译为字节码时转为包含乱码字符（`??????`）并灌入 MySQL，导致前端按学期/教师名称无法检索。
     - 前端视图中存在防御性硬编码三元表达式（如 `currentTeacher === '郭军' ? '《软件项目管理》' : '《计算机组成原理》'`、`'95 人次' : '120 人次'`、指标卡片 `|| 4`、`|| 3`、`|| 75.0` 等），引发“面板造假”质疑。
  2. 治理措施：
     - `backend/pom.xml` 配置 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` 与 `maven-compiler-plugin` 的 `-encoding UTF-8`。
     - 清洗 MySQL 数据库并重新使用 UTF-8 启动后端，重新灌入无乱码的东北大学真实课程（CS3001、CS2002、CS2001、CS3002）、开课班次、排课时段及随堂评教。
     - `TeacherDeskView.vue` 移除硬编码三元，教师下拉框改为动态提取，课程名、班级名、排课教室（联动 `scheduleApi.getAll` 真实排课时段）与班额全动态绑定真实开课实体。
     - `SupervisorDeskView.vue` 彻底移除 `|| 4`、`|| 3`、`|| 75.0`、`|| 1` 等假数据 fallback；动态加载 `availableTerms`，全量展现真实开课卡片。
     - 前端执行 `npm run build` 校验，0 编译错误；后端全部接口经真实调用验证通过。

## 2026-09-17 UI 浅色简约风重构方案设计 (Operate 模式高阶工艺)

### 1. 现状痛点诊断 (Heuristic & Craft Audit)
* **暗黑赛博过度包装**：全站采用 `#0a0f1d`（暗黑高对比底色）+ `#00f2fe`（高饱和霓虹青）+ 强烈发光阴影（`shadow-glow-cyan`, `shadow-glow-green`），属于典型的“极客科技风大屏”，不符合高校教务日常办公、教学督导、课程管理所需的“温和、护眼、专业、高可读性”诉求。
* **图标系统混乱**：大量使用系统 Emoji（如 👔、👨‍🏫、🕵️‍♂️、🎥、👥、📊、➕、📁 等）替代规范矢量图标，跨平台渲染参差不齐，破坏学术级管理系统的严谨质感。
* **信息层级与留白不足**：玻璃拟态卡片（`glass-card`）过度使用毛玻璃滤镜与多层嵌套边框，造成视觉噪音；表单输入控件缺乏统一的浅色态交互轮廓。

### 2. 浅色简约设计语言系统 (Minimalist Light Design Tokens)
* **模式定位**：Operate 模式（任务驱动、高频扫描、极简克制、信息层级清晰）。
* **色彩基调 (Calm & Restrained Palette)**：
  - 应用主背景：`#f8fafc` (Tailwind `slate-50`)，自然柔和，无视觉压迫感。
  - 卡片与工作区表面：`#ffffff` (纯白 Surface)，搭配 1px 精致边框 `#e2e8f0` (slate-200)。
  - 辅助浅灰容器：`#f1f5f9` (slate-100)，用于导航药丸底色、搜索框底色与表格表头。
  - 核心字色层级：
    - 一级主标题/重要数据：`#0f172a` (slate-900)，对比度 > 10:1。
    - 二级正文与标签：`#334155` (slate-700)。
    - 三级弱化说明/占位符：`#64748b` (slate-500)。
    - 四级极弱注脚：`#94a3b8` (slate-400)。
  - 品牌与功能色 (语义化微色温)：
    - 主功能/主行动：学术沉稳靛蓝 `indigo-600` (`#4f46e5`)，激活背景 `indigo-50`，弱边框 `indigo-200`。
    - 正常出勤/就绪：健康翡翠绿 `emerald-600` (`#059669`)，激活背景 `emerald-50`。
    - 待办/提示：暖色琥珀 `amber-600` (`#d97706`)，激活背景 `amber-50`。
    - 缺勤/冲突/停止：质感玫红/红 `rose-600` (`#e11d48`)，激活背景 `rose-50`。
* **深度与投影 (Elevation & Shadows)**：
  - 彻底剥离霓虹光晕（`glow-*`）与硬边投影。
  - 采用多层微弥散投影：卡片默认 `box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05)`，悬浮轻微提升 `box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.05)`。
* **图标规范化**：
  - 全面引入工程中已有的 `lucide-vue-next` 现代矢量线框图标，笔触统一为 1.75px 或 2px，替代全部 Unicode Emoji。
* **ECharts 图表浅色化重构**：
  - 折线趋势图（`FocusTrendChart.vue`）：背景透白，横纵网格线替换为柔和浅灰虚线（`#f1f5f9`），主趋势线使用雅致靛蓝（`#4f46e5`）辅以平滑透明微弱浅蓝渐变面积（`rgba(79, 70, 229, 0.12)` 至 `rgba(79, 70, 229, 0)`），提示框改为浅色悬浮白卡配阴影。
  - 督导雷达图（`TeacherDeskView.vue`）：移除暗黑蛛网背景，采用浅灰等距同心环（`#e2e8f0`），轴线与刻度字色适配深灰（`#475569`），覆盖面以柔和半透靛蓝描摹。

## 2026-09-18 项目目录重构
采用 frontend/、backend/、vision/ 三个主要模块，scripts/ 集中运行工具，旧视觉副本归档。共享 uploads/ 与已有 API URL 保持稳定。详细映射及验证见 [重构记录](refactor-20260918.md)。


## 2026-09-18 根目录进一步收拢
用户指出根目录文件仍多，要求实际整理并写好说明。保留前端、后端、视觉、scripts 和五个规定目录；零散日志与上传数据集中 runtime/，部署和联调测试下沉 scripts/，backup 与旧入口归档 docs/archive/，根目录只保留 start_project.ps1 一个启动入口。移动 raw 以外文件，修正依赖路径，校验文件完整性与构建后更新 README 和详细迁移说明。不调用摄像头、不更改真实数据库记录。

## 2026-09-18 教学督导工作台【全院开课总课表 / 听课日程看板】全面落地
- **业务诉求背景**：
  教学督导专家（Supervisor）的核心日常业务为随堂听评课。此前督导工作台缺少全局开课总课表与听课日程看板，且开课卡片中未注明上课教室与节次时段，无法支持督导实地进班听课的实际工作需求。
- **架构与功能设计落地**：
  1. **工作台一级 Tab 重构升级**：
     - 新增第一核心 Tab：【全院开课总课表 / 听课日程看板 (US-03/06)】，默认直接聚焦课表日程。
     - 保留【待督导目标课程多维检索 (US-06)】与【教学质量预警中心 (US-16)】。
  2. **双视图模式无缝切换**：
     - **高校标准周历矩阵课表**：横向周一至周日 7 列，纵向 5 个标准大节次时段（08:00-09:35、10:05-11:40、13:30-15:05、15:35-17:10、18:30-20:05）。单元格内高亮突出主讲教师徽章（第一视觉焦点）、课程名称代码、教室（翠绿标签）、班级与班额。
     - **多维排课日程卡片看板**：卡片式流式呈现，突出任课教师第一视觉，具备 P1 级分页控制栏（支持每页 3 / 6 / 9 条）。
  3. **多维快捷组合筛选工具栏**：
     - 星期几筛选（周一至周日）
     - 节次时段筛选（1-2节至9-10节）
     - 课程性质筛选（专业核心课、专业基础课、通识必修课、专业选修课）
     - 授课教师检索（高亮模糊过滤）
     - 教室筛选（文管 A447、信息馆 B201 等）
     - 一键重置筛选
  4. **全链路业务闭环联动**：
     - 课表卡片直接提供【随堂评课 (US-13)】（一键唤起 BOPPPS 四维打分表并自动回填课程教师信息）、【进班监控】（进入实时考勤姿态大屏）、【预审课件】（免密查阅大纲与课件）。
  5. **开课检索卡片补全时空信息**：
     - 在待督导开课复合检索卡片中关联并直观展示教室地点（如 `📍 文管 A447`）与排课时段（如 `⏰ 周三 第3-4节 (1-16周)`）。

## 2026-09-18 全量教务底座初始化 (initialize.sql) 与全系教师快捷登录矩阵上线
- **业务诉求背景**：
  1. 用户要求生成全量基础课程数据的 SQL 脚本，命名为 `initialize.sql` 并注入 MySQL 数据库；
  2. 包含 8 个班级学生档案，每个班严格恰好 10 名学生（全院共计 80 人），且人脸数据库（`face_feature`）512 维特征向量与底库文件完整关联；
  3. 涵盖 8 位专任教师（郭军、姜琳颖、赵广生、王伟、董晓梅、刘博、陈立新、孙志刚）及李主任、张督导等管理账号，统一密码 `123456`；
  4. 满足“一个老师有多门课程，分布在不同时间段”，并在周三第3-4节、周二第1-2节等时段实现多班级、多教师在不同教室的并行排课；
  5. 在平台登录首页（`LoginView.vue`）显式呈现所有老师的快捷登录入口，具备一键直登与凭证回填，并配置带有持久化存储（localStorage）的显隐折叠开关。
- **架构与实现落地**：
  1. **SQL 自动化生成器与初始化脚本**：
     - 开发 `scripts/generate_initialize_sql.py`，产出 `initialize.sql`（457KB）并备份至 `scripts/deploy/mysql/init/initialize.sql`。
     - 包含：5 个专业（SE、CS、AI、DS、SEC）、8 位教师档案、10 个教务权限账号、17 门精品专业课程、8 个班级（80 名学生档案 + 80 条 512 维人脸特征向量）、17 个开课班次及排课日程（包含周三3-4节三门并行、周二1-2节两门并行）、80 名学生选课记录、12 项工程认证国标指标点矩阵及督导评教记录。
     - 解决 Windows PowerShell 管道编码截断问题，通过 Docker 内部 `SOURCE /tmp/initialize.sql;` 保证 UTF-8 字符集无乱码写入。
  2. **后端数据持久化保护 (TeachingDataInitializer.java)**：
     - 修改学生数校验阈值，尊重 MySQL 现有 80 名学生真实持久化数据，避免启动时重复篡改。
     - Maven 重新编译打包并重启 daemon 后端服务，API 运行平稳。
  3. **登录页全系教师快捷登录体验矩阵 (LoginView.vue)**：
     - 增加顶部交互式 Toggle 开关（`showQuickLogin`），支持一键展开/折叠全量教师快捷面板，并将用户选择持久化保存在 `localStorage` 中（默认开启）。
     - 支持三维分类筛选 Tab：【全部人员 (10)】、【专任教师团队 (8)】、【管理与督导 (2)】。
     - 8 位专任教师卡片显式呈现姓名,职称,所属教研室,账号密码,负责的多门主讲课程,时间节次,授课教室与班级，并高亮提示“并行授课”标记。
     - 每一位教师与督导卡片均配备【⚡ 一键直登】（直接完成身份校验并直入对应工作台）与【填入】（回填账号密码至左侧表单）双重操作。
  4. **任课教师工作台多课程无缝切换 (TeacherDeskView.vue)**：
     - 针对同一教师主讲多门课程的业务场景，在教师工作台顶部新增【当前主讲课程】下拉切换器，教师可随时在多门主讲课程间平滑切换，大纲目标、12 项毕业要求指标点与课件资源随之动态联动。

## 2026-09-19 任课教师专属课程隔离（未开课显示无）与考勤归档记录操作人身份追溯
- **业务诉求背景**：
  1. 任课教师（如姜琳颖老师）登录系统后，在大屏上只允许查看自己的主讲课程，严禁越界查看或操作其他教师（如郭军老师）的课程；
  2. 如果任课教师当前没有处于授课时段的主讲课程（即未开课），“当前授课班级”必须显示为“无”，且摄像头开启、模拟流、下课归档等功能保持禁用；
  3. 考勤归档记录必须明确记录并展示“是谁考的勤”：是督导、主任还是任课教师，并在档案表格中直观展示操作人姓名与身份彩色徽章；
  4. 同步更新 `initialize.sql` 基础数据库初始化脚本，包含 `t_attendance_session` 建表与全字段种子数据，确保全新部署开箱即用。
- **架构与实现落地**：
  1. **数据库扩展与回填**：
     - `t_attendance_session` 扩展字段：`operator_name` (VARCHAR 64)、`operator_role` (VARCHAR 32)、`operator_title` (VARCHAR 64)；
     - 历史归档记录（会话 #1~#7）全量补全回填为 `郭军` / `TEACHER` / `任课教师`；
     - 在 `initialize.sql` 与 `scripts/deploy/mysql/init/initialize.sql` 中正式定义 `t_attendance_session` 表结构与标准化考勤种子数据（教室规范为 `文管 A447`）。
  2. **后端服务增强 (AttendanceServiceImpl.java)**：
     - `startSession` 与 `finishSession` 自动解析前端传入的操作人姓名、角色代码与中文职称；
     - 智能兜底：若角色为 `SUPERVISOR` 自动映射为 `教学督导`，`DIRECTOR` 自动映射为 `教研室主任`，`TEACHER` 映射为 `任课教师`，名字默认取开课主讲教师；
     - 单元测试 `AttendanceServiceTest` 扩充对不同角色操作人身份的验证，用例 100% 通过。
  3. **前端大屏角色隔离与身份徽章 (AttendanceDashboardView.vue)**：
     - 接收认证用户 `loggedInUser`（若无则兜底请求 `/api/v1/auth/me`）；
     - `allowedOfferingList`：任课教师只允许查看自身负责的主讲课程；
     - `activeOfferingList`：结合排课时段动态计算。若教师无正在开课的课程，下拉框显示不可选的【无】，大屏待机视窗提示“您当前暂无处于授课时段的个人课程”；
     - 发起考勤与下课归档时，自动将当前登录人员的姓名与身份传给后端；
     - 【考勤归档记录】档案表格新增【考勤人员】列，提供紫蓝（教师）、翠绿（督导）、琥珀（主任）三色高辨识度徽章展示。

## 2026-09-19 考勤归档记录弹窗全屏自适应排版优化与历史归档数据修正（张督导）
- **问题定位**：
  1. 弹窗此前宽度限制为 `max-w-4xl`（896px），面对 10 列信息严重挤压；最右侧【状态】列文字“已归档”被挤成纵向折行甚至右缘遮挡；
  2. 历史归档数据此前因控制台字符集传入 latin1 乱码，且用户明确要求将数据库之前对应的考勤历史记录修改为“张督导”；
  3. 考勤起止时间缺少完整年份信息。
- **优化与修复落地**：
  1. **弹窗布局与表格排版重构 (AttendanceDashboardView.vue)**：
     - 弹窗宽度扩展为 `max-w-6xl`（1152px），自适应大屏视窗；
     - 表格容器赋予 `overflow-x-auto overflow-y-auto` 双向滚动防护与细腻圆角外边框；
     - 表格设置 `min-w-[960px] border-collapse`，表头 `th` 与数据单元格 `td` 统一施加 `whitespace-nowrap px-3.5 py-3`；
     - 考勤起止时间展示完整年月日时分（如 `2026-09-15 15:38 至 2026-09-16 16:52`）；
     - 最右侧【状态】列设置 `text-center whitespace-nowrap`，徽章设定 `min-w-[64px] px-2.5 py-1`，杜绝文字垂直换行与截断。
  2. **数据库历史记录修正**：
     - 采用无 BOM 的标准 UTF-8 脚本并在容器内以 `SET NAMES utf8mb4` 执行更新；
     - 历史归档数据全部统一修正为：`operator_name = '张督导', operator_role = 'SUPERVISOR', operator_title = '教学督导'`；
     - `GET /api/v1/attendance/offering/1` 验证返回全量会话（#1 至 #8）均为“教学督导 (张督导)”，中文展示清晰工整。
  3. **初始化脚本同步**：
     - `initialize.sql` 与 `scripts/deploy/mysql/init/initialize.sql` 第 15 节种子数据同步更新为 `张督导`、`SUPERVISOR`、`教学督导`。

## 2026-09-19 实验二补齐分支（feat/exp2-supplement-auth-fix）与登录测试 JwtTokenProvider 注入修复
- **分支创建背景**：
  - 新建专用分支 `feat/exp2-supplement-auth-fix`，规范承接实验二自动化回归体系补齐与登录鉴权测试修复。
- **问题根因分析**：
  - 在引入 Spring Security 与无状态 JJWT 认证机制后，`AuthController` 构造器新增了 `JwtTokenProvider` 依赖注入；
  - 既有单元测试 `AuthControllerTest` 仅通过 Mockito 模拟了 `UserAccountRepository`，未对 `JwtTokenProvider` 进行 Mock 注入；
  - 导致在执行 `testLogin_Success` 时调用 `jwtTokenProvider.generateToken(vo)` 抛出 `NullPointerException`，阻断了 `scripts/run-tests.ps1` 自动化流水线。
- **修复措施与验证落地**：
  1. **单元测试完善 (AuthControllerTest.java)**：
     - 使用 `@Mock private JwtTokenProvider jwtTokenProvider;` 补齐模拟对象；
     - 在 `testLogin_Success` 中注入 `when(jwtTokenProvider.generateToken(any(UserVO.class))).thenReturn("mock-jwt-token-12345");`，并验证响应体包含 Token；
     - 扩展增设 `testGetMe_WithValidBearerToken_ReturnsUser`（验证 Bearer Token 头部解析）与 `testRegisterSupervisor_Success`（验证督导专家在线注册签发 Token）测试用例；
  2. **全栈自动化检查全绿达成**：
     - 运行 `powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1`；
     - **后端测试**：55 项单元测试 100% 通过（0 失败，0 错误，耗时 5.995s）；
     - **前端测试**：10 项单元测试 100% 通过（耗时 235ms）；
     - **前端生产构建**：Vite 构建 0 错误（耗时 7.72s，成功产出 dist 包）；
     - **边缘视觉回归**：5 项 Python 测试 100% 通过（耗时 149ms）；
     - 最终顺利输出 `All automated checks passed.`。

## 2026-09-19 实验二第 1 步：补齐公共身份、权限和数据关联全面落地
- **任务目标与交付基准**：
  1. 实验二教务业务接口统一要求登录：未登录返回 HTTP 401，无权限返回 HTTP 403；
  2. 统一授权规则（Deny-by-Default）：主任管理所属教研室，教师访问本人关联课程，督导读取授权专业；无授权、专业缺失等情况默认不放行；
  3. 列表、详情、历史、草稿、排课、大纲及相关资源入口使用同一套统一授权服务，避免绕过列表直接访问 ID 越权；
  4. 主任工作台增加督导账号创建、专业授权入口；主任只能分配自己管辖的专业；关闭公开注册接口及入口；
  5. 密码改为 BCrypt 哈希存储，既有账号平滑透明升级；课程访问以服务端当前数据库授权为准，杜绝旧 JWT 长期持有已撤销权限；
  6. 选课以“班次—学生”关系 (`OfferingStudentEnrollment`) 为唯一真值依据，行政班保留为学生属性且不可被选课篡改，考勤与推流脱钩行政班并优先取班次选课名单；
  7. 建立最小班次维护能力（CRUD），为后续教师发布课程提供底座支持；
  8. 全量自动化检查脚本（`scripts/run-tests.ps1`）恢复并通过。
- **架构重构与具体实施**：
  1. **统一教务授权服务 (CourseAuthorizationService.java)**：
     - 践行 Deny-by-Default：无登录上下文时直接抛出 `UnauthorizedException`（401）；角色越界、未授权专业或课程/班次专业缺失时抛出 `ForbiddenException`（403）；
     - 主任隔离：`checkDirectorCourseAccess` 严格校验主任所属部门与课程 `department` 一致性；
     - 教师绑定：`checkTeacherCourseAccess` / `checkTeacherOfferingAccess` 严格校验教师是否为主讲人或被关联到该开课班次；
     - 督导只读与专业鉴权：督导仅能读取 `authorizedMajors` 包含的专业课程，专业缺失默认拒绝，写操作（`validateCourseWrite` / `validateOfferingWrite`）一律拦截；
     - 控制器纳管：`CourseController`（详情、保存、删除、班次维护）、`CourseScheduleController`（排课调度）、`SyllabusController`（大纲目标与指标点）、`CourseResourceController`（课件资源）、`CourseOfferingHistoryController`（历史人次）全链路接入授权校验。
  2. **主任专属督导账号开通与专业授权 (DirectorController.java & DirectorDeskView.vue)**：
     - 后端暴露 `/api/v1/director/managed-majors`、`/api/v1/director/supervisors`、`POST /api/v1/director/supervisors`、`PUT /api/v1/director/supervisors/{id}/majors`；
     - 严格校验 `validateMajorsInDirectorJurisdiction`，主任越权授权非管辖专业立即抛出 403；
     - 关闭公开注册通道：`AuthController` 废弃公开注册端点，`LoginView.vue` 下线注册入口；
     - 前端 `DirectorDeskView.vue` 增设 Tab 4【督导建档与专业授权 (US-07)】，展示主任管辖专业公示条、督导列表与专业授权徽章，并提供严格限定主任管辖范围的多选弹窗。
  3. **密码 BCrypt 安全哈希与服务端实时授权**：
     - `AuthController` 登录校验若检测到旧明文密码，使用 `passwordEncoder.matches` 比对并在验证通过后自动更新为 `$2a$10$...` 哈希；
     - `JwtAuthenticationFilter` 在每次请求时通过 Token 中的用户名从 `UserAccountRepository` 重新装载最新的用户实体，确保主任调整督导专业授权后立即生效。
  4. **选课名单真值唯一依据与行政班脱钩**：
     - `OfferingStudentEnrollmentRepository` 补齐按班次与学号的选课删除及存在性校验；
     - `CourseServiceImpl` 选课逻辑彻底从学生实体 `className` 脱钩，不篡改学生行政班；
     - `AttendanceServiceImpl` 考勤应到人数优先取班次选课名单真实人数；
     - `VisualDashboardServiceImpl` 推流解析与实时学生状态严格基于班次选课名单；
     - `TeachingDataInitializer` 启动时自动升级迁移存量数据，保障种子数据一致性。
  5. **开课班次最小维护能力 (CourseOffering CRUD)**：
     - `CourseController` 完善 `POST /api/v1/courses/offerings`、`PUT /api/v1/courses/offerings/{id}`、`DELETE /api/v1/courses/offerings/{id}`；
     - 前端 `api/index.ts` 补齐 `createOffering`、`updateOffering`、`deleteOffering`；
     - `DirectorDeskView.vue` Tab 2 增加【新建开课班次】快捷弹窗，实现开课班次全生命周期管理。
- **自动化测试验证与指标**：
  - 新增 `CourseAuthorizationServiceTest`（4 个用例）：覆盖未登录 401、主任隔离 403、教师未关联 403、督导只读与专业越权 403；
  - 新增 `DirectorSupervisorManagementTest`（4 个用例）：覆盖主任创建督导管辖专业成功、跨专业 403、更新督导专业成功与跨专业 403；
  - 修复 `AuthControllerTest`、`CourseSupervisorRbacTest`、`CourseOfferingHistoryTest`；
  - **后端单元测试**：63 项测试全部通过（0 失败，0 错误，BUILD SUCCESS）；
  - **前端单元测试**：10 项测试全部通过；
  - **前端生产构建**：Vite 构建 0 错误（产出生产 dist 包）；
  - **边缘感知测试**：5 项 Python 测试全部通过；
  - `powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1` 顺利输出 `All automated checks passed.`。

