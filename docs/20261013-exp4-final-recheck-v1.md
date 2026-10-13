# exp4 当前工作区完整复验

日期：2026-10-13（UTC）。验证对象：本聊天的 `/workspace/classroom-ai`，分支exp4，HEAD `5900488657a519821f31d22b5997d6cc4bb1ab62`，包含尚未提交的资源版本、登录竞态和验收脚本修复。

**结论：本轮全部执行项通过，在覆盖范围内未发现新增代码问题或回归。** 当前代码可以完成构建、启动及两套真实业务浏览器验收。此结论针对上述工作区的实际文件，不能直接代替远端5900488或其他聊天快照的状态。

## 本轮结果

| 项目 | 结果 | 本轮证据 |
| --- | --- | --- |
| 前端类型检查及生产构建 | 成功 | frontend-build.log |
| 后端从源码clean package | 成功；构建后另行启用全部测试 | backend-build.log |
| 后端全部测试 | **单次完整运行284通过，0失败/错误/跳过**，包含隔离MySQL及真实浏览器条件用例 | backend-tests.log、backend-summary.json、backend-xml/ |
| 前端单元测试 | 23通过，0失败/跳过 | frontend-tests.log |
| Python无硬件测试 | 9通过 | vision-tests.log |
| 实验二真实业务浏览器 | 三角色、五故事、10子流程通过 | legacy-browser/us0203-real-browser.log |
| Sprint 3真实业务浏览器 | 8组通过，页面运行错误0 | business-browser/browser-results.json |
| 资源版本竞态与写入保护 | 7组通过，页面运行错误0 | resource-browser/results.json |
| 登录竞态与正式401撤销 | 3组通过，页面运行错误0 | login-browser/results.json |
| HTTP200但业务code401的登录/撤销边界 | 同样3组通过，页面运行错误0 | login-business401/results.json |
| 既有七问题回归 | 8组通过，页面运行错误0 | fixes-browser/browser-results.json |
| 既有UI/角色/响应式回归 | 15组通过，页面运行错误0 | ui-browser/results.json |
| 合成旧库升级及重复启动 | 两次通过，资源系列、旧建议与未知权重评价保留且没有重复生成 | upgrade-results.json、upgrade.log |
| 源码一致性 | 313个源码、配置、测试及受保护文件的前后SHA256一致 | source-consistency.json、source-hashes-before.json、source-hashes-after.json |
| 脚本语法及差异检查 | 四个改动/新增CJS语法检查、git diff --check通过 | 本轮执行记录 |

证据统一位于 `docs/20261013-exp4-final-recheck-v1/`，综合结果为 `verification-summary.json`。本轮从clean构建开始，后端测试XML由该次clean后的完整运行生成，没有合并上轮通过记录。生产JAR及前端静态文件指纹为 `built-artifact-hashes.json`。

## 重点复核

- 资源恢复加载的慢响应和失败响应不覆盖后来选中的资源；卸载取消请求，当前历史失败时没有旧写操作。实际捕获版本切换及上传请求，校验新资源系列和课程ID。待上传文件取消切换后保留，历史固定ID在当前版本变化后仍可查询。
- 启动身份探测不取消在途登录，旧身份成功响应不覆盖新登录；正式业务401仍立即撤销认证页面。额外使用临时夹具副本验证HTTP200中的业务code401分支，未修改项目测试脚本。
- 真实业务验证保留课程导入鉴权、两窗口409、95人名单、排课冲突详情、专业AND筛选、越权拒绝、归档冻结、匿名反馈时点、CSV/XLSX导出、视频时长及区间、版本409和共享撤销等断言。
- 旧库连续启动两次后，两个同名旧资源仍是两个独立系列，旧建议完整保留一条，未知权重旧评价保留一条。检查等待实际迁移完成，不仅检查HTTP就绪。

## 范围与限制

前端业务及UI回归主要使用本轮构建的生产包；需要动态导入源码的既有七问题脚本使用Vite dev。全部源码在验证期间保持不变。所有数据库写入都发生在本轮新建临时MySQL/Redis及合成数据中，没有访问日常业务库，没有导入真人视频或人脸资料。

本轮只新增复验记录和证据，没有修改业务代码、项目测试脚本、raw或memory-bank，也没有提交、推送或部署。临时服务及容器在结束后停止。日志中的临时认证值已脱敏。

仍有既有前端大包提示：主JS约1941.39kB，gzip620.31kB。本轮未做生产网络首屏速度或负载容量测试，也未验证Windows一键启动、真人摄像头、多人数识别及GPU现场效果。因此可确认上述软件验证范围可用、没有观察到新增回归，不能保证所有平台、硬件与未覆盖场景绝对零缺陷。

资料来源：用户本轮复验请求、当前工作区源码、既有修复记录 `docs/20261013-exp4-current-fix-v1.md` 和本轮直接执行结果。合成视频沿用 `docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4`，旧库使用项目已有合成DDL生成器。引用日期2026-10-13；未补充外部业务资料。
