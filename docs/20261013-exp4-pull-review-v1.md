# exp4 最新云端更新本机检查

日期：2026-10-13（Asia/Shanghai）。用户授权：拉取最新 exp4、检查更新；随后确认 Docker 已启动并要求继续验证。

## 结论

已从 `5900488657a519821f31d22b5997d6cc4bb1ab62` 快进到 `20de6a85f6a475d8e67b9fcd2690aa8c3a833a46`，本地 exp4 与本次 fetch 后的 origin/exp4 一致。保留本地未提交修改和既有未跟踪文件，未提交、推送、合并 main 或部署日常服务。

更新的资源历史保护、已有登录专项及主要业务回归均通过，但补充检查确认仍有一处登录竞态。因此不能判定最新版完全没有问题。源码对比表明此前也没有在登录开始时失效启动探测；本次记录为最新版残留问题，不声称已证明由该提交首次引入。

## 更新内容

- ResourceVersionsPanel.vue：列表与历史请求增加取消、代次和卸载保护；加载失败清除旧操作；上传/设当前版捕获固定资源上下文；保留有权限读取的历史 ID；保护待上传文件。
- App.vue、api/index.ts：启动身份探测的 401 不再撤销在途登录；探测结果按会话代次检查；Sprint 3 GET 支持调用方取消信号。
- 两套真实业务浏览器脚本适配当前界面和独立证据目录；Java 浏览器用例同样支持独立证据路径；增加资源版本与登录竞态专项脚本。
- 附带云端检查和复验资料。Java 业务源码、数据库迁移、raw 和 memory-bank 本次提交均无变化。云端资料中的“尚未提交”是当时状态，本机实际已拉取包含这些变更的提交。

## 确认问题

### P2：旧身份成功返回早于新登录完成，页面身份与 Token 不一致

位置：`frontend/src/App.vue:69`，关联 `frontend/src/views/LoginView.vue:623` 和 `frontend/src/api/index.ts:603`。

触发条件：浏览器保留旧账号的有效 Token；打开页面后的 `/auth/me` 探测尚未返回，在暂时可见的登录页提交另一个账号；旧账号探测成功先返回，新账号登录随后成功。

现象：App 将旧用户写入 currentUser，卸载 LoginView。新登录请求仍会成功并把新 Token 写入 localStorage，但卸载组件的 login-success 事件无法更新 App。最终显示旧管理员的姓名/模板管理入口，实际请求 Token 已属于新教师；教师工作入口缺失。

原因：sessionEpoch 只在登录成功或会话失效时增加，登录开始到返回之间仍允许旧探测调用 login。现有专项的成功分支是“新登录已完成后再释放旧身份响应”，没有覆盖相反顺序。

证据：`login-authenticated-dev/results.json` 与 `login-authenticated-production/results.json` 均记录 probeUsedOldToken=true、newTokenStored=true、newTeacherVisible=false、adminNavigationVisible=true；各自有截图。开发与生产构建都复现，页面运行异常为 0。诊断脚本退出 0 表示缺陷复现断言成立，不是业务正确。

建议：在用户发起登录/注册时取消或使启动探测失效，避免其在交互登录期间安装旧用户；再增加“旧身份成功先返回、新登录后返回”的回归，校验姓名、角色入口和 Token 属于同一账号。服务端权限仍按 JWT 校验，本轮没有证明越权成功，不将界面错配描述为后端权限突破。本次仅检查，未修复。

### P3：既有 UI 回归脚本对异步保存后切换即时断言

`scripts/tests/ui-interaction-browser.cjs:37` 点击“保存后离开”后立即读取 aria-selected，本机一次运行得到 false。过程副本仅将此处改成现有 assertEventually 等待 true 后，15组检查全部通过，保存数据断言保留。业务源码和正式测试脚本未改；原失败日志保留。该项属于测试等待不足，不作为已确认的功能回归。

## 本机直接执行结果

| 检查 | 本轮结果 | 证据（均在本轮证据目录） |
| --- | --- | --- |
| 前端单元测试 | 23通过，0失败/跳过 | frontend-tests.log |
| 类型检查及生产构建 | 成功 | frontend-build.log |
| 后端完整 package 和全测试 | 284通过，0失败/错误/跳过；包含真实 MySQL 条件测试及实验二浏览器 | backend-full.log、backend-summary.json、backend-xml/ |
| Python无硬件回归 | 9通过 | vision-tests.log |
| 实验二真实浏览器 | 三角色、五故事、10子流程通过 | legacy-browser/us0203-real-browser.log |
| Sprint 3真实业务浏览器 | 8组通过，页面运行错误0 | business-browser/browser-results.json |
| 新资源版本专项 | 7组通过，页面运行错误0 | resource-browser/results.json |
| 云端新增登录专项 | 3组通过，页面运行错误0 | login-browser/results.json |
| 已有七问题回归 | 8组通过，页面运行错误0 | fixes-browser/browser-results.json |
| 已有 UI 回归 | 原脚本异步断言失败；等待副本15组通过，页面运行错误0 | ui-browser.log、ui-browser-wait/results.json |
| 补充登录时序 | 开发和生产均确认残留缺陷 | login-authenticated-dev/、login-authenticated-production/ |

后端初次在 Docker 不可用时运行206通过、78条件跳过；用户启动 Docker 后的完整运行284全部通过。最终数字来源于完整运行的XML，不合并或借用云端通过证据。

数据库与 Redis 均为本次新建容器，MySQL 8.0.36、Redis 7-alpine；合成课程、账号、PDF及既有纯色视频用于验收。没有访问日常 classroom-mysql 的业务库，没有导入真人资料。真实回归包含两窗口409、95人名单、排课冲突/归档、权限筛选、模板发布、CSV/XLSX导出、视频区间和资源共享撤销。

早期补充诊断的开发版未达到夹具，是诊断脚本误拦截 `/src/api/index.ts`；修正为仅拦截以 `/api/` 开头的请求后，最终开发/生产均复现。旧日志留作过程，不列为额外项目缺陷。

## 限制、保护与交付

本机仍有既有大包提示：主 JS 1941.39kB、gzip 620.31kB，未做生产网络首屏/容量测试。真人摄像头、多人识别和 GPU 现场效果仍需用户实际设备复核。本轮没有重跑旧库两次升级和 Windows 一键启动，不把云端对应结果写成本机通过。

raw 只有 README.md，业务原始资料未提供。来源为用户当前请求、Git提交及项目代码、既有过程文档和本机直接测试；ui-ux-pro-max Skill 用于交互检查，没有联网补充业务资料。

过程文件：本记录、`20261013-exp4-pull-review-plan-v1.md`、`20261013-exp4-pull-review-evidence-v1/`，及 source-notes.md、analysis-notes.md、questions.md 的追加记录。本次未要求 Office/PDF 交付，未新增 output 正式文件。既有输出、未提交修改和 raw 指纹核对结果见 protection-final.json；临时服务和容器清理结果见 cleanup.json。

未完成：上述登录竞态修复、真实硬件验证；后者不作为已执行软件测试的失败。详细修复方向已明确，后续可按本记录复现并修复。

收尾服务状态：本轮专用端口均无监听，两个临时容器已停止。现有 classroom-backend、classroom-redis、classroom-mysql 也处于停止状态；MySQL记录在北京时间16:27:58退出（137，OOMKilled=false），早于本轮隔离数据库全量测试。原因待确认，不能据此认定是本次代码更新或测试造成。本轮只停止记录ID的专用容器和专用端口进程，没有对这些日常容器执行停止/重启。状态证据daily-container-state.json；本次未自动恢复或部署日常项目。
