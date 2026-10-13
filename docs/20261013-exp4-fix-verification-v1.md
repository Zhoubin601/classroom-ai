# exp4 七项问题修复与验证记录

日期：2026-10-13（Asia/Shanghai）。用户授权：“修复这些问题”。当前分支 exp4，HEAD `49ee777`；修复在原有未提交工作区上增量实施，没有提交或推送。

## 修复结果

| 检查问题 | 本次实现 | 验证结果 |
|---|---|---|
| P1 课程切换失败仍可删除旧课片段 | 切换时清除旧列表、资产、片段编辑和来源表单；列表与资产一起成功后才开放操作；失败禁用表单并提供重试；操作前校验当前列表上下文 | 注入503后旧片段/删除按钮不存在，重试恢复当前课程，取消切换保留真正的本地编辑 |
| P1 组件取消信号被覆盖 | 请求控制器连接调用方signal与会话撤销；完成后解绑监听；启动前检查取消，轮询等待可取消；摄像头适配器用启动代次和停止计数阻止旧初始化恢复运行 | 预先取消无启动POST；在途请求取消；适配器班次鉴权暂停后收到停止，旧启动返回409且不创建日志或进程 |
| P2 小数权重拒绝 | 用千分位整数精确合计；校验4维、范围及最多3位小数；失败显示具体原因 | 33.3/33.3/0.1/33.3及其他小数用例通过；非法合计明确提示且不发保存请求 |
| P2 播放响应乱序 | 每次播放递增独立序号、取消前次请求，切换/卸载暂停视频并释放Blob | 先选一再选二，延迟的一不能覆盖二；替换仅保留1个Blob，离开释放至0；既有合成视频可解码并保持区间约束 |
| P2 导出与显示筛选不同 | 保存成功查询的表单及实际学期；未应用/失败筛选禁用导出，下载和分页使用已生效学期；请求期间新编辑仍标为未应用 | 修改未查询、查询503、请求中继续修改均不能导出；恢复到成功快照后导出term一致；最新学期变化不影响旧快照分页 |
| P2 仅打开编辑即提示未保存 | 直接同步写时间文本，编辑快照包含片段ID、版本、原资产与全部输入；清空/放弃恢复完整上下文 | 未编辑切换无提示；真正修改仍提示，取消保留输入，放弃后可新建 |
| P2 年度报表名实不符 | 主任入口改为“最新学期质量报表”；下载前确定最新可见学期，显式传入term并用真实学期命名 | 模拟2027-2028春季，参数为该学期，文件名为teaching-quality-2027-2028春季.csv，没有固定2026年份 |

不增加自然年/学年聚合规则，不调整已确认的课程权限、全量导出及两种雷达刻度。历史“年度”需求若另行扩展，需先明确业务口径；当前七项修复没有新增待确认方向。

## 文件范围

业务源码：

- `frontend/src/views/MicroTeachingView.vue`：课程上下文、加载/失败恢复、完整编辑快照、播放取消与Blob回收。
- `frontend/src/api/index.ts`、新增 `frontend/src/api/cancellation.mjs`：请求取消连接、监听清理、可取消轮询、视频请求信号、报表学期参数。
- `frontend/dev/camera-plugin.mjs`：停止与未完成初始化之间的代次保护。
- `frontend/src/components/Sprint3Workspace.vue`、新增 `frontend/src/ui/weights.mjs`：精确权重、错误提示、导出/分页筛选快照。
- `frontend/src/views/DirectorDeskView.vue`：最新学期报表文案、显式学期和动态文件名。

回归测试：

- 新增 `frontend/tests/exp4-boundaries.test.mjs`，修改 `frontend/tests/camera-plugin.test.mjs`。
- 新增 `scripts/tests/exp4-fixes-browser.cjs`。使用已存在的合成色视频；全部业务API通过本地夹具响应，不连接业务库或启动摄像头。
- 既有 `scripts/tests/ui-interaction-browser.cjs` 未改写；本轮结果另存新证据目录。

过程文件：本记录、`docs/20261013-exp4-fix-plan-v1.md`、`docs/20261013-exp4-fix-evidence-v1/`；来源、判断、复核说明追加到 source-notes.md、analysis-notes.md、questions.md。未覆盖历史检查或旧验收文件，没有生成用户未要求的Office/PDF正式文件。

## 测试证据与限制

| 检查 | 当前结果 | 证据 |
|---|---|---|
| 前端单元测试 | 23通过，0失败/跳过；含新增5项 | frontend-tests-final.log |
| 前端类型检查与构建 | 成功；仍有既有大包提示，主JS约1939.68kB，gzip619.73kB | frontend-build-final.log |
| 七问题定向浏览器验证 | 8组检查覆盖7个问题，页面运行错误0；375px无整页横向溢出 | browser-results.json、browser-tests-final.log |
| 既有UI回归 | 15组通过，页面运行错误0；含4角色、断点、缩放、编辑冲突和身份撤销 | ui-regression/results.json、ui-regression.log |
| 后端离线边界回归 | Sprint3BoundaryTest 16通过，0失败/错误/跳过 | backend-boundary-final.log |
| 独立MySQL/Redis真实业务回归 | **未能执行**。Docker Linux引擎连接不存在，隔离MySQL创建即失败；没有新建容器或进行数据库写入 | business-runner.log；run-business.ps1为隔离runner副本，只改根目录与新证据目录 |

全部测试数字仅指本轮对应项目，不把历史282/284项证据算成本轮通过。模拟浏览器回归不等于真实数据库业务回归。摄像头启动/停止边界由模拟鉴权和请求验证，真人摄像头/GPU识别尚需真实设备复核。

早期 browser-failure.txt/png 为验证脚本使用错误精确文本定位留下的调试记录；修正定位后最终脚本通过，不作为当前功能失败。backend-business容器启动失败是环境限制，未计为通过。本轮使用既有本地工具和ui-ux-pro-max Skill，没有联网补充资料。

## 保护与后续

raw/README.md、stop_project.ps1、memory-bank/project-context.md及原有AttendanceDashboardView.vue的检查前后SHA256一致（protected-files.json）。原有业务数据和其他未提交修改保留。backend业务源码未改写；编译产物和测试日志属于运行产物。

本次Vite测试进程已按PID与专用25175命令行核验后停止；25175及独立业务测试端口13319/16319/18089/15179均无监听。未替换日常后端镜像或进行部署，Git HEAD保持49ee777。

后续需复核：真实设备上的摄像头启动/停止及多人识别。Docker引擎可用后可重新执行本目录的run-business.ps1做真实数据库回归；生成新证据版本，保留本轮环境失败记录。

检查中的全表查询扩量优化、候选视频重复哈希和大范围统计架构整理属于维护建议，本轮未开展这些大范围改造，也未宣称性能容量问题已经解决。

资料来源：原检查记录、当前代码及memory-bank已确认范围；raw/只有README.md，业务原始资料未提供；视频来源为既有 `docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4`，没有自然人人脸或真实课堂视频。

证据目录：[20261013-exp4-fix-evidence-v1](20261013-exp4-fix-evidence-v1/)。
