# 跨模块联调测试（scripts/tests/）

## 当前 exp4 的 Sprint 2 完整验收

先构建当前前后端，再运行 `scripts/run-exp3-browser-tests.ps1`（兼容入口）或 `scripts/run-sprint2-acceptance.ps1`。新增 `-EvidenceDir`、`-BackendMode Auto|Native|Docker`、`-BackendImage` 参数；证据目录必须新建或为空。仅使用脚本创建的 MySQL/Redis；各后端按顺序启动，统一 Asia/Shanghai，结束后清理测试进程/容器和恢复环境。

- `exp3-real-browser.cjs`、`fix-exp3-acceptance.cjs`、`exp3-history-scores.cjs` 已适配当前页签、确认提示、已发布模板和 expectedVersion 契约，保留原业务断言。
- `login-start-browser.cjs`：有效旧身份探测先/后返回、HTTP/业务401及登录失败；检查页面身份、角色入口与存储Token一致。
- `exp3-startup-history-browser.cjs`：真实后端仅延迟启动排课响应，切到另一班次并选择历史后，迟到启动链路不得改回当前版。
- `exp3-configurable-browser.cjs`：PDF-only/1024字节规则、未标注筛选、小数模板权重、驳回/修改/审核日志和自然零延迟匿名反馈。
- `sanitize-test-evidence.py`：脱敏测试日志中的临时JWT及CSRF值，不改变业务数据。

上传配置与零小时反馈使用独立18083实例；24小时默认反馈使用18081实例，并只推进该次临时库中特定记录的发布时间验证到期分支。它不表示等待了24小时，也不代替真实摄像头、多人识别或GPU现场验收。

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

## 大纲历史与评分必填专项

`exp3-history-scores.cjs` 使用真实登录、API和页面，验证历史矩阵快照、教师/主任的版本切换和只读状态、权限隔离，以及正式评价缺少评分拒绝、明确0分允许、不完整草稿允许。它已接入 `scripts/run-exp3-browser-tests.ps1`，沿用该入口的隔离服务和清理流程；单独运行时须配置 `EXP3_FRONTEND_URL`、`EXP3_BACKEND_URL`、Playwright/Chromium路径，可用 `EXP3_FIX_EVIDENCE_DIR` 指定新证据目录。

## exp4 交互与登录竞态回归

- `resource-versions-browser.cjs` 使用本地 API 夹具验证记忆资源恢复、慢/失败响应、卸载取消、写入上下文、历史ID保留和待上传文件保护，不连接业务库。
- `login-probe-browser.cjs` 使用本地 API 夹具验证启动身份探测不取消在途登录、不覆盖新身份，正式业务401仍撤销页面。
- 两个脚本均支持 `UI_URL`、`PLAYWRIGHT_MODULE`、`EXP3_CHROMIUM_PATH`；证据目录分别由 `RESOURCE_VERSIONS_EVIDENCE`、`LOGIN_PROBE_EVIDENCE` 指定，可用 Vite dev 或生产 preview 验证。
- `exp4-browser.cjs` 的真实业务验收使用当前角色入口、功能页签与模板卡片。可设置 `EXP4_EVIDENCE_DIR` 为新证据目录、`EXP4_VIDEO_FIXTURE` 为合成视频路径；默认视频取已有两秒合成夹具。
- `us02-us03-real-browser.cjs` 及 `CourseBrowserMysqlTest` 支持 `US0203_EVIDENCE_DIR`，将日志、截图和查询计时写入本轮目录；默认仍使用 `docs/`。该脚本实际操作账号菜单、班次/排课表单、未保存提示和归档确认，不跳过业务断言。
