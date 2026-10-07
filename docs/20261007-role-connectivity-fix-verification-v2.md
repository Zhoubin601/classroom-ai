# 三角色功能权限修复与实际复测记录

日期：2026-10-07。用户授权：“修复这些问题，然后再次进行测试”。依据为用户提供的两张角色图及v1实际失败证据；没有使用外部调研资料，也没有更改raw。

## 修复内容

| 原缺陷 | 实施与实际复测结果 |
|---|---|
| 考勤缺少班次数据范围 | 新增AttendanceAccessService，复用CourseAuthorizationService。主任按本室，教师按主讲/协同，督导按授权专业。start、offering、current、live-update、finish全部纳管。原5项失败复测通过；新增同一督导会话撤权后的读、更新、结束403，current为空。 |
| 冻结班次仍可写 | 开始、更新、结束均先取得班次行锁，再校验isSnapshotFrozen/status。与主任归档锁同一班次；会话写入另取会话锁。原2项失败通过；4个并发启动请求只产生1个活动记录；归档并发可先启动成功或随后409，但归档完成后启动、更新、结束均409，数据库记录保持不变。 |
| 客户端伪造操作人 | start/finish忽略客户端操作人姓名、角色、职称，统一取认证账号。原1项失败通过。主任、主讲、协同、督导均实测启动→更新→结束，并用FORGED身份参数验证保存的真实身份。 |
| 人脸API绕过主任学生管理权限 | SecurityConfig将/api/face/**限定DIRECTOR。原4项失败通过，教师注册403且没有新建学生；督导删除403且目标记录仍存在；主任可正常注册、读取和删除合成档案。 |

关联修复：视觉overview、trend、students/status、reset必须明确班次并通过范围校验；report/stream取得班次锁并校验冻结。Redis概览、趋势、姿态、名单、心跳及清理均按班次隔离，实际交替上报验证本班趋势不混入外班、重置本班不清空外班。

Vite开发服务器本地人脸注册/验证路由会先向真实后端/api/v1/auth/me核验角色。匿名401、教师/督导403，主任空载荷进入正常业务校验400；这些拒绝发生在写文件和启动硬件进程前。允许的Python人脸程序通过进程环境转发登录身份，指定实际后端地址；没有把凭据写入文件或命令行。实际Python请求访问测试底库HTTP200，摄像头与模型识别未据此认定通过。

界面修复：冻结班次的模拟、摄像头启动、结束归档按钮禁用；模拟启动API失败会立即停止，不继续推流。教师选班使用后端返回的关联范围，包含协同班次。Playwright实际选择协同班次和冻结班次，验证按钮状态并截图。

## 最终验证环境与证据

- 当前工作区以Git HEAD `68255ad9a4ebe5b52df9b7a7f18eba32db97cf78`为基础，包含本轮未提交修复。不能把HEAD本身当作修复版本。最终JAR、前端及关键源文件SHA256在`docs/role-connectivity-evidence-20261007-v2/final/build-fingerprint.json`。
- Playwright 1.57.0，真实Chromium 149，实际Vue页面及Vite代理；新构建Spring Boot JAR、独立MySQL 8.0.36、Redis 7.2、LibreOffice真实文档转换。业务接口没有使用模拟响应。
- 修复首轮证据保留在`docs/role-connectivity-evidence-20261007-v2/`根层：发现冻结按钮和全局趋势问题后补修。最终全新测试库及成功回归证据在其`final/`子目录，以此为准。
- 边界脚本额外重跑并等待真实班次列表响应，消除页面加载未完成时将“没有选项”误认成通过的问题。成功证据在`final/confirmed-boundaries/`，仍为同一最终构建。

| 检查 | 最终通过/总数 | 证据（相对final/） |
|---|---:|---|
| 三角色边界与业务联通 | 52/52 | confirmed-boundaries/boundary-results.json、boundary-http.json、boundary.log |
| 建档、排课、名单、模拟、归档历史 | 7/7 | scheduling-results.json、scheduling-http.json |
| 目录、共享资源、人脸底库范围 | 12/12 | scope-results.json、scope-http.json |
| 修复新增检查：正向角色、并发、撤权、视觉、代理、Python | 13/13 | fix-results.json、fix-http.json |
| 原全功能浏览器检查 | 23/23 | all-features/results.json |
| Office、大纲、评价严格检查 | 6/6 | office/fix-acceptance-results.json |
| Sprint2跨角色流程 | 6组通过 | exp3-real-browser.log及sprint2/截图 |

前四类共84组通过。它们含图中范围例外确认；检查组存在业务覆盖重叠，不能把上述数字累加为业务功能完成率。原v1的12项失败全部已有对应通过结果。

普通后端测试：发现163项，实际执行123项全部通过、40项因独立MySQL/浏览器环境条件未启用而跳过；不是163项全部通过。日志`../backend-tests-v2.log`，Maven test/package成功。前端11项全部通过，日志`../frontend-tests-v3.log`；类型检查及Vite构建通过，日志`../frontend-build-v2.log`。另外Python三份改动文件语法检查通过。实际数据库并发及业务验证以上表Playwright运行结果为依据。

实际模拟结果：名单3人，额外1名旁听；实到3、旁听1、本班出勤率100%；结束后MySQL考勤记录保存actualCount=3、attendanceRate=100、status=FINISHED，归档历史累计人数3。截图`41-simulation-three-enrolled-one-auditor.png`及`42-simulation-archive-three.png`。冻结协同班次按钮截图`43-frozen-collaborator-controls.png`。

## 复现

在本机完成Maven package及前端build后，为每轮指定新的过程证据目录，避免覆盖历史证据：

```powershell
./scripts/run-role-connectivity-env.ps1 -Action Start -EvidenceDirectory <新的绝对证据目录>
./scripts/tests/run-role-regression.ps1 -EvidenceDirectory <同一绝对证据目录>
./scripts/run-role-connectivity-env.ps1 -Action Stop -EvidenceDirectory <同一绝对证据目录>
```

运行器配置的是本机已安装Playwright与Chromium路径，换机器需要相应调整。所有写入和发布时间推进只作用于临时数据库；不是修改日常数据库来让测试通过。

## 交付与边界

- 修改了后端考勤、视觉服务、权限配置，前端考勤与本地人脸适配器，Python人脸请求身份传递，并增加对应回归脚本及测试；文件列表可由当前工作区git diff/status复核。
- 正式结论：`output/20261007-爱教学-功能实机测试结论-v2.md`，v1及原始失败证据保留。
- 修复已经写入当前工作区并用修复构建启动后端验证。没有提交或推送代码，没有替换日常8080后端或5173前端。日常MySQL/Redis及用户业务数据保留。临时服务清理结果见`final/cleanup.json`。
- 需业务复核的三个图中已标明例外仍保留：主任CSV遍历全量开课、主任学生列表全量、微格切片只检查登录而无课程范围。本轮没有自动把业务例外改成新的政策。
- 现场摄像头、GPU模型推断、机器人及真实视频流未实测。本轮结论覆盖真实软件链路和软件模拟器，不代表硬件识别已验收。

原始文件名：`20261007-爱教学-角色功能权限矩阵-v1.png`、`20261007-爱教学-角色功能与关联图-v1.png`，位于`D:/2026Autumn Semester File/软管/实验三/output/`，引用日期2026-10-07。
