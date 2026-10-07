# 三角色功能打通检查：过程与复现

## 本轮环境与方法

- 用户于2026-10-07要求检查两张角色图对应功能，并调用实际模拟器或Playwright。
- 当前代码：Git 68255ad，完整提交号见本轮evidence/environment.json。本轮没有修改业务后端或前端界面。
- 真实Chromium 149.0.7827.55，Playwright headless，Vue生产构建预览127.0.0.1:15173。
- 从本工作区重新构建Spring Boot JAR，挂载进已有含LibreOffice的本地Docker镜像，连接一次性MySQL 8.0.36和Redis 7.2。
- 正常后端18081，过期预览票据验证后端18082；二者共享本轮临时上传目录及临时数据库。未连接日常3308业务库。
- 补充测试通过真实浏览器fetch、Vite代理、生产JWT及真实业务数据库，无合成HTTP响应。exp3既有脚本中的route仅转发真实后端，不能称为mock数据测试。
- 后端打包使用-DskipTests，不计为后端单元测试通过。前端类型检查及生产构建通过，有常规包体积告警。

## 执行结果的计数口径

| 测试 | 本轮结果 | 证据 |
|---|---|---|
| 原全功能浏览器 | 23个检查组通过，21张截图；部分检查仅页面展示 | all-features/results.json、all-features.log |
| Sprint2真实跨角色链路 | 6条PASS日志，包含共享、撤销、提交审核、延迟匿名反馈 | sprint2.log、sprint2/ |
| Office和严格验收 | 6个结果组通过 | office-v3/fix-acceptance-results.json |
| 新增角色与范围检查 | 52项：44通过、8失败 | boundary-results.json、boundary-http.json |
| 新增排课和非零考勤模拟 | 7项通过 | scheduling-v2/scheduling-results.json |
| 新增目录、共享下载、人脸权限 | 10项：6通过、4失败 | scope-results.json、scope-http.json |

新增检查合计69项，57通过、12失败。这些是检查组，不能当作69个互不重叠的用户故事，也不据此计算“功能完成率”。图中允许登录者访问微格及全量CSV等例外，复现成功计为例外确认，不代表范围符合业务期望。

## 已实测通过的链路

1. 主任课程导入预览、确认入库及模板鉴权（原全功能），补充新建/删除无关联课程。
2. 主任新建教学班，指定郭军主讲和刘博协同、3名学生名单；主讲、协同、督导可读取对应班次。
3. 真实新增排课，跨教室同教师同学期时段冲突返回409；编辑自身排课成功；名单增删人数一致。
4. 教师草稿保存和正式发布；主任和督导读到相同已发布内容；旧草稿再次发布返回409；主任和督导不能读教师工作草稿。
5. 合成公共目录导入、同版本替换、重复编号拒绝；派生大纲及映射；12类推荐草案、目标/权重CRUD、主任锁定后编辑409。
6. CS牵头主任导入公共目录；参与主任能读目录而导入403，参与关系不使其能读其他教研室课程；实际授课教师与授权督导能读目录。
7. 上传PDF/DOCX/PPTX及资源标签维护；真实LibreOffice转换、浏览器canvas渲染、两页DOCX翻页；超过100MB拒绝，损坏DOCX预览422，预览过期和身份绑定拦截。
8. 同室共享教师发现和预览资源；共享不授予修改或下载权；撤销共享后旧票据及列表立即失效，外室教师不能发现资源。主任和关联教师能下载原件，督导不能编辑/下载。
9. 督导草稿恢复及正式提交；至少3条不同亮点校验；主任驳回→原督导修改重提→主任通过，审核日志可追溯。
10. 审核通过仍有正常24小时发布延迟。仅推进本轮临时数据库中的该评价发布时间，验证教师能收到匿名反馈且无督导身份/审核批注；没有实际等待24小时，也未改变应用延迟配置。
11. 覆盖率明细能追溯评价ID；低分产生RED预警；已发布建议进入教师雷达统计及词云；教师不能请求其他教师雷达或覆盖/预警管理API。
12. 浏览器点击演示模拟流：本班3人、旁听1人、本班出勤率100%；点击结束并归档后实际人数3、出勤率100%、FINISHED持久化并可查询。
13. 开课归档后名单和排课写入409，教师历史累计人次仍为3。此冻结保护成立，但考勤API保护失败，见下节。

## 12项失败检查及根因归组

### P1：考勤API缺少班次数据范围校验（5项）

| 实测请求 | 实际 | 应有边界 |
|---|---|---|
| guojun POST /api/v1/attendance/start，offeringId=5（不关联班次） | 200/200，创建会话 | 403 |
| director GET /api/v1/attendance/offering/5（外室班次） | 200/200 | 403 |
| guojun GET /api/v1/attendance/current | 返回外班会话 | 不应返回外班数据 |
| 已被撤销全部专业授权的测试督导 POST /api/v1/attendance/live-update?sessionId=12&actualCount=1&lookupRate=50 | 200/200，写入成功 | 403 |
| director POST /api/v1/attendance/finish，sessionId=12（外室会话） | 200/200，结束成功 | 403 |

临时ID来自本轮fixture，不是日常库ID。代码定位AttendanceServiceImpl.java:53、134、175、180、186；SecurityConfig.java:129只要求登录。页面选择列表有范围过滤，不能防止直接请求绕过。

### P1：已归档班次仍能写考勤（2项）

- 本轮commonOffering=3经主任归档后isSnapshotFrozen=true。
- POST /api/v1/attendance/start仍返回200/200，未按冻结返回409。
- 该班ACTIVE会话live-update仍返回200/200并更新数据。
- 考勤页面确实隐藏冻结班次；该UI检查通过，不改变后端缺陷结论。

### P1：考勤操作人可被客户端伪造（1项）

- 教师JWT请求start，传operatorRole=DIRECTOR、operatorName=伪造主任，服务端原样保存。
- AttendanceServiceImpl.java:73读取客户端operatorRole；finish也会读客户端身份字段。本轮实测证明start伪造，不把未单独测到的finish伪造作为另一项失败计数。
- 应从服务端当前登录身份绑定操作人。

### P1：人脸底库绕过主任专属学生管理权限（4项）

- guojun GET /api/face/all：200/200；应按底库管理范围403。
- supervisor GET /api/face/all：200/200；同上。
- guojun POST /api/face/register：使用本轮合成512维向量，200/200；随后主任/api/student/list确实出现新合成学生，证明写入了学生底库，非仅空接口放行。
- supervisor DELETE /api/face/{合成测试学号}：200/200，删除成功。
- /api/student/list对教师、督导返回403，但/api/face/**默认仅登录，从FaceServiceImpl.java:42/68可创建或更新Student。
- 只使用合成向量；不将其当作真实摄像头识别成功，不在证据中保存特征向量或认证令牌。

## 图中已标注例外的实测确认

- 主任质量CSV确实包含本教研室外课程；教师和督导导出403。
- 主任学生列表读取全量80条基线记录，代码未按教研室过滤；教师和督导该列表403。此结论与人脸接口绕过是两个问题。
- 教师、主任可对其他课程微格元数据挂载、读取、删除；另建无专业授权督导，普通课程详情403，但同课程微格挂载/读取/删除仍200。
- 微格验证仅元数据接口，不代表真实视频生成、上传、播放和边缘智能体已联通。
- 这些属于图中已标明的范围例外；是否允许由业务复核，本轮不自动扩权或收权。

## 测试自身的修正

- 原Office严格脚本仍定位旧按钮“一键套用12项国标推荐模板”和旧提示，导致office.log、office-v2.log超时。
- 只把定位改为当前“套用推荐示例模板（12类）”及推荐示例草案提示，office-v3全部通过；不是修改业务代码使测试通过。
- 新排课脚本第一次使用错误导航名称，API排课和冲突已成功，截图步骤超时。修正导航后scheduling-v2七项全部通过，旧证据保留。
- 新增脚本保存每项结果和脱敏HTTP摘要，发现FAIL后返回非零；最初记录日志时脚本尚未加最终非零退出逻辑，不能用初次进程退出码替代JSON中的失败结果。

## 尚未验证

- 摄像头真实采集、GPU模型推理、人脸现场识别、机器人连接及实时视频流。
- 日常业务数据全量闭环；本轮验证当前代码和隔离数据，未向日常库写测试数据。
- 各功能所有并发和极端输入组合、完整资源历史回退与教师改进回执；图中本来也未将后两项列为已完成操作。

## 重跑与交付检查

先构建backend JAR和frontend/dist，执行scripts/run-role-connectivity-env.ps1 -Action Start -EvidenceDirectory 新的绝对证据目录；设置ROLE_EVIDENCE_DIR为该目录，设置PLAYWRIGHT_MODULE、EXP3_CHROMIUM_PATH及相应脚本环境变量，按Sprint2→Office→all-features→role-connectivity→role-scheduling→role-scope顺序运行。最后在finally中执行-Action Stop -EvidenceDirectory 同一目录。已有证据目录的Start会拒绝覆盖。

旧脚本变量须从environment.json取EXP3_FRONTEND_URL、EXP3_BACKEND_URL、EXP3_EXPIRED_BACKEND_URL、EXP3_TEST_MYSQL_CONTAINER；指定EXP3_EVIDENCE_DIR及FIX_EXP3_EVIDENCE_DIR为新目录下子目录。all-features设置BASE_URL、BACKEND_URL、FORWARD_BACKEND=false、ALL_FEATURES_EVIDENCE_DIR。新脚本读取ROLE_EVIDENCE_DIR中的environment.json。

本轮不修复业务权限，不提交/推送，不改写raw/；测试数据留在一次性容器中，结束清理容器和服务。最终用户可复核结论存output/20261007-爱教学-功能实机测试结论-v1.md，过程、失败日志及截图存docs/role-connectivity-evidence-20261007-v1/。
