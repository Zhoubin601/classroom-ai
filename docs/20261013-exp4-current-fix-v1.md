# exp4 当前检查后的修复记录

日期：2026-10-13（UTC）。分支 exp4，基准提交 `5900488`。用户授权：“修复”。针对 `docs/20261013-exp4-current-check-v1.md` 中确认的问题完成修复，并修复真实登录回归中新复现的启动身份探测竞态。

## 修复内容

### 资源版本页

`frontend/src/components/ResourceVersionsPanel.vue` 的列表与历史加载增加请求代次、取消控制器和卸载保护；历史响应必须与当前资源ID一致才能更新页面。加载开始即清空旧历史和操作区，失败不会开放旧资源写操作。

版本切换、上传和预览校验已加载的资源上下文；写操作捕获固定系列、版本和文件，写入期间禁止切换与重复提交。待上传文件受未保存提示保护，取消版本切换保留文件。预览的过时失败也不能关闭后来打开的预览。

搜索接口仅返回当前版本；切换后仍可保留所选择历史ID的历史详情。该选项由成功通过后端鉴权的历史响应补回，不因当前版本变化而清空选择或冒用另一资源。

`frontend/src/api/index.ts` 的 Sprint 3 GET 支持调用方 AbortSignal，沿用已有的会话与组件取消合并机制。

### 原始验收脚本

- `scripts/tests/exp4-browser.cjs` 使用ADMIN“模板管理”入口、article模板卡片、改进行动/资源版本页签及当前成功提示；业务权限、真实视频、导出、版本冲突和共享撤销断言保留。
- `scripts/tests/us02-us03-real-browser.cjs` 操作账号菜单、工作功能页签、班次/排课表单、取消时的未保存提示以及归档确认。保留真实登录、两窗口409、95人名单、AND筛选、越权拒绝和历史归档断言。
- 两套脚本支持本轮证据目录；`CourseBrowserMysqlTest` 的日志同样尊重 `US0203_EVIDENCE_DIR`，不必覆盖历史证据。失败会保存截图。
- 新增 `scripts/tests/resource-versions-browser.cjs`、`login-probe-browser.cjs`；运行方式记录在 `scripts/tests/README.md`。

### 回归中新发现的登录竞态

真实浏览器在启动身份检查未结束时点击登录，旧的未认证 `/auth/me` 响应返回401，原响应拦截器撤销会话并取消在途登录。截图表现为登录页“canceled”和“登录会话已失效”。这属于实际启动竞态，不仅是旧脚本定位失效。

`authApi.getMe` 增加专用于启动身份探测的标记，其401仅让该次探测失败。`App.vue` 捕获会话代次，迟到的旧身份成功响应不能覆盖已经完成的新登录。正式业务401仍执行原会话撤销逻辑，定向测试已验证。

## 验证结果

| 检查 | 最终结果与证据 |
| --- | --- |
| 后端测试 | 284项通过，0失败/错误/跳过；`backend-summary.json`、`backend-xml/` |
| 原始实验二浏览器 | 三角色、五故事、10子流程通过；`legacy-browser-final.log`、`legacy-browser/us0203-real-browser.log` |
| 原始 Sprint 3 浏览器 | 独立MySQL/Redis八组流程通过、页面运行错误0；`business-browser-final.log`、`business-browser-final/browser-results.json` |
| 新资源版本回归 | 7组通过、页面运行错误0；生产preview及dev均验证；`resource-browser-production.log`、`resource-browser-production/results.json` |
| 新登录竞态回归 | 3组通过、页面运行错误0；生产preview及dev均验证；`login-browser-production.log`、`login-browser-production/results.json` |
| 既有七问题回归 | 8组通过、页面运行错误0；`fixes-browser-final2.log` |
| 既有交互回归 | 15组通过、页面运行错误0；`ui-browser-final.log` |
| 前端单元测试 | 23项通过；`frontend-tests-final.log` |
| 前端类型检查、生产构建 | 成功；`frontend-build-final.log` |
| Python无硬件回归 | 9项通过；`vision-tests.log` |
| 代码与脚本检查 | `git diff --check`、四个改动/新增CJS的语法检查通过 |

本轮后端首次全量运行283通过，旧浏览器用例继续暴露新版界面定位问题；修复后该用例单独重跑通过。284为本轮分批运行后最终XML集合的汇总，不将中间失败当成通过或掩盖失败记录。中间尝试日志保留，当前结论以上表最终证据为准。

Sprint 3也验证了切换当前版本后仍保留旧固定资源ID、409乐观锁以及已打开水印预览的共享撤销。新定向测试捕获写请求，核对系列和课程，未仅检查页面文字。

## 范围与交付状态

证据位于 `docs/20261013-exp4-current-fix-v1/`。改动包含前端业务代码、两套旧脚本、新回归脚本、Java测试的证据路径和脚本README；Java业务权限及数据库迁移代码未改动。旧检查报告保留，历史默认浏览器日志恢复原内容。

MySQL/Redis均为本轮创建的临时容器，数据库只有合成账号、课程和文件，没有访问日常业务库或导入人脸资料。测试服务在完成后停止。raw和memory-bank未修改。

当前改动保留在本地exp4工作区，未提交、推送、合并或部署。构建仍有已有的大包提示（主JS约1941.39kB，gzip620.31kB），本次未做拆包优化。真实摄像头/GPU效果仍须现场验证，本轮回归不代替硬件验收。

资料来源：本轮用户授权、`docs/20261013-exp4-current-check-v1.md`、当前代码及上述本轮测试证据；合成视频沿用 `docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4`。引用日期2026-10-13；未补充外部业务资料。
