# 工具目录

所有命令从项目根目录执行即可，脚本按自身位置查找项目。

| 命令 | 用途 |
| --- | --- |
| `./start_project.ps1` | 根目录唯一入口，启动全部组件 |
| `./scripts/start_frontend.ps1` | 启动前端 |
| `./scripts/start_services.ps1` | 启动 MySQL/Redis |
| `./scripts/run_monitor.bat` | 启动视觉监控 |
| `./scripts/run_register.bat` | 启动注册工具，后面可接 Python 参数 |
| `./scripts/register_face.bat` | 交互式输入学生信息后注册 |
| `./scripts/compose.ps1 ps` | 查询 Compose 服务状态 |
| `./scripts/compose.ps1 up -d` | 启动 Compose 服务 |
| `./scripts/compose.ps1 logs -f classroom-backend` | 查看容器后端日志 |
| `./scripts/run-tests.ps1 -Offline` | 自动测试与前端构建 |

`deploy/` 保存 Docker Compose、`.env.example` 和 MySQL 文件；Compose 的本地 `.env` 应放在 `deploy/`，不会自动生成。
`tests/` 是跨服务联调，模块内的单元测试不在这里。旧人脸调试脚本会写入固定学号，未纳入自动运行。

目录迁移解释见 [目录整理说明](../docs/目录整理说明.md)。
