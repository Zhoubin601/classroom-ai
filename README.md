# Classroom AI Demo · 爱教学

面向课程管理、教学资源共享、督导评价与教学改进的多角色演示系统。项目采用 Vue 3 前端、Spring Boot 后端和独立 Python 视觉模块，包含实验一规划的 21 条用户故事，以及课堂考勤、人脸注册和实时看板等扩展模块。

## 功能图文总览

**[打开功能图文总览 →](output/功能汇总/20261016-exp1-exp4-feature-trace-v1.html)**

按 21 条用户故事查看页面操作、业务流程、源码定位、接口、数据变化和测试证据，适合功能演示、答辩讲解与开发交接。HTML 正文与图片支持离线阅读；下载或克隆项目后用浏览器打开，在 GitHub 中查看时请先下载 HTML。

[快速启动](#快速启动) · [功能范围](#功能范围) · [配置说明](#配置说明) · [测试与验收](#测试与验收) · [项目目录](#项目目录) · [常见问题](#常见问题)

## 功能范围

| 阶段 | 用户故事 | 已实现功能 |
| --- | --- | --- |
| Sprint 1：课程底座 | US-01～04、US-06 | 课程建档与 CSV 模板导入、预检确认、内容草稿与发布、多教师排课及冲突检查、历史开课与累计人次、权限范围内组合检索 |
| Sprint 2：资源与督导 | US-05、US-07～10、US-13～15 | 培养方案导入、大纲指标映射与历史版本、按章节上传与环节标签、受控水印预览、教研室资源共享、评价暂存与提交、审核和延迟脱敏反馈、学期覆盖率追溯 |
| Sprint 3：分析与改进 | US-11～12、US-16～21 | 资源历史与当前版、授权微格视频与区间回看、质量预警、评价雷达图、CSV/XLSX 报表、课程类别问题分布、评教模板版本、建议与行动反馈闭环 |

### 四类角色

| 角色 | 主要操作 |
| --- | --- |
| 教研室主任 | 维护课程、开课与排课，查看指标矩阵，审核评价，查看质量预警并导出报表 |
| 任课教师 | 编辑课程内容、维护大纲和资源，查询授课历史，查看本人反馈与分析，登记改进行动 |
| 教学督导 | 在授权专业内检索课程、预览材料、提交评价与建议，查看覆盖率和问题分布 |
| 系统管理员 | 维护评价模板及权重版本，管理微格视频与分析元数据复核 |

具体可见范围由后端权限校验决定。主任、教师也可在授权课程内使用微格入口；共享读取权限不授予资源修改权限。管理员通过显式初始化创建，配置项见下文。

### 关键业务口径

- **大纲指标**：数量由导入的培养方案决定；当前未锁定版本可维护，历史版本只读并保留独立映射。
- **评价评分**：示例维度为教学态度、内容、方法、效果。正式提交按已发布模板的正权重维度校验，明确的 0 分有效；草稿允许不完整。模板发布后保留历史权重快照。
- **反馈发布**：主任审核通过后按配置延迟向教师反馈，默认 24 小时；教师响应对督导身份脱敏。小样本下不承诺无条件匿名。
- **统计分析**：覆盖率以选定学期有效开课课程为分母，并按课程去重；报表为 CSV/XLSX，按已生效筛选导出。预警供人工复核，不直接作为考核结论。
- **微格回看**：挂载授权视频、片段区间和元数据，服务端探测视频时长；不据此声称已实现自动切片或自动课堂分析。

课堂考勤、人脸注册、视觉推流和实时看板位于独立扩展链路。摄像头、多人识别、GPU 和目标设备效果需要现场实测，不由上述 21 条故事的软件测试证明。

## 技术组成

| 模块 | 技术与职责 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Tailwind CSS、ECharts、PDF.js；多角色工作台、统计图表与预览 |
| 后端 | Java 21、Spring Boot 3.3.4、Spring Security/JWT、Spring Data JPA；业务、鉴权与数据服务 |
| 数据 | MySQL 8.0、Redis 7.2；业务持久化、缓存和实时状态 |
| 文件与视频 | LibreOffice 转换 Office 文件，PDFBox 处理 PDF，ffprobe 探测视频时长 |
| 视觉模块 | Python、OpenCV、InsightFace、ONNX Runtime；可选 YOLO/MediaPipe 实验依赖 |

日常启动方式为 **本机 Vite + Docker 中的后端、MySQL、Redis**。Python 视觉模块按需启动。

## 快速启动

以下命令均在项目根目录的 PowerShell 中执行。

### 环境准备

- Windows、PowerShell，已安装并启动 Docker Desktop 的 Linux 容器引擎，`docker info` 能正常返回。
- Java 21、Maven、Node.js 和 npm。后端在本机构建 JAR，再构建 Docker 运行镜像。
- 首次运行需要下载 Maven/npm 依赖、容器镜像及镜像内工具；后续可复用本地缓存。
- 使用视觉功能时，另准备 Python 3.10+、对应依赖、模型及摄像头；仓库中的本地 `.venv1/` 不随 Git 分发。

### 启动

Windows 可双击根目录的 `start_project.bat`，或执行：

```powershell
# 构建后端 JAR 和 Docker 镜像，启动数据库、缓存、后端及前端
.\start_project.ps1

# 显式重建；与 SkipBuild 同时使用时以 Rebuild 为准
.\start_project.ps1 -Rebuild

# 复用已有 JAR 和镜像；仅在确认产物对应当前代码时使用
.\start_project.ps1 -SkipBuild

# 不自动打开浏览器，不等待交互输入
.\start_project.ps1 -NonInteractive
```

脚本会检查 Docker Engine，**不会代替用户启动 Docker Desktop**；前端依赖缺失时会尝试安装。正常启动不自动运行整套测试。

| 服务 | 默认访问位置 | 说明 |
| --- | --- | --- |
| Web 前端 | [http://127.0.0.1:5173](http://127.0.0.1:5173) | 从登录页进入相应角色工作台 |
| Java 后端 | [http://127.0.0.1:8080](http://127.0.0.1:8080) | 业务接口通常需要登录鉴权 |
| MySQL | `127.0.0.1:3308` | Compose 暴露的本机端口 |
| Redis | `127.0.0.1:6379` | 缓存服务 |
| 视觉推流 | `127.0.0.1:8088` | 按需启动，前端开发代理转发视频流 |

使用已有演示账号登录。管理员首次创建需显式启用 `CLASSROOM_ADMIN_BOOTSTRAP_ENABLED` 并提供初始化身份；用户名与密码在本地进程环境中配置，使用后关闭初始化开关，不写入仓库。具体操作见 [Sprint 3 使用说明](output/20261012-sprint3-user-guide-v2.md)。

### 停止

可双击 `stop_project.bat`，或执行：

```powershell
# 按 PID 文件停止本机进程，并停止后端容器；保留 MySQL/Redis 运行
.\stop_project.ps1

# 同时停止 MySQL/Redis
.\stop_project.ps1 -StopDocker
```

停止操作保留数据库卷和上传文件。运行日志及 PID 位于 `runtime/logs/`。

### 分模块操作

```powershell
# 只启动 MySQL/Redis；或单独启动前端
.\scripts\start_services.ps1
.\scripts\start_frontend.ps1

# 查看容器及后端日志
.\scripts\compose.ps1 ps
.\scripts\compose.ps1 logs -f classroom-backend

# 检查 Python 入口；使用已配置的本地环境
.\.venv1\Scripts\python.exe -m vision --help
```

前端独立启动命令会在当前终端持续运行。视觉启动、注册及诊断见 [视觉模块说明](vision/README.md)。

## 配置说明

- [Compose 配置](scripts/deploy/docker-compose.yml) 与 [环境样例](scripts/deploy/.env.example) 位于 `scripts/deploy/`。本地 Compose 环境文件放在该目录的 `.env`。
- 后端配置来源为 [application.yml](backend/src/main/resources/application.yml)；前端代理和本地摄像头适配分别见 [vite.config.ts](frontend/vite.config.ts) 与 [camera-plugin.mjs](frontend/dev/camera-plugin.mjs)。
- **应用支持的环境变量需要实际传给对应进程或容器才会生效。** `.env` 不会自动覆盖所有后端配置；Compose 中未映射的变量需同步调整容器环境。部分服务端口在启动脚本中固定，修改时应同步核对代理与脚本。

| 配置项 | 用途与默认值 |
| --- | --- |
| `CLASSROOM_API_PROXY` | Vite 开发代理的后端地址，默认 `http://127.0.0.1:8080` |
| `CLASSROOM_PYTHON` | 前端本地摄像头适配器使用的 Python，默认项目 `.venv1` 中的解释器 |
| `CLASSROOM_MONITOR_PORT` | 视觉服务与视频代理端口，默认 `8088` |
| `CLASSROOM_RESOURCE_MAX_FILE_BYTES` | 后端教学资源大小限制，默认 `104857600` 字节（100 MiB） |
| `CLASSROOM_RESOURCE_ALLOWED_EXTENSIONS` | 资源允许扩展名，默认 `ppt,pptx,doc,docx,pdf` |
| `CLASSROOM_RESOURCE_PREVIEW_MINUTES` | 预览票据有效分钟数，默认 `5` |
| `CLASSROOM_FEEDBACK_DELAY_HOURS` | 审核后反馈延迟小时数，默认 `24` |
| `CLASSROOM_LOW_SCORE_THRESHOLD` / `CLASSROOM_LOW_COVERAGE_THRESHOLD` | 低评价/低覆盖阈值，默认 `75` / `30` |
| `CLASSROOM_SOFFICE` / `CLASSROOM_FFPROBE` | Office 转换与视频探测工具路径 |

Docker 运行镜像已安装 LibreOffice、ffmpeg/ffprobe 和中文字体。直接在 Windows 运行 JAR 时，需要本机提供相应工具。

共享上传文件位于 `runtime/uploads/`。全新 MySQL 数据卷执行初始化基线；已有数据卷由启动脚本应用 Sprint 2 增量 SQL，Sprint 3 由后端启动迁移补充。不要通过重新导入基线来升级已有业务库。

## 测试与验收

### 日常自动化检查

```powershell
# 先安装前端依赖；已安装时无需重复
npm.cmd --prefix frontend install

# 后端普通测试、前端测试与构建、Python 无硬件测试
.\scripts\run-tests.ps1

# Maven 依赖已缓存时可离线执行
.\scripts\run-tests.ps1 -Offline
```

统一脚本调用 PATH 中的 `python`；使用 `.venv1` 时先激活该环境或确认解释器。`-Offline` 只切换 Maven 离线模式。普通后端测试中受条件控制的 MySQL/浏览器用例可能跳过，跳过不计为通过；该入口也不执行真实摄像头测试。

### 隔离数据库与真实浏览器

```powershell
# Sprint 1 课程工作流、排课等 MySQL 专项
.\scripts\run-us0102-mysql-tests.ps1

# Sprint 2 MySQL、迁移与覆盖率专项
.\scripts\run-exp3-mysql-tests.ps1

# 浏览器验收前，构建当前前后端
mvn.cmd -f backend/pom.xml -DskipTests package
npm.cmd --prefix frontend run build

# Sprint 2 正式入口：隔离 MySQL/Redis、后端与真实浏览器
.\scripts\run-sprint2-acceptance.ps1
```

隔离脚本需要运行中的 Docker；使用的本地镜像需提前备齐。上述 MySQL 测试使用 `mysql:8.0.36`；Sprint 2 浏览器另需 `redis:7.2-alpine`、Playwright、Chromium/Chrome。可通过 `PLAYWRIGHT_MODULE`、`EXP3_CHROMIUM_PATH` 指定已有安装。

Sprint 2 默认 `Auto` 模式：缺少本机 LibreOffice 时，使用已有后端运行镜像并挂载当前 JAR，不自动拉取该镜像。可用 `-BackendMode Native|Docker` 选择模式，用 `-BackendImage` 指定镜像。`-EvidenceDir` 可指定新建或空的证据目录；默认写入带时间戳的 `docs/runtime/` 子目录。旧入口 `run-exp3-browser-tests.ps1` 转发到同一验收流程。

Sprint 2 专用端口为 `13318`、`16318`、`18081～18083`、`15173`；运行结束清理本轮临时服务。七套脚本覆盖八故事、Office 实际渲染、历史只读、评分、登录/启动竞态及非默认配置。默认 24 小时分支只推进隔离记录的发布时间，另测 0 小时自然发布，不声称真实等待 24 小时。

Sprint 1 浏览器、Sprint 3 业务脚本及其他专项见 [跨模块测试说明](scripts/tests/README.md) 和 [工具目录](scripts/README.md)。全功能页面脚本会写入合成业务数据，应连接测试数据库。

### 已有验证证据

2026-10-16 对 `exp4 / f5a8bcc` 的已有实测记录：

| 检查 | 结果 |
| --- | --- |
| 后端完整回归 | 284 项通过，失败/错误/跳过均为 0；包含单元、边界及隔离 MySQL 集成测试 |
| 前端 | 23 项通过，类型检查与生产构建成功 |
| Sprint 1 真实浏览器 | 三角色、五故事、十个子流程通过 |
| Sprint 2 正式入口 | 七套脚本通过 |
| Sprint 3 真实业务浏览器 | 八组通过 |

不同测试覆盖有重叠，不能相加为功能数量。该表记录已有验证，不表示每次打开仓库都重跑过测试，也不证明当前运行实例已部署同版。

[验收就绪核对](docs/20261016-acceptance-readiness-v1.md) · [功能汇总复核与待完善项](docs/20261016-feature-summary-review-v1.md) · [本轮验证证据](docs/20261016-exp1-exp4-trace-evidence-v1/)

测试使用初始化样例及合成数据，不代表真实教学评价结论。正式人工签收、真实评审/回顾和现场硬件能力按实际活动另行核验。教师页 BOPPPS 与四维评分的展示歧义、固定 100MB 提示与服务端可配置上限的差别仍需复核；详细边界见上述记录。

## 项目目录

| 位置 | 内容 |
| --- | --- |
| `frontend/` | Vue 页面、组件、前端 API、开发适配器及测试 |
| `backend/` | Java 业务源码、配置、Dockerfile 与测试 |
| `vision/` | Python 视觉入口、依赖、无硬件测试与手动诊断 |
| `scripts/` | 启动、构建辅助与验收脚本；`deploy/` 放 Compose/SQL，`tests/` 放跨模块测试 |
| `runtime/` | 本地上传文件、日志、PID 与运行工具；不随 Git 分发 |
| `raw/` | 原始资料，保持原样 |
| `docs/` | 来源、计划、分析、问题与验证过程证据 |
| `output/` | 正式交付物、功能图文总览和使用说明 |
| `memory-bank/` | 已确认背景、长期偏好与继续工作所需信息 |
| `assets/` / `design-system/` | 可复用素材与界面设计规范 |

根目录保留启动/停止的 `.bat`、`.ps1` 入口、初始化 SQL 及项目元文件。本地 `.venv1/` 和 IDE 配置按需保留；不要把它们当作新环境自动具备的依赖。历史目录调整见 [目录整理说明](docs/目录整理说明.md)。

## 常见问题

| 现象 | 检查方式 |
| --- | --- |
| Docker Engine 不可连接 | 先启动 Docker Desktop，确认 Linux 引擎就绪及 `docker info` 成功，再运行启动脚本 |
| 页面打不开或端口被占用 | 检查 `5173`、`8080` 的监听进程，查看 `runtime/logs/` 与 `scripts/compose.ps1 ps` 输出 |
| 接口返回 401/403 | 核对登录状态、账号角色和课程/专业授权；后端受保护接口不能直接匿名访问 |
| 修改代码后仍是旧行为 | 确认运行实例与代码版本；使用 `-Rebuild` 更新后端产物和镜像，前端已运行时必要时重启 |
| Office 预览失败 | 检查文件内容、资源授权、票据有效期和 LibreOffice；损坏 Office 可能上传成功但转换预览失败 |
| 微格视频导入失败 | 检查 ffprobe、视频编码、来源和授权声明；工具不可用与视频内容无效有不同错误提示 |
| 摄像头无法启动 | 检查本机摄像头权限、Python 路径、模型和依赖；摄像头启动依赖 Vite 本地开发适配器，静态前端包本身不提供进程启动接口 |
| GitHub 点击图文总览只显示源码 | 下载 HTML 后用浏览器打开；正文图片已内嵌，可离线阅读 |

## 进一步阅读

- [Sprint 3 使用说明](output/20261012-sprint3-user-guide-v2.md)：模板、视频、分析及行动反馈的具体操作。
- [跨模块测试说明](scripts/tests/README.md)：测试类型、环境变量和执行边界。
- [后端说明](backend/README.md) · [前端说明](frontend/README.md) · [视觉说明](vision/README.md)：各模块细节；旧记录中的历史状态需结合当前实现核对。
- [项目协作规则](AGENTS.md)：原始资料保护、过程文档与正式交付约定。
- [许可证](LICENSE)。
