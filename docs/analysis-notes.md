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
