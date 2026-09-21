# 跨模块联调测试（scripts/tests/）

- `test_http_smoke.py`：需要已启动的 Java 后端；创建独立合成记录并清理。
- `browser_smoke.cjs`：需要前后端、Playwright 和浏览器；检查页面与摄像头接口参数校验。
- `test_backend_api.py`：保留的旧手动调试脚本，会读取已有特征库并写入固定测试学号，**不纳入自动回归**。

各模块自己的测试分别放在：

- `frontend/tests/`：Node 单元测试。
- `backend/src/test/`：Java 测试。
- `vision/tests/`：Python 无硬件测试。
- `vision/diagnostics/`：手动硬件实验。

统一自动化入口为根目录下的 `scripts/run-tests.ps1`。

## US-01 / US-02 回归

- 普通回归：`scripts/run-tests.ps1 -Offline`。包括权限、导入边界、草稿身份与版本校验；默认跳过需要隔离数据库的测试类。
- 真实 MySQL：`scripts/run-us0102-mysql-tests.ps1`。需要已运行 Docker、本地 `mysql:8.0.36` 镜像和空闲端口 13316；创建并自动清理临时容器，验证并发、事务回滚及历史迁移。
- 浏览器：先在 frontend 执行 `npm run build` 和 `npm run preview -- --host 127.0.0.1 --port 15173 --strictPort`，再执行 `node scripts/tests/us01-us02-browser.cjs`。
  需要 Playwright，可通过环境变量 `PLAYWRIGHT_MODULE` 指定已有模块路径、`US0102_CHROMIUM_PATH` 指定已有 Chromium 可执行文件。浏览器用例使用合成 API 响应检查真实 Vue 页面交互及请求字段，不冒充浏览器到 MySQL 的整链验收。


## US-02 / US-03 事务与真实浏览器验收

- `scripts/run-us0102-mysql-tests.ps1` 默认运行 CourseWorkflowMysqlTest、CourseSchedulingMysqlTest，共 31 个真实 MySQL 用例；包含并发排课、教师/学期变更、完整冲突 HTTP 409、锁持有到外层事务提交、历史人数冻结与回滚、AND 检索和权限、归档迁移重复执行及旧数据保留。
- 真浏览器：先构建并启动 15173 端口前端 preview，配置 `PLAYWRIGHT_MODULE`、`US0102_CHROMIUM_PATH` 后设置 `$env:US0203_BROWSER='true'`，运行 `./scripts/run-us0102-mysql-tests.ps1 -Tests 'CourseBrowserMysqlTest,CourseSchedulingMysqlTest,CourseWorkflowMysqlTest'`。完成后移除 `US0203_BROWSER` 环境变量。
- CourseBrowserMysqlTest 在随机端口启动真实 Spring HTTP 服务，调用 `us02-us03-real-browser.cjs`。现在使用真实登录页面和生产 JWT 链，不再注入身份。三角色覆盖五条故事及两窗口并发，合成名单 95 人，包含导入模板鉴权、归档历史、专业 AND 筛选及直接详情越权；额外执行 20 次本地顺序查询记录耗时。字典测试控制器读取真实库，大纲指标仅提供空测试列表，其他模块不计入验收。
- 证据：`docs/us0203-real-browser.log`、`docs/us0203-schedule-sample.png`、`docs/us0203-mysql-final.log`。专用容器绑定 127.0.0.1:13316，退出自动清理，不连接业务库。
