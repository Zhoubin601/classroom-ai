# Classroom AI Demo

项目已按「前端 / 后端 / Python 视觉 / 工具 / 数据 / 文档」整理。根目录从 **33 项减少到 16 项**（含 `.idea`、`.venv1`）。

## 现在去哪找文件

| 位置 | 用途 |
| --- | --- |
| `frontend/` | Vue 页面、组件、前端 API 与测试 |
| `backend/` | Java 业务接口、配置与测试 |
| `vision/` | Python 视觉代码、模型、依赖、测试和硬件诊断 |
| `scripts/` | 启动、测试工具；`deploy/` 放部署配置，`tests/` 放跨服务测试 |
| `runtime/` | `uploads/` 保存共享图片，`logs/` 保存日志和 PID |
| `docs/` | 过程记录、目录整理说明；`archive/` 保存历史备份 |
| `raw/` | 原始资料，保持原样 |
| `assets/` | 可复用素材 |
| `memory-bank/` | 长期背景与当前目录约定 |
| `output/` | 正式交付物 |

根目录的 `.venv1/` 是现有 Python 环境，`.idea/` 是 IDE 配置，两者保持原位。其余根目录文件只有 `.gitignore`、`AGENTS.md`、本说明和 `start_project.ps1`。

**详细迁移清单、整理理由及后续存放规则：[目录整理说明](docs/目录整理说明.md)。**

## 启动与停止

### 推荐方式（Windows 用户双击启动）
- **启动服务**：直接双击根目录的 `start_project.bat`（会自动检查 Docker、启动 MySQL/Redis、启动后端与前端并打开浏览器，窗口保留进度与提示）。
- **停止服务**：双击根目录的 `stop_project.bat`（一键停止运行中的前后端后台进程）。

### 命令行（PowerShell）执行

```powershell
# 一键构建当前后端 jar 和 Docker 镜像，再启动全部服务
.\start_project.ps1

# 显式要求重新构建（同时指定 SkipBuild 时以 Rebuild 为准）
.\start_project.ps1 -Rebuild

# 复用已经构建好的当前版本 jar 和 Docker 镜像
.\start_project.ps1 -SkipBuild

# 停止前后端服务
.\stop_project.ps1

# 单独启动
.\scripts\start_frontend.ps1
.\scripts\start_services.ps1
.\scripts\run_monitor.bat
.\scripts\run_register.bat
.\scripts\register_face.bat

# Docker 运维：配置已移到 scripts/deploy/
.\scripts\compose.ps1 ps
.\scripts\compose.ps1 up -d
.\scripts\compose.ps1 logs -f classroom-backend

# 统一自动化检查
.\scripts\run-tests.ps1 -Offline
```

前端：`http://127.0.0.1:5173`；后端：`http://127.0.0.1:8080`；视觉流端口：`8088`。

## 实验二与 Sprint 2 功能

当前代码包含实验二 US-01～04、US-06，以及实验三 Sprint 2 的 US-05、US-07～10、US-13～15：课程导入、草稿发布、开课排课及历史归档、专业权限检索、培养方案目录与大纲映射、课件标签和共享、水印授权预览、督导提交与主任审核、延迟匿名反馈、学期覆盖率追溯。

### 大纲版本与评价评分

- 教师工作台、主任的“毕业要求指标点矩阵”均提供“查看大纲版本”下拉框，默认显示当前版。切换历史版可查看该版指标、支撑权重和课程目标；历史版只读，不能新增、修改、删除指标或锁定。切回当前未锁定版本后可继续维护。
- 正式提交督导评价必须填写教学态度、内容、方法、效果四项评分，**每项均允许 0 分**。缺少任一评分会提示补齐；草稿仍允许不完整。各项上限按配置权重执行。
- 指标查询接口 `GET /api/v1/syllabus/course/{courseId}/indicators` 支持可选 `syllabusId` 参数；省略时仍返回最新映射。读取历史版继续校验课程权限及大纲所属课程。

正常启动通过 Docker 提供 LibreOffice，支持 DOC/DOCX/PPT/PPTX 转水印 PDF。直接在 Windows 上运行 jar 时，需要本机安装 LibreOffice 或配置 `CLASSROOM_SOFFICE`。全新 MySQL 只执行完整基线；已有数据卷只应用可重复执行的 Sprint 2 迁移，不重导入基线。

### 真实浏览器验收

先构建后端和前端（`cd frontend; npm run build`），再执行以下命令。Playwright 可通过 `PLAYWRIGHT_MODULE` 指定，浏览器通过 `EXP3_CHROMIUM_PATH` 指定；未指定时 runner 会尝试发现本机可用安装。

```powershell
.\scripts\run-tests.ps1 -Offline
.\scripts\run-exp3-mysql-tests.ps1
.\scripts\run-exp3-browser-tests.ps1

# 实验二：一次性 MySQL + 三角色五故事真实浏览器
$env:US0203_BROWSER = 'true'
$env:US0102_PREVIEW_URL = 'http://127.0.0.1:5173'
.\scripts\run-us0102-mysql-tests.ps1 -Tests 'CourseWorkflowMysqlTest,CourseSchedulingMysqlTest,CourseBrowserMysqlTest'
```

Sprint 2 浏览器 runner 使用独立 MySQL/Redis、两个后端进程及 15173 端口的前端预览，真实代理上传，结束自动清理。除八故事流程外，还检查两页 DOCX/PPTX 实际渲染、12项推荐草案、PDF保护及草稿恢复。匿名反馈仍使用正常 24 小时规则，测试仅推进自身临时容器中的发布时间来验证发布后的页面与身份脱敏。

runner 还执行 `scripts/tests/exp3-history-scores.cjs`：验证教师/主任切换历史矩阵及只读状态、历史查询权限隔离、正式评价缺失评分拒绝、明确四个 0 分提交成功，以及不完整草稿仍可保存。

2026-10-14 起，原 `run-exp3-browser-tests.ps1` 入口转发到 `run-sprint2-acceptance.ps1`。完整验收还包括登录启动竞态、真实后端的慢启动历史切换、PDF-only/1024 字节上传限制、小数模板权重、审核修改日志和零延迟匿名反馈。默认使用新时间戳证据目录；可以通过 `-EvidenceDir` 指定新目录，非空目录会被拒绝以保护旧证据。服务与测试库统一采用 Asia/Shanghai。

本机缺少 LibreOffice 时自动使用已有 Docker 运行镜像，并挂载当前构建的 JAR；运行前仍需构建当前前后端。可显式选择 `-BackendMode Native` 或 `-BackendMode Docker`，通过 `-BackendImage` 指定本机含 LibreOffice/ffprobe 的运行镜像，脚本不会自动拉取或替换日常服务。专用端口为 13318、16318、18081～18083、15173。结束后恢复进程环境、清理测试服务并脱敏日志。

2026-10-11 云端验收：后端去重实际通过 204 项，另 33 项专用测试未补跑；前端 13 项与构建通过，八故事、Office 预览及上述修复专项浏览器通过。完整范围、证据和运行边界见 [两处缺口修复记录](docs/20261011-exp3-two-bugs-fix-v1.md)。

全功能页面验收可运行 `scripts/run-all-features-playwright.ps1`。若使用独立后端，启动前端时设定 `CLASSROOM_API_PROXY`，同时设置测试的 `BASE_URL`、`BACKEND_URL`、`FORWARD_BACKEND=false`，即可直接走真实 Vite 代理。设置 `OFFICE_PREVIEW_FIXTURE` 为测试 DOCX/PPTX 路径时，会额外验证 Office 上传与水印预览；使用测试数据库，不向正式数据导入测试内容。

本项目使用明确标注的合成培养方案示例验证指标机制，不要求补交真实培养方案。团队评审、回顾及正式交付批准记录按实际活动核对。验收与集成记录见 [实验三 main 集成记录](docs/20261006-exp3-main-integration-v1.md)。

Python 独立入口：

```powershell
.\.venv1\Scripts\python.exe -m vision --help
.\.venv1\Scripts\python.exe -m vision monitor --port 8088
# 新环境安装核心依赖；不需要在已配置环境中重复安装
.\.venv1\Scripts\python.exe -m pip install -r vision/requirements.txt
```

## 配置与注意事项

- Docker 配置和环境样例都在 `scripts/deploy/`，如需本地 Compose 环境文件，在该目录创建 `.env`。数据库卷名称保持原值。
- Java 上传与图片访问、Python 保存、前端图片读取统一使用 `runtime/uploads/`；浏览器的 `/uploads/...` 地址保持有效。
- 现有 `.venv1/` 未移动或重装。可选 YOLO/MediaPipe 实验依赖见 `vision/requirements-analysis.txt`。
- 新运行日志写入 `runtime/logs/`，原根目录日志在 `runtime/logs/history/`；旧 `docs/runtime/` 只保留此前运行记录。
- `docs/archive/` 是历史存档，不是可运行项目副本；里面的旧入口和旧目录说明不再适用。
- 先前已运行的服务需要重启，才会使用新路径；整理操作没有中止用户进程。
- 摄像头启动接口沿用 Vite 本地开发适配器，静态生产包本身不提供这些进程启动接口。
