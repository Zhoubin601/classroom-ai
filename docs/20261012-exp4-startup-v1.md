# exp4 项目启动记录

日期：2026-10-12（Asia/Shanghai）。用户指令：启动 exp4 的项目内容。

依据：README.md、scripts/start_project.ps1、scripts/deploy/docker-compose.yml、output/20261012-sprint3-user-guide-v2.md。使用当前 exp4 分支及现有未提交工作区，不切换分支、不提交代码。未使用 raw/ 业务资料，未修改 raw/ 文件。

执行 `start_project.ps1 -Rebuild -NonInteractive`，后端 Maven 打包和 Docker 镜像构建成功。前端通过 npm run dev 后台启动；服务保留运行。

检查结果：

- 前端 http://127.0.0.1:5173/ 返回 HTTP 200；Sprint3Workspace.vue 可加载。
- 后端 http://127.0.0.1:8080/api/v1/courses 返回 HTTP 401；未登录鉴权正常。
- 通过前端代理访问 /api/v1/courses 和 /api/v1/sprint3/templates 均返回 HTTP 401，代理连通。
- classroom-mysql、classroom-redis 健康；classroom-backend 运行，Spring Boot 已完成启动。
- 数据库存在 s3_resource_series、s3_template_draft、s3_template_version、s3_video_asset。
- 容器内 ffprobe 和 soffice 存在，服务端视频探测预检通过。

运行日志：runtime/logs/frontend.stdout.log、runtime/logs/frontend.stderr.log；前端 PID：runtime/logs/frontend.pid。后端日志通过 `docker logs classroom-backend` 查看。

范围：仅构建、启动及连通检查，未重新执行完整业务验收，也未启动摄像头。现有业务库没有 ADMIN 账号；未创建账号或修改权限。管理员模板操作需后续显式初始化，方法见既有使用说明。

自动打开浏览器被执行策略拒绝，工具仅返回 blocked by policy，未提供具体原因。用户可手动打开上述前端链接。需用户登录现有账号复核实际页面操作。
