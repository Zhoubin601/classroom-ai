# 实验一需求在 exp4 中的实现路径、测试与图文汇总

2026-10-16 · Asia/Shanghai · exp4 / `f5a8bccf6fe19155263bef87599419a1f5658020`

本报告将实验一正式项目规划中的21条用户故事，逐项对应到exp4的页面操作、源码、接口、数据变化与本轮隔离实测。四条路径用于组织讲解，21条故事逐项保留原编号。

范围仅含US-01～US-21；US-05归Sprint 2，但在课程底座路径讲解。摄像头、人脸考勤及现场设备性能不在本报告范围。原件表5/6/7为需求基线，当前实现不自动增加验收要求。

本轮前端23项通过并完成生产构建；后端重新打包。完整隔离数据库回归284项通过，失败/错误/跳过均为0；Sprint 2正式入口7套通过；Sprint 1真实浏览器覆盖3角色、5故事、10个子流程；Sprint 3真实业务8组通过。不同检查存在覆盖重叠，不能相加为功能数。

测试使用本地初始化样例、合成指标、合成名单及纯合成视频。默认24小时分支先验证审核至发布时间相差86400秒，再推进隔离记录发布时间；另测0小时自然发布。未真实等待24小时。自然人正式批准、真实会议、学校正式培养方案资料未提供。


## 功能全景
![21条需求的业务全景](20261016-exp1-exp4-feature-trace-v1-images/overview.svg)

|需求|功能|角色|业务路径|本轮结论|
|---|---|---|---|---|
|US-01|课程建档与模板导入|主任|课程底座|核心软件本轮通过|
|US-02|课程内容草稿与发布|教师|课程底座|核心软件本轮通过|
|US-03|多教师开课与排课统筹|主任|课程底座|核心软件本轮通过|
|US-04|历史开课与累计人次|教师|课程底座|核心软件本轮通过|
|US-05|大纲与毕业要求指标关联|主任、教师|课程底座|核心软件本轮通过|
|US-06|督导授权范围内组合检索|督导|课程底座|核心软件本轮通过|
|US-07|按章节上传教学材料|教师|教学资源|核心软件本轮通过|
|US-08|教学环节多选标签|教师|教学资源|核心软件本轮通过|
|US-09|限时授权水印预览|督导|教学资源|核心软件本轮通过|
|US-10|教研室共享资源检索|教师|教学资源|核心软件本轮通过|
|US-11|资源版本与当前版控制|主任、教师|教学资源|核心软件本轮通过|
|US-12|授权微格片段与元数据回看|管理员；主任、教师按授权课程操作|教学资源|核心软件本轮通过|
|US-13|结构化听课评价|督导|评价与改进|核心软件本轮通过|
|US-14|审核、延迟与匿名反馈|主任、督导、教师|评价与改进|核心软件本轮通过|
|US-15|学期课程覆盖统计|督导|统计与分析|核心软件本轮通过|
|US-16|低覆盖与低评价预警|主任|统计与分析|核心软件本轮通过|
|US-17|本人评价汇总与雷达图|教师|统计与分析|核心软件本轮通过|
|US-18|教学质量报表导出|主任|统计与分析|核心软件本轮通过|
|US-19|按课程类别分析问题分布|督导|统计与分析|核心软件本轮通过|
|US-20|评教模板与权重版本|管理员|评价与改进|核心软件本轮通过|
|US-21|建议到教学行动闭环|督导、教师|评价与改进|核心软件本轮通过|



## 一条完整路径：评价到教学改进

核心样例为同一SYN-CORE评价。模板创建、分析展示、行动保存走真实浏览器；评价和独立建议初始输入通过真实API构造。补充截图另外覆盖评价表单与主任审核，属于另一明确标记的合成记录。

|角色/阶段|页面操作|实现链路（省略共同前缀/api/v1）|数据变化|
|---|---|---|---|
|管理员|模板管理 → 模板草稿 → 保存并发布25/25/25/25|Sprint3Workspace.publish → POST /admin/templates/{id}/publish → EvaluationTemplateService.publish|s3_template_version新增不可变版本；模板ID 2|
|督导|对SYN-CORE班次暂存评价，写入独立建议，再正式提交|POST /supervisions → AdviceService.receive → submitEvaluation|核心样例评价ID 1；模板及weight_snapshot固定；状态PENDING_REVIEW|
|主任|教研室主任工作台 → 评价审核 → 通过|POST /supervisions/{id}/review → SupervisionServiceImpl.reviewEvaluation|APPROVED_PENDING；reviewedAt、publishTime与审核日志保存|
|教师不可见阶段|教师请求评价详情并查询雷达|GET /supervisions/{id} → 403；GET /sprint3/radar → 尚未纳入|督导身份与尚未到期内容没有提前反馈给教师|
|隔离时钟推进|仅更新本次测试评价的publish_time至过去|核对默认86400秒后，通过隔离SQL推进；0h配置另行自然发布|不更改系统时钟；未真实等待24小时|
|教师|统计与反馈 → 评价分析 → 改进行动 → 保存行动反馈|GET /sprint3/suggestions/mine → PUT /suggestions/{id}/my-feedback → AdviceService.feedback|建议Practice in groups；行动及Synthetic reflection collected落库，修订历史保留|

![补充样例：督导填写评价](20261016-exp1-exp4-feature-trace-v1-images/shot-13-annotated.svg)
![补充样例：教师匿名反馈](20261016-exp1-exp4-feature-trace-v1-images/shot-14-annotated.svg)
![核心样例：教师行动反馈](20261016-exp1-exp4-feature-trace-v1-images/shot-21-annotated.svg)


## 路径1：课程底座

主任 → 教师 → 主任 → 督导 → 教师

![课程底座操作次序与角色切换](20261016-exp1-exp4-feature-trace-v1-images/route-1.svg)


### US-01 课程建档与模板导入

角色：主任。原件表5第2行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.1:课程档案简介作为教研室主任，我希望录入或按模板批量导入课程档案，以便建立可追溯的课程底座。

**验收标准：** 编码唯一；导入前预览；缺项、重复与引用冲突返回错误清单；确认后写入并记录操作者。

**页面操作：**

1. 教研室主任工作台 → 课程档案
2. 单条建档：新增课程，填写唯一编码、专业、学分学时并保存
3. 批量建档：批量导入课程 → 下载模板 → 选择CSV
4. 查看预检成功数和逐行错误；修正后重新预检
5. 确认导入并整批入库 → 检查课程列表

**前置及输入：** 有效CSV编码BROWSER-IMPORT，专业SE，学分3，学时48；错误示例学分-1。使用该角色授权数据，各套测试夹具独立。

![US-01关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-01-annotated.svg)

**实现链路：**

![US-01六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-01.svg)

**HTTP接口：**

- `POST /api/v1/courses`
- `GET /api/v1/courses/import/template`
- `POST /api/v1/courses/import/preview`
- `POST /api/v1/courses/import/confirm`
- `GET /api/v1/courses`
- `DELETE /api/v1/courses/{id}（已有入口，非原件导入验收新增要求）`

**存储：** CourseRepository / CourseImportLogRepository → t_course、t_course_import_log

**数据变化：** 预检生成临时batchId，不立即建课；确认后课程及导入日志入库。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/DirectorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/DirectorDeskView.vue:1210>)|handleConfirmImport|确认前校验批次可提交，成功后刷新课程|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:661>)|courseImportApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseImportController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseImportController.java:56>)|confirmImport|限制主任并将批次交给业务层|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseImportServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseImportServiceImpl.java:420>)|confirmImport|整批校验、操作者绑定和一次消费，事务写入|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseImportServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseImportServiceImpl.java:194>)|previewImport|预检分支：逐行错误、引用冲突与批次生成|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseServiceImpl.java:46>)|saveCourse|单条录入路径：编码和专业/教研室边界|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/Course.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/Course.java:18>)|t_course|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseImportLog.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseImportLog.java:17>)|t_course_import_log|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-01-N|正常路径|有效CSV编码BROWSER-IMPORT，专业SE，学分3，学时48；错误示例学分-1|依次执行本项页面步骤；核对结果并回查数据。|有效CSV预览后确认，列表出现新课程并有操作者记录|本次端到端及关联自动化通过|
|TC-01-B1|异常/边界|缺项、负学分、批内重复与引用冲突 → 错误清单且不入库|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|缺项、负学分、批内重复与引用冲突 → 错误清单且不入库|本次关联自动化通过|
|TC-01-B2|权限/状态|其他操作者或过期批次确认 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|其他操作者或过期批次确认 → 拒绝|本次关联自动化通过|


- 本次通过：[CourseImportTest#testConfirmImport_Success](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseImportTest.java:129>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseImportTest.xml
- 本次通过：[CourseImportServiceTest#testPreviewImport_VariousValidationFailures](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/service/CourseImportServiceTest.java:209>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.service.CourseImportServiceTest.xml
- 本次通过：[CourseImportServiceTest#testConfirmImport_ForbiddenOperator](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/service/CourseImportServiceTest.java:337>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.service.CourseImportServiceTest.xml
- 本次通过：[CourseImportServiceTest#testConfirmImport_DuplicateConsumption](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/service/CourseImportServiceTest.java:329>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.service.CourseImportServiceTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us01-import-error.png

**讲解要点：**

- 预检成功不等于已入库
- 编码唯一，错误批次不能确认；不能重复消费批次
- 允许手工录入与CSV两条入口，不把质量报表当课程导入模板


### US-02 课程内容草稿与发布

角色：教师。原件表5第3行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.1:课程档案简介作为任课教师，我希望编辑并发布课程简介、考核方式和教学目标，以便让师生理解课程要求。

**验收标准：** 草稿与发布状态分开；必填项完整；发布人和更新时间可追溯。

**页面操作：**

1. 任课教师工作台 → 当前主讲课程 → 课程资料
2. 填写课程简介、考核方式和教学目标
3. 暂存草稿，核对草稿版本
4. 正式发布 → 查看已发布版本和时间
5. 再次编辑草稿，用另一读者验证仍显示旧正式版

**前置及输入：** 课程简介、合成考核、合成目标；发布v1后再次保存草稿。使用该角色授权数据，各套测试夹具独立。

![US-02关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-02-annotated.svg)

**实现链路：**

![US-02六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-02.svg)

**HTTP接口：**

- `GET/POST /api/v1/courses/{courseId}/content/draft`
- `POST /api/v1/courses/{courseId}/content/publish`
- `GET /api/v1/courses/{courseId}/content/published`

**存储：** CourseContentRevisionRepository → t_course_content_revision；CourseRepository锁定课程

**数据变化：** DRAFT → 保存；正式发布生成PUBLISHED历史版并创建后续草稿；发布人和时间可追溯。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/TeacherDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/TeacherDeskView.vue:1333>)|handleSaveDraft|前端保存携带草稿身份及版本|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:641>)|courseContentApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java:60>)|publishContent|当前实现直接在控制器内进行事务发布与版本检查|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java:103>)|checkedDraft|比较draftId、lockVersion和发布基线，避免旧页面覆盖|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java:46>)|saveDraft|不完整草稿允许保存|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseContentController.java:92>)|getPublishedContent|读者只取正式版|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseContentRevision.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseContentRevision.java:18>)|t_course_content_revision|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-02-N|正常路径|课程简介、合成考核、合成目标；发布v1后再次保存草稿|依次执行本项页面步骤；核对结果并回查数据。|填齐三项并正式发布v1；修改草稿不污染v1，重发得到v2|本次端到端及关联自动化通过|
|TC-02-B1|异常/边界|必填缺失 → 发布400|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|必填缺失 → 发布400|本次关联自动化通过|
|TC-02-B2|权限/状态|另一个旧窗口暂存 → 409，未关联教师发布 → 403|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|另一个旧窗口暂存 → 409，未关联教师发布 → 403|本次关联自动化通过|


- 本次通过：[CourseContentRevisionTest#testPublish_Success_IncrementsPublishVersion](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseContentRevisionTest.java:181>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseContentRevisionTest.xml
- 本次通过：[CourseContentRevisionTest#testPublish_MissingFields_Returns400](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseContentRevisionTest.java:141>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseContentRevisionTest.xml
- 本次通过：[CourseContentRevisionTest#testGetPublished_DraftEditingDoesNotPollutePublished](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseContentRevisionTest.java:253>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseContentRevisionTest.xml
- 本次通过：[CourseContentRevisionTest#testPublish_ForbiddenForNonRelatedTeacher](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseContentRevisionTest.java:159>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseContentRevisionTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us02-publication.png

**讲解要点：**

- 不完整内容可以暂存，但不能正式发布
- 这里没有独立ContentService，不能补画不存在的服务层
- 并发冲突返回409，读者只读已发布版本


### US-03 多教师开课与排课统筹

角色：主任。原件表5第4行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.2:开课统筹排课作为教研室主任，我希望按教师、周次、教室查看开课概览，以便统筹排课并发现冲突。

**验收标准：** 支持多教师开课；周次与节次展开正确；以资料中的文管A447、95人课程为样例核对展示。

**页面操作：**

1. 教研室主任工作台 → 开课排课
2. 新建或编辑班次，选择主讲、协同教师及选课名单
3. 为班次新增周次、星期、节次和教室
4. 按学期、教师、周次、教室组合筛选
5. 检查文管A447、95人样例和冲突提示

**前置及输入：** 文管A447，95名合成学生，主讲＋协同教师及周次/节次。使用该角色授权数据，各套测试夹具独立。

![US-03关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-03-annotated.svg)

**实现链路：**

![US-03六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-03.svg)

**HTTP接口：**

- `GET/POST /api/v1/courses/offerings`
- `PUT /api/v1/courses/offerings/{id}`
- `GET/POST /api/v1/schedules`
- `GET /api/v1/schedules/check-conflict`
- `GET /api/v1/courses/offerings/{id}`
- `DELETE /api/v1/schedules/{id}`

**存储：** CourseOfferingManagementService、CourseScheduleRepository → t_course_offering、t_course_offering_teacher、t_course_schedule、t_offering_student_enrollment

**数据变化：** 开课班次关联多个教师和名单；排课记录单独保存，冲突时拒绝提交。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/OfferingScheduleBoard.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/OfferingScheduleBoard.vue:647>)|async function save|按班次/排课表单分别调用API并显示冲突|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:331>)|scheduleApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseScheduleController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseScheduleController.java:42>)|saveSchedule|接收排课DTO|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseScheduleServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseScheduleServiceImpl.java:58>)|saveSchedule|校验可写班次、学期锁及教师/教室冲突|
|[backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingManagementService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingManagementService.java:41>)|save|多教师与名单维护，不等同单条排课|
|[backend/src/main/java/com/classroom/ai/modules/course/service/ScheduleConflictService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/ScheduleConflictService.java:39>)|find(|枚举教师及教室冲突；周次节次相交判断|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOffering.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOffering.java:18>)|t_course_offering|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOfferingTeacher.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOfferingTeacher.java:17>)|t_course_offering_teacher|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseSchedule.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseSchedule.java:18>)|t_course_schedule|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/OfferingStudentEnrollment.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/OfferingStudentEnrollment.java:18>)|t_offering_student_enrollment|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-03-N|正常路径|文管A447，95名合成学生，主讲＋协同教师及周次/节次|依次执行本项页面步骤；核对结果并回查数据。|主任维护多教师班次和文管A447课表，展开周次与节次后显示95人|本次端到端及关联自动化通过|
|TC-03-B1|异常/边界|相交时段教师或教室冲突 → 409与原因|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|相交时段教师或教室冲突 → 409与原因|本次关联自动化通过|
|TC-03-B2|权限/状态|冻结班次修改或越权排课 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|冻结班次修改或越权排课 → 拒绝|本次关联自动化通过|


- 本次通过：[CourseBrowserMysqlTest#realBrowserWorkflow](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseBrowserMysqlTest.java:68>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseBrowserMysqlTest.xml
- 本次通过：[CourseScheduleMultiTeacherConflictTest#collaboratorAcrossClassroomsBlocksAndIdentifiesTeacher](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseScheduleMultiTeacherConflictTest.java:15>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseScheduleMultiTeacherConflictTest.xml
- 本次通过：[CourseScheduleConflictTest#sameOfferingDifferentClassroomStillConflicts](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseScheduleConflictTest.java:29>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseScheduleConflictTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/legacy-browser/us0203-schedule-sample.png

**讲解要点：**

- 课程档案、开课班次、排课记录是不同对象
- 协同教师也参加冲突校验；跨学期分开判断
- 95人为隔离合成名单，不是核实真实在读学生


### US-05 大纲与毕业要求指标关联

角色：主任、教师。原件表6第2行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.3:大纲认证关联作为教研室主任，我希望维护课程大纲与毕业要求指标的对应关系，以便支持专业认证材料整理。

**验收标准：** 指标数量由导入的培养方案确定；映射可编辑、保存版本并追溯，不硬编码12条。

**页面操作：**

1. 主任工作台 → 指标矩阵 → 选择培养方案专业和版本
2. 导入明确标注的合成指标目录
3. 选择课程，从培养方案创建新大纲
4. 教师工作台 → 指标矩阵 → 编辑支撑关系与权重
5. 主任审查锁定；切换历史版本核对只读映射

**前置及输入：** 2条合成指标，独立planVersion及两个大纲版本。使用该角色授权数据，各套测试夹具独立。

![US-05关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-05-annotated.svg)

**实现链路：**

![US-05六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-05.svg)

**HTTP接口：**

- `PUT /api/v1/syllabus/plans/{majorCode}/{version}/indicators`
- `POST /api/v1/syllabus/course/{courseId}/from-plan`
- `GET /api/v1/syllabus/course/{courseId}/indicators`
- `POST /api/v1/syllabus/{id}/lock`
- `GET /api/v1/syllabus/course/{courseId}`
- `GET /api/v1/syllabus/course/{courseId}/latest`
- `GET /api/v1/syllabus/course/{courseId}/indicators?syllabusId={id}`
- `POST /api/v1/syllabus/course/{courseId}/indicators`
- `PUT/DELETE /api/v1/syllabus/indicators/{id}`

**存储：** TrainingIndicatorRepository / CourseSyllabusRepository / GraduationIndicatorRepository → t_training_indicator、t_course_syllabus、t_graduation_indicator

**数据变化：** 目录 → 绑定planVersion的大纲DRAFT → 指标映射 → LOCKED；不同大纲保留独立映射快照。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/DirectorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/DirectorDeskView.vue:1034>)|savePlanCatalog|解析目录后调用培养方案导入接口|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:365>)|syllabusApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java:115>)|importPlanIndicators|限制牵头专业导入、校验引用版本不可随意替换|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/SyllabusServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/SyllabusServiceImpl.java:146>)|lockSyllabus|校验主任范围并锁定当前大纲版本|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java:161>)|createFromPlan|新大纲绑定培养方案版本|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/SyllabusController.java:42>)|getIndicators|历史读取校验大纲确属该课程|
|[backend/src/main/java/com/classroom/ai/modules/course/service/impl/SyllabusServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/impl/SyllabusServiceImpl.java:184>)|updateIndicator|编辑映射时校验大纲状态和指标目录|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/TrainingIndicator.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/TrainingIndicator.java:8>)|t_training_indicator|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseSyllabus.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseSyllabus.java:18>)|t_course_syllabus|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/GraduationIndicator.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/GraduationIndicator.java:18>)|t_graduation_indicator|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-05-N|正常路径|2条合成指标，独立planVersion及两个大纲版本|依次执行本项页面步骤；核对结果并回查数据。|导入2条合成指标并创建大纲，编辑保存映射，锁定后历史可追溯|本次端到端及关联自动化通过|
|TC-05-B1|异常/边界|重复指标或引用版本替换 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|重复指标或引用版本替换 → 拒绝|本次关联自动化通过|
|TC-05-B2|权限/状态|跨课程历史ID读取、锁定后写入 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|跨课程历史ID读取、锁定后写入 → 拒绝|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#syllabusVersionsKeepTheirOwnMappings](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:104>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml
- 本次通过：[SyllabusHistoryHttpTest#versionOfAnotherCourseCannotBeReadThroughAnAuthorizedCourse](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/SyllabusHistoryHttpTest.java:77>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.SyllabusHistoryHttpTest.xml
- 本次通过：[SyllabusHistoryHttpTest#unauthorizedCourseDoesNotExposeItsHistory](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/SyllabusHistoryHttpTest.java:69>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.SyllabusHistoryHttpTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/sprint2-browser/core/00-teacher-syllabus-mapping.png

**讲解要点：**

- 指标数量随导入方案变化，不固定12条
- 合成目录仅验证机制，不是正式培养方案
- 历史版本只读，锁定后需新版本修改


### US-06 督导授权范围内组合检索

角色：督导。原件表5第6行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.3:大纲认证关联作为教学督导，我希望按专业、学期和教师组合检索课程，以便定位待听课对象。

**验收标准：** 单条件与组合条件均有测试；越权不可查询；记录测试数据量和响应时间，不承诺无条件300ms。

**页面操作：**

1. 教学督导工作台 → 课程检索
2. 选择授权专业、学期、教师，输入课程关键词
3. 逐个单条件查询，再组合查询
4. 清空筛选与不存在条件分别核对
5. 记录样本规模、请求次数及响应时间

**前置及输入：** SE＋学期＋协同教师ID＋课程关键词；另测未授权专业。使用该角色授权数据，各套测试夹具独立。

![US-06关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-06-annotated.svg)

**实现链路：**

![US-06六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-06.svg)

**HTTP接口：**

- `GET /api/v1/courses/offerings?term=&majorId=&teacherId=&keyword=`

**存储：** CourseOfferingRepository、CourseAuthorizationService → 开课、课程、主讲/协同关系及授权专业

**数据变化：** 只读查询，不写业务数据；未授权专业不能因组合条件而泄露。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/SupervisorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/SupervisorDeskView.vue:1003>)|handleFilterChange|筛选变化触发请求，迟到结果受请求代次约束|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:281>)|getOfferings:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingQueryController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingQueryController.java:15>)|search|传递多个可选筛选参数|
|[backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingQueryService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingQueryService.java:23>)|search|先授权过滤，再按学期、教师和关键词AND筛选|
|[backend/src/main/java/com/classroom/ai/modules/course/service/CourseAuthorizationService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/CourseAuthorizationService.java:178>)|filterOfferings|按主任/教师/督导分别限定班次读取范围|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-06-N|正常路径|SE＋学期＋协同教师ID＋课程关键词；另测未授权专业|依次执行本项页面步骤；核对结果并回查数据。|授权专业＋学期＋协同教师＋关键词组合得到预期班次|本次端到端及关联自动化通过|
|TC-06-B1|异常/边界|不存在条件 → 空列表|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|不存在条件 → 空列表|本次关联自动化通过|
|TC-06-B2|权限/状态|显式未授权专业或课程详情 → 403|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|显式未授权专业或课程详情 → 403|本次关联自动化通过|


- 本次通过：[CourseBrowserMysqlTest#realBrowserWorkflow](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseBrowserMysqlTest.java:68>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseBrowserMysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/legacy-browser/us0406-supervisor.png

**讲解要点：**

- 组合条件是AND
- 教师编码/ID比姓名更可靠，同名不代表同一身份
- 实测延迟仅适用于记录的数据量，不承诺无条件300ms
- 本轮检索实测：3门课程、3班次、2教师、95合成学生、2专业；20次顺序请求，中位数20.49ms，P95 26.18ms；中位数按20个观测的中间两值平均重算，P95沿用最近秩。仅为本地小样本，非生产容量承诺。


### US-04 历史开课与累计人次

角色：教师。原件表5第5行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动1.2:开课统筹排课作为任课教师，我希望查询历史开课学期、教室与学生人次，以便为备课提供参考。

**验收标准：** 按学期过滤；人数与累计人次分开标注；查询结果与样例数据一致。

**页面操作：**

1. 任课教师工作台 → 开课历史
2. 选择历史学期并查询
3. 查看各班次教师、教室、人数和累计人次
4. 查询不存在学期，确认空表及累计归零
5. 归档支线由主任在开课排课操作结课归档

**前置及输入：** 2026秋季及不存在学期；两个班次同一学生用于区分累计人次。使用该角色授权数据，各套测试夹具独立。

![US-04关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-04-annotated.svg)

**实现链路：**

![US-04六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-04.svg)

**HTTP接口：**

- `GET /api/v1/courses/offerings/history?term={term}`
- `POST /api/v1/courses/offerings/{id}/archive`

**存储：** CourseOfferingRepository / OfferingStudentEnrollmentRepository / OfferingHistorySnapshot → t_course_offering及history_snapshot

**数据变化：** 在读人数来自名单；结课后使用冻结快照；同一班次多教师不重复累计。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/OfferingHistoryPanel.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/OfferingHistoryPanel.vue:263>)|async function load|查询后替换历史列表和统计，避免保留旧数据|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:698>)|offeringHistoryApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingHistoryController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingHistoryController.java:44>)|getOfferingHistory|按角色过滤班次并计算累计人次|
|[backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingManagementService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/service/CourseOfferingManagementService.java:106>)|archive|归档冻结班次、人数和教学历史|
|[backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingHistoryController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/controller/CourseOfferingHistoryController.java:72>)|cumulativePersonTimes += count|人次累计只按班次计数；冻结读快照|
|[backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOffering.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/course/entity/CourseOffering.java:18>)|t_course_offering|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-04-N|正常路径|2026秋季及不存在学期；两个班次同一学生用于区分累计人次|依次执行本项页面步骤；核对结果并回查数据。|按学期看到历史班次，累计为班次人数之和；归档前后历史一致|本次端到端及关联自动化通过|
|TC-04-B1|异常/边界|不存在学期 → 空表且0人次|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|不存在学期 → 空表且0人次|本次关联自动化通过|
|TC-04-B2|权限/状态|教师只见本人关联班次，冻结班次写入拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|教师只见本人关联班次，冻结班次写入拒绝|本次关联自动化通过|


- 本次通过：[CourseBrowserMysqlTest#realBrowserWorkflow](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseBrowserMysqlTest.java:68>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseBrowserMysqlTest.xml
- 本次通过：[CourseOfferingHistoryTest#testHistoryStatistics_EnrollmentAndFrozenSnapshot](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseOfferingHistoryTest.java:104>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseOfferingHistoryTest.xml
- 本次通过：[CourseOfferingHistoryTest#testTeacherRoleDataIsolation](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/course/CourseOfferingHistoryTest.java:144>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.course.CourseOfferingHistoryTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/legacy-browser/us0406-history.png

**讲解要点：**

- 人数与累计人次不能混写
- 空结果必须清空旧统计
- 归档后不能通过排课或名单接口改写历史


## 路径2：教学资源

教师 → 同室教师/督导 → 主任 → 管理员

![教学资源操作次序与角色切换](20261016-exp1-exp4-feature-trace-v1-images/route-2.svg)


### US-07 按章节上传教学材料

角色：教师。原件表6第3行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.1:课件教案上传作为任课教师，我希望按章节上传课件、教案和实验指导书，以便集中维护教学材料。

**验收标准：** 允许类型与大小限制可配置；上传失败有提示；资源绑定课程并校验访问权限。

**页面操作：**

1. 任课教师工作台 → 课程资料 → 上传新课件/教案
2. 选择课程对应章节，填写名称
3. 选择PDF或允许的Office文件，选择标签与共享范围
4. 立即挂载 → 查看资源列表和失败提示

**前置及输入：** 有效PDF/DOCX/PPTX；损坏内容；超过100MB；PDF-only/1024字节配置。使用该角色授权数据，各套测试夹具独立。

![US-07关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-07-annotated.svg)

**实现链路：**

![US-07六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-07.svg)

**HTTP接口：**

- `POST /api/v1/resources/upload`

**存储：** CourseResourceRepository / ResourceVersionService → t_course_resource、s3_resource_series及隔离上传目录

**数据变化：** 实际文件与课程、章节绑定；新资源建立系列及首版；类型或大小校验失败不创建资源；损坏Office可能上传成功，但转换预览会失败。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/TeacherDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/TeacherDeskView.vue:1314>)|handleSaveResource|组织章节、标签、共享和实际文件|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:447>)|uploadFile:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:47>)|uploadResourceFile|校验课程、允许类型及大小，保存文件后调用元数据服务|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/impl/CourseResourceServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/impl/CourseResourceServiceImpl.java:45>)|saveResource|再次检查大小及课程写权，保存元数据并接入版本系列|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:61>)|file.getSize() > maxFileBytes|上传大小阈值配置实际执行位置|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java:65>)|public byte[] preview|内容有效性在预览转换阶段判定，不冒称上传时已检测|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java:19>)|t_course_resource|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java:5>)|s3_resource_series|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-07-N|正常路径|有效PDF/DOCX/PPTX；损坏内容；超过100MB；PDF-only/1024字节配置|依次执行本项页面步骤；核对结果并回查数据。|有效PDF/DOCX/PPTX上传后列表可见并可渲染|本次端到端及关联自动化通过|
|TC-07-B1|异常/边界|超100MB或禁用类型 → 上传拒绝；损坏Office → 转换预览失败并提示|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|超100MB或禁用类型 → 上传拒绝；损坏Office → 转换预览失败并提示|本次关联自动化通过|
|TC-07-B2|权限/状态|PDF-only/1024字节配置必须生效|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|PDF-only/1024字节配置必须生效|本次关联自动化通过|


- 本次通过：[CourseResourceServiceTest#testUploadValidResource_ShouldGenerateWatermark](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/resource/CourseResourceServiceTest.java:64>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.resource.CourseResourceServiceTest.xml
- 本次通过：[CourseResourceServiceTest#testUploadSizeLimit_Over100MB_ShouldThrowException](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/resource/CourseResourceServiceTest.java:44>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.resource.CourseResourceServiceTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us07-upload.png

**讲解要点：**

- 上限和允许类型以配置为准，默认100MB
- Office预览依赖LibreOffice；上传按后缀校验类型，损坏Office在转换预览阶段失败
- 共享只影响读取，不授予修改权


### US-08 教学环节多选标签

角色：教师。原件表6第4行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.1:课件教案上传作为任课教师，我希望为资源标注理论、实验或研讨等环节，以便方便分类查找。

**验收标准：** 多选标签可维护；标签筛选结果正确；无标签资源有明确显示。

**页面操作：**

1. 教师工作台 → 课程资料
2. 上传时多选理论、实验、讨论等实际选项
3. 资源卡片 → 编辑标签与共享 → 保存修改
4. 分别点击单个标签、未标注和全部，核对结果

**前置及输入：** 理论、实验、讨论等白名单多选与空标签。使用该角色授权数据，各套测试夹具独立。

![US-08关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-08-annotated.svg)

**实现链路：**

![US-08六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-08.svg)

**HTTP接口：**

- `POST /api/v1/resources`
- `GET /api/v1/resources?tag={tag}`

**存储：** CourseResourceRepository → t_course_resource.tags；ResourceVersionService同步系列共享

**数据变化：** 文件及课程归属不变，仅标签和共享属性更新；空标签按未标注展示。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/TeacherDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/TeacherDeskView.vue:1308>)|saveResourceMetadata|只发送可编辑元数据并刷新列表|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:433>)|export const resourceApi =|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:144>)|saveResource|元数据编辑入口；此处执行标签白名单校验|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/impl/CourseResourceServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/impl/CourseResourceServiceImpl.java:45>)|saveResource|校验资源归属、可写权限并保存元数据；白名单在控制器执行|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:152>)|dto.getTags().stream()|非法元数据标签在此拒绝，防绕过UI|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java:19>)|t_course_resource|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-08-N|正常路径|理论、实验、讨论等白名单多选与空标签|依次执行本项页面步骤；核对结果并回查数据。|勾选多标签保存后，各标签筛选均能找到资源|本次端到端及关联自动化通过|
|TC-08-B1|异常/边界|非法标签 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|非法标签 → 拒绝|本次关联自动化通过|
|TC-08-B2|权限/状态|清空标签 → 未标注可见且筛选正确|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|清空标签 → 未标注可见且筛选正确|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#resourceVisibilityTagsAndExpiringPreview](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:133>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/sprint2-browser/core/02-edit-tags-and-sharing.png

**讲解要点：**

- 原文举例“研讨”，当前界面选项为“讨论”，按实际白名单说明
- 不能借元数据更新重挂文件或改课程
- 多选与无标签均需要筛选验证


### US-10 教研室共享资源检索

角色：教师。原件表6第6行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.2:检索在线预览作为任课教师，我希望检索教研室内允许共享的优秀教学资源，以便支持集体备课。

**验收标准：** 公开范围与角色权限共同生效；搜索结果不得暴露未授权文件。

**页面操作：**

1. 资源所属教师 → 课程资料 → 编辑标签与共享 → 勾选教研室共享
2. 同室其他教师 → 共享备课 → 输入资源关键词 → 检索共享资源
3. 授权预览，核对只有读取权限
4. 资源所属教师撤销共享，原读者刷新及重试旧票据

**前置及输入：** 同室共享/私有、外室资源与唯一关键词。使用该角色授权数据，各套测试夹具独立。

![US-10关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-10-annotated.svg)

**实现链路：**

![US-10六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-10.svg)

**HTTP接口：**

- `GET /api/v1/resources?isPublic=true&keyword=`
- `POST /api/v1/resources`

**存储：** CourseResourceRepository / ResourceAccessService → t_course_resource、s3_resource_series

**数据变化：** 共享true → 同室教师可发现和预览；撤销时授权epoch变化，旧访问同时收回。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/TeacherDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/TeacherDeskView.vue:1289>)|loadSharedResources|共享备课库按关键词请求授权资源|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:436>)|search: async (params:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:121>)|searchResources|接收共享和标签筛选|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceAccessService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceAccessService.java:21>)|canRead|角色、课程授权及同教研室共享共同判断|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java:56>)|public void sharing|撤销共享提升授权纪元，旧票据收回|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java:19>)|t_course_resource|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java:5>)|s3_resource_series|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-10-N|正常路径|同室共享/私有、外室资源与唯一关键词|依次执行本项页面步骤；核对结果并回查数据。|同室跨教师、跨课程搜索到共享资料并预览|本次端到端及关联自动化通过|
|TC-10-B1|异常/边界|未共享、外教研室资源不出现在结果|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|未共享、外教研室资源不出现在结果|本次关联自动化通过|
|TC-10-B2|权限/状态|撤销后旧票据、旧预览与详情不可继续访问|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|撤销后旧票据、旧预览与详情不可继续访问|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#resourceVisibilityTagsAndExpiringPreview](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:133>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#revocationClosesOldIdAndReadsFreshEpoch](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:88>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/sprint2-browser/core/01-cross-teacher-shared-search.png

**讲解要点：**

- isPublic在本项目表示受控教研室共享，不是互联网公开
- 可读不意味着可改、可下载
- 课程授权与共享读取权限不能混同


### US-09 限时授权水印预览

角色：督导。原件表6第5行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.2:检索在线预览作为教学督导，我希望在授权范围内在线预览课程资料，以便提前了解教学设计。

**验收标准：** 已登录用户经权限校验后预览；临时链接到期失效；基础水印和访问日志随预览上线。

**页面操作：**

1. 教学督导工作台 → 课程检索 → 选择班次
2. 课件免密预审 → 选择资料
3. 打开限时水印预览 → 翻页查看真实内容
4. 到期、权限撤销或换账号后重试旧链接

**前置及输入：** 已登录督导、绑定申请人的票据；到期/异账号/撤销票据。使用该角色授权数据，各套测试夹具独立。

![US-09关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-09-annotated.svg)

**实现链路：**

![US-09六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-09.svg)

**HTTP接口：**

- `POST /api/v1/resources/{id}/preview-ticket`
- `GET /api/v1/resources/preview/{ticket}`
- `GET /api/v1/resources/{id}（前端周期复核）`
- `GET /api/v1/resources/{id}/download（督导拒绝）`

**存储：** ResourceAccessService / ResourceAccessLogRepository → t_resource_access_log；票据及文件转换缓存

**数据变化：** 登录 → 课程授权 → 票据 → 再鉴权 → 水印PDF → 访问日志。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/SupervisorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/SupervisorDeskView.vue:1137>)|previewResource|申请受控PDF并周期复核访问权|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:460>)|getPreviewPdf:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java:185>)|preview|消费短期票据并返回PDF字节|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java:65>)|preview|再次校验申请人、到期、权限和授权纪元，生成水印与日志|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java:56>)|createTicket|申请票据时绑定当前申请人和授权纪元|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceFileService.java:156>)|watermarkImage|预览水印生成|
|[backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceAccessService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/service/ResourceAccessService.java:36>)|requireRead|票据有效也必须仍有当前读取权限|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/ResourceAccessLog.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/ResourceAccessLog.java:8>)|t_resource_access_log|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-09-N|正常路径|已登录督导、绑定申请人的票据；到期/异账号/撤销票据|依次执行本项页面步骤；核对结果并回查数据。|授权督导打开真实PDF/Office转换内容，水印及访问日志存在|本次端到端及关联自动化通过|
|TC-09-B1|异常/边界|到期或跨申请人使用票据 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|到期或跨申请人使用票据 → 拒绝|本次关联自动化通过|
|TC-09-B2|权限/状态|原件直链或授权撤销后的旧票据 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|原件直链或授权撤销后的旧票据 → 拒绝|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#resourceVisibilityTagsAndExpiringPreview](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:133>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#cachedPreviewAndOldTicketsAreRejectedAfterRevocation](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:116>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/sprint2-browser/office/protected-supervisor.png

**讲解要点：**

- 免密表示无需额外文件密码，仍要求登录与授权
- 默认5分钟，按配置变化；旧票据撤销后也失效
- 受控预览不等于原件下载，不承诺防截屏


### US-11 资源版本与当前版控制

角色：主任、教师。原件表7第2行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.3:版本版权控制作为教研室主任，我希望查看资源历史版本并控制可用版本，以便追溯资源修订。

**验收标准：** 上传新版本保留旧版记录；当前版本可识别；基础鉴权与水印沿用US-09，不延后补安全控制。

**页面操作：**

1. 统计与反馈 → 资源版本 → 刷新资源
2. 选择当前资源，查看系列、版本号及历史ID
3. 教师选择新文件 → 上传新版并设为当前
4. 主任选择历史版 → 设为当前版
5. 按固定历史ID预览，验证旧文件仍保留

**前置及输入：** 同seriesId首版/新版、固定历史ID及旧expectedVersion。使用该角色授权数据，各套测试夹具独立。

![US-11关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-11-annotated.svg)

**实现链路：**

![US-11六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-11.svg)

**HTTP接口：**

- `GET /api/v1/resources/{id}/history`
- `POST /api/v1/resources/upload`
- `PUT /api/v1/resources/series/{id}/current`
- `GET /api/v1/resources/series/{id}/current`

**存储：** EntityManager / CourseResourceRepository → s3_resource_series、t_course_resource

**数据变化：** 同一系列增加versionNumber与固定资源ID；currentVersionId切换，旧文件和历史ID不重写。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/ResourceVersionsPanel.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/ResourceVersionsPanel.vue:57>)|async function selectVersion|携带series.revision防止并发切换|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:25>)|public Object current(|当前版本切换API|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java:48>)|public ResourceSeries select|校验系列归属、权限和expectedVersion|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java:18>)|public CourseResource attach|上传新版锁系列并验证expectedVersion|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceVersionService.java:42>)|public Map<String,Object> history|历史读取依然经过授权|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ResourceSeries.java:5>)|s3_resource_series|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/CourseResource.java:19>)|t_course_resource|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-11-N|正常路径|同seriesId首版/新版、固定历史ID及旧expectedVersion|依次执行本项页面步骤；核对结果并回查数据。|新增第二版后旧版可追溯；主任切回首版且指针正确|本次端到端及关联自动化通过|
|TC-11-B1|异常/边界|旧revision切换 → 409|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|旧revision切换 → 409|本次关联自动化通过|
|TC-11-B2|权限/状态|同名共享教师不能借名称取得所有权|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|同名共享教师不能借名称取得所有权|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#resourceConcurrentUploadsAndFixedHistoricalIds](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:86>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#databaseRejectsCrossSeriesPointerAndDuplicateVersion](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:87>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/us11-versions.png

**讲解要点：**

- 版本号、资源ID、系列ID是不同标识
- 并发旧revision应返回409
- 共享只读者不能上传新版本或切换当前版


### US-12 授权微格片段与元数据回看

角色：管理员；主任、教师按授权课程操作。原件表7第3行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动2.3:版本版权控制作为系统管理员，我希望挂载经授权的微格切片及课堂分析元数据，以便为课堂回看和后续分析接入提供接口。

**验收标准：** 校验课程、片段起止时间、标签和来源；能按片段回看；接口测试使用授权样例，不声称已实现自动切片。

**页面操作：**

1. 微格教学视频 → 选择微格课程
2. 上传授权合成MP4，填写来源、授权声明和教学环节
3. 按服务端识别时长填写起止毫秒并挂载片段
4. 可选填写课堂分析元数据，由管理员复核
5. 回看片段，验证拖动边界及终点自动停止

**前置及输入：** 2秒纯合成MP4；500–1500ms片段；负值/反向/超时区间。使用该角色授权数据，各套测试夹具独立。

![US-12关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-12-annotated.svg)

**实现链路：**

![US-12六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-12.svg)

**HTTP接口：**

- `POST /api/v1/admin/micro-assets`
- `POST /api/v1/admin/micro-slices`
- `GET /api/v1/admin/micro-slices/{id}/video`
- `POST /api/v1/admin/micro-slices/{id}/review`
- `GET /api/v1/admin/courses`
- `GET /api/v1/admin/micro-assets?courseId=`
- `GET /api/v1/admin/micro-slices?courseId=`
- `PUT/DELETE /api/v1/admin/micro-slices/{id}`
- `POST/GET /api/v1/resources/micro-assets（非管理员授权课程入口）`

**存储：** EntityManager → s3_video_asset、t_micro_teaching_slice及微格文件目录

**数据变化：** 上传资产由ffprobe确认时长；片段引用资产和起止区间；元数据复核另有记录。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/MicroTeachingView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/MicroTeachingView.vue:91>)|const mount=|提交来源与起止区间，播放器按毫秒限定范围|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:29>)|public Object mount(|微格挂载与管理员元数据复核入口|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java:74>)|public MicroTeachingSlice mount|课程、资产、来源、区间、标签和分析元数据验证|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java:62>)|public long probe|服务端探测视频时长|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAssetService.java:89>)|validateAnalysis|分析元数据字段、区间与数量限制|
|[frontend/src/views/MicroTeachingView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/MicroTeachingView.vue:117>)|function seek|播放器阻止拖出片段|
|[frontend/src/views/MicroTeachingView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/MicroTeachingView.vue:118>)|function bound|达到片段终点自动暂停|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAsset.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/VideoAsset.java:5>)|s3_video_asset|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/resource/entity/MicroTeachingSlice.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/resource/entity/MicroTeachingSlice.java:18>)|t_micro_teaching_slice|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-12-N|正常路径|2秒纯合成MP4；500–1500ms片段；负值/反向/超时区间|依次执行本项页面步骤；核对结果并回查数据。|上传2秒合成视频，挂载500–1500ms，播放器限制拖动并在终点停止|本次端到端及关联自动化通过|
|TC-12-B1|异常/边界|负起点、反向区间、超时长或缺来源 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|负起点、反向区间、超时长或缺来源 → 拒绝|本次关联自动化通过|
|TC-12-B2|权限/状态|文件哈希改变、越权资产或不可用探测工具 → 拒绝/503|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|文件哈希改变、越权资产或不可用探测工具 → 拒绝/503|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#realVideoDurationAndAllSixStagesAreVerifiedOnServer](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:96>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#changedVideoHashBlocksPlaybackAndReplacementDoesNotMoveOldInterval](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:107>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#invalidProbeDoesNotSaveAssetAndUnavailableToolReturns503](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:101>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/admin-video-segment.png

**讲解要点：**

- 2秒视频、500–1500毫秒区间是纯合成样例
- 不声称自动切片、自动课堂分析或真实设备识别
- 非管理员只有课程授权内操作；未复核分析数据不直接公开


## 路径3：评价与改进

管理员 → 督导 → 主任 → 教师

![评价与改进操作次序与角色切换](20261016-exp1-exp4-feature-trace-v1-images/route-3.svg)


### US-20 评教模板与权重版本

角色：管理员。原件表7第8行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.3:综合分析报表作为系统管理员，我希望维护评教指标与权重模板，以便适配不同评价场景。

**验收标准：** 权重和校验为100%；模板版本可追溯；新模板不改写历史评价记录。

**页面操作：**

1. 模板管理 → 模板草稿
2. 填写名称、场景、四维说明及权重，例如25/25/25/25
3. 新建草稿 → 发布不可变版本
4. 已发布版本 → 基于此版本新建草稿
5. 新模板供后续评价选择，核对历史评价未变化

**前置及输入：** 25/25/25/25、小数权重、总和不100及旧revision。使用该角色授权数据，各套测试夹具独立。

![US-20关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-20-annotated.svg)

**实现链路：**

![US-20六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-20.svg)

**HTTP接口：**

- `GET/POST /api/v1/admin/templates`
- `PUT /api/v1/admin/templates/{id}`
- `POST /api/v1/admin/templates/{id}/publish`
- `GET /api/v1/sprint3/templates`

**存储：** EntityManager → s3_template_draft、s3_template_version；评价保存template_version_id、weight_snapshot

**数据变化：** 可编辑草稿 → 不可变发布版本；后续版本不回写历史评价权重。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:86>)|function publish|传递草稿revision并刷新模板|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:20>)|public Object publish(|模板发布与版本参数入口|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java:30>)|public TemplateVersion publish|管理员、权重100%、不可变发布与并发控制|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java:50>)|public static void validate|精确校验权重和为100%|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java:20>)|public TemplateDraft save|可编辑草稿与不可变发布版本分离|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/TemplateDraft.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/TemplateDraft.java:5>)|s3_template_draft|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/TemplateVersion.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/TemplateVersion.java:5>)|s3_template_version|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-20-N|正常路径|25/25/25/25、小数权重、总和不100及旧revision|依次执行本项页面步骤；核对结果并回查数据。|保存并发布25/25/25/25模板，督导可选择已发布版本|本次端到端及关联自动化通过|
|TC-20-B1|异常/边界|权重总和错误或负值 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|权重总和错误或负值 → 拒绝|本次关联自动化通过|
|TC-20-B2|权限/状态|同revision并发发布只有一方成功，已发布版不可编辑|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|同revision并发发布只有一方成功，已发布版不可编辑|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#templateConcurrentPublishHasOneWinnerAndPublishedIsImmutable](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:82>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#templateStaleEditRollsBack](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:83>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#unpublishedAndForgedWeightsCannotBeSubmitted](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:85>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/us20-template.png

**讲解要点：**

- 四维权重总和必须100%，允许小数
- 模板草稿不可直接用来正式评价
- 管理员不自动获得主任、教师或督导的业务权限


### US-13 结构化听课评价

角色：督导。原件表6第7行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.1:督导随堂评价作为教学督导，我希望提交结构化听课评价，以便形成可检索的督导记录。

**验收标准：** 教学态度、内容、方法、效果作为本版示例维度；可配置权重；支持暂存、必填校验和审核状态。

**页面操作：**

1. 督导工作台 → 课程检索 → 目标班次 → 随堂评价
2. 选择已发布模板，填写主题与四维评分
3. 填写至少3条不同亮点、建议及问题标签
4. 暂存草稿；重新打开检查
5. 正式提交（待审核） → 主任审核队列

**前置及输入：** 四维明确0分/缺失值、至少3条不同亮点及已发布模板。使用该角色授权数据，各套测试夹具独立。

![US-13关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-13-annotated.svg)

**实现链路：**

![US-13六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-13.svg)

**HTTP接口：**

- `GET /api/v1/sprint3/templates`
- `POST /api/v1/supervisions`
- `GET /api/v1/supervisions/{id}`

**存储：** SupervisionEvaluationRepository / EvaluationTemplateService → t_supervision_evaluation、s3_advice

**数据变化：** DRAFT/REJECTED → 编辑暂存 → PENDING_REVIEW；写入模板ID与权重快照。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/SupervisorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/SupervisorDeskView.vue:1204>)|handleSaveEvaluation|暂存和正式提交使用同一事件的isDraft分支|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:511>)|supervisionApi|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionController.java:50>)|submitEvaluation|接收评价DTO并返回状态|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java:87>)|submitEvaluation|绑定实际督导身份，验证模板、评分、亮点及版本|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/EvaluationTemplateService.java:55>)|public static Double total|正权重评分必填，明确0分合法，使用服务端模板计算|
|[backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java:19>)|t_supervision_evaluation|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Advice.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Advice.java:5>)|s3_advice|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-13-N|正常路径|四维明确0分/缺失值、至少3条不同亮点及已发布模板|依次执行本项页面步骤；核对结果并回查数据。|不完整评价先暂存，补齐主题、评分与亮点后提交待审核|本次端到端及关联自动化通过|
|TC-13-B1|异常/边界|缺失/空正权重评分或不足3条不同亮点 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|缺失/空正权重评分或不足3条不同亮点 → 拒绝|本次关联自动化通过|
|TC-13-B2|权限/状态|过时expectedVersion → 并发冲突|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|过时expectedVersion → 并发冲突|本次关联自动化通过|


- 本次通过：[SupervisionServiceTest#incompleteDraftCanStillBeSaved](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/supervision/SupervisionServiceTest.java:141>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.supervision.SupervisionServiceTest.xml
- 本次通过：[SupervisionServiceTest#explicitZeroScoresCanBeSubmitted](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/supervision/SupervisionServiceTest.java:133>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.supervision.SupervisionServiceTest.xml
- 本次通过：[SupervisionServiceTest#formalSubmissionRejectsMissingScores(String)[1]](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/supervision/SupervisionServiceTest.java:121>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.supervision.SupervisionServiceTest.xml
- 本次通过：[Sprint3MysqlTest#evaluationConcurrentDraftEditHasOneWinner](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:84>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us13-evaluation-form.png

**讲解要点：**

- 示例四维是态度、内容、方法、效果
- 零分合法；缺失评分与零分不同
- 暂存允许不完整，提交按正权重维度校验


### US-14 审核、延迟与匿名反馈

角色：主任、督导、教师。原件表6第8行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.1:督导随堂评价作为教学督导，我希望提交亮点与改进意见并经审核反馈教师，以便支持有依据的教学改进。

**验收标准：** 审核后按角色反馈并脱敏；延迟时长可配置，演示默认24小时；保留修改与审核日志。

**页面操作：**

1. 督导正式提交评价
2. 主任工作台 → 评价审核 → 查看评价 → 通过或驳回
3. 驳回后督导修改并重新提交
4. 通过后核对反馈发布时间，教师到期前不可见
5. 到期后教师工作台 → 教学反馈，核对身份脱敏与审核日志

**前置及输入：** 默认24h、0h配置、审核通过/驳回及到期前后。使用该角色授权数据，各套测试夹具独立。

![US-14关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-14-annotated.svg)

**实现链路：**

![US-14六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-14.svg)

**HTTP接口：**

- `POST /api/v1/supervisions/{id}/review`
- `GET /api/v1/supervisions/{id}/audit`
- `GET /api/v1/supervisions?offeringId={id}`

**存储：** SupervisionEvaluationRepository / EvaluationAuditLogRepository → t_supervision_evaluation、t_evaluation_audit_log

**数据变化：** PENDING_REVIEW → REJECTED或APPROVED_PENDING → 到期反馈可见/PUBLISHED；教师响应移除督导及审核身份。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/DirectorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/DirectorDeskView.vue:1053>)|reviewEvaluation|发送通过/驳回决定并刷新审核队列|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:527>)|review:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionController.java:69>)|public ApiResponse<SupervisionEvaluation> review|主任审核接口|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java:201>)|reviewEvaluation|记录审核人与时间并计算可配置publishTime|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java:254>)|private SupervisionEvaluation anonymize|教师响应去除督导和审核身份|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java:265>)|private void audit|修改与审核留存审计|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionServiceImpl.java:220>)|refreshDesensitizeStatus|到期状态刷新与可见判断配合|
|[backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java:19>)|t_supervision_evaluation|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/supervision/entity/EvaluationAuditLog.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/entity/EvaluationAuditLog.java:8>)|t_evaluation_audit_log|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-14-N|正常路径|默认24h、0h配置、审核通过/驳回及到期前后|依次执行本项页面步骤；核对结果并回查数据。|主任通过后默认延迟86400秒，到期前教师403，到期后匿名反馈可见|本次端到端及关联自动化通过|
|TC-14-B1|异常/边界|驳回可修改重提并留审计|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|驳回可修改重提并留审计|本次关联自动化通过|
|TC-14-B2|权限/状态|非主任审核、到期前教师访问或未审核直接公开 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|非主任审核、到期前教师访问或未审核直接公开 → 拒绝|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#reviewDelayAndSemesterCoverage](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:230>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#managementCountsApprovedBeforeFeedbackButTeacherCannotSee](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:70>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#publishedWithoutAuditIsNeverIncluded](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:73>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us14-published-feedback.png

**讲解要点：**

- 默认24h来自审核时间，配置为0时可即时发布
- 测试推进隔离记录时间，不假装真实等候24h
- 脱敏降低直接身份暴露，不承诺小样本情境的无条件匿名


### US-21 建议到教学行动闭环

角色：督导、教师。原件表7第9行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.3:综合分析报表作为任课教师，我希望接收改进建议并登记后续实践反馈，以便形成建议到行动的跟踪记录。

**验收标准：** 接口接收建议、证据引用与教学环节标签；教师可接受、调整并填写行动反馈；BOPPPS作为可选标签，不等同四维评分。

**页面操作：**

1. 督导先暂存评价草稿
2. 统计与反馈 → 督导建议 → 本人可编辑评价 → 新增独立建议
3. 填写建议文本、同课程证据和可选教学环节标签
4. 父评价提交并经主任审核，到期后教师 → 统计与反馈 → 改进行动
5. 选择接受或调整，填写行动内容和实践反馈 → 保存行动反馈 → 查看修改历史

**前置及输入：** 建议Practice in groups；ACCEPTED/ADJUSTED；行动、反馈、证据及历史revision。使用该角色授权数据，各套测试夹具独立。

![US-21关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-21-annotated.svg)

**实现链路：**

![US-21六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-21.svg)

**HTTP接口：**

- `POST /api/v1/supervisions/{id}/suggestions`
- `GET /api/v1/sprint3/suggestions/mine`
- `PUT /api/v1/suggestions/{id}/my-feedback`
- `GET /api/v1/supervisions/{id}/evidence-candidates`
- `GET /api/v1/supervisions/{id}/suggestions`

**存储：** EntityManager → s3_advice、s3_action_feedback、s3_feedback_revision

**数据变化：** 评价下多条建议 → 教师个人ACCEPTED/ADJUSTED行动 → 每次保存形成历史修订；协同教师各自独立反馈。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:78>)|saveFeedback|只提交当前建议编辑器，保留其他未保存输入|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:50>)|public Object feedback(|教师行动反馈接口|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java:75>)|public ActionFeedback feedback|校验父评价可见性、教师稳定身份、revision及证据范围|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java:34>)|public Advice receive|建议附属于同一评价，保留多条独立建议|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java:112>)|private JsonNode evidence|证据限定同课程与可访问范围|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/AdviceService.java:71>)|private Map<String,Object> historyView|修订历史也需按角色脱敏|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Advice.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Advice.java:5>)|s3_advice|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/ActionFeedback.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/ActionFeedback.java:5>)|s3_action_feedback|持久化实体对应的实际表名|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/FeedbackRevision.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/FeedbackRevision.java:5>)|s3_feedback_revision|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-21-N|正常路径|建议Practice in groups；ACCEPTED/ADJUSTED；行动、反馈、证据及历史revision|依次执行本项页面步骤；核对结果并回查数据。|到期后教师接受建议、填写行动和实践反馈，保存后可查修改历史|本次端到端及关联自动化通过|
|TC-21-B1|异常/边界|到期前或其他教师反馈 → 拒绝|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|到期前或其他教师反馈 → 拒绝|本次关联自动化通过|
|TC-21-B2|权限/状态|跨课程证据、过时revision → 拒绝；协同教师反馈互不覆盖|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|跨课程证据、过时revision → 拒绝；协同教师反馈互不覆盖|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#multipleAdviceAndFeedbackHistoryCannotBeOverwritten](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:89>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#evidenceMustBeSameCourseAndAccessible](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:91>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#coTeachersMaintainIndependentFeedbackRows](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:131>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/teacher-feedback.png

**讲解要点：**

- BOPPPS只作为可选环节标签，不能替代四维评分
- 证据必须同课程且可访问，不能任意引用ID
- 并发版本防覆盖，历史反馈保留


## 路径4：统计与分析

督导 → 主任 → 教师 → 督导 → 主任

![统计与分析操作次序与角色切换](20261016-exp1-exp4-feature-trace-v1-images/route-4.svg)


### US-15 学期课程覆盖统计

角色：督导。原件表6第9行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.2:覆盖统计巡检作为教学督导，我希望查看课程开设数量和督导覆盖情况，以便安排未覆盖课程的听课。

**验收标准：** 明确分母为选定学期有效开课课程；分子去重；能由明细追溯覆盖率。

**页面操作：**

1. 督导工作台 → 课程检索
2. 选择学期，查看开课数、覆盖数及覆盖率
3. 查看覆盖明细与评价记录
4. 用明细重算分母和去重分子
5. 排除无效班次，比较相同课程多个班次

**前置及输入：** 选定学期有效课程、重复评价、无效班次及评价ID明细。使用该角色授权数据，各套测试夹具独立。

![US-15关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-15-annotated.svg)

**实现链路：**

![US-15六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-15.svg)

**HTTP接口：**

- `GET /api/v1/supervisions/analytics/dashboard?term=`
- `GET /api/v1/supervisions/analytics/coverage?term=`

**存储：** CourseOfferingRepository / SupervisionEvaluationRepository → 有效开课课程、已审核评价

**数据变化：** 只读聚合：分母为选定学期有效开课课程去重数；分子为已有合格审核评价的课程数。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/views/SupervisorDeskView.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/views/SupervisorDeskView.vue:1117>)|loadAnalytics|使用选定学期加载指标及可追溯明细|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:531>)|getCoverage:|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionAnalyticsController.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/controller/SupervisionAnalyticsController.java:35>)|getCoverage|覆盖统计接口|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionAnalyticsServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionAnalyticsServiceImpl.java:57>)|getCoverageDetails|筛选有效开课并按课程归并审核评价ID|
|[backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionAnalyticsServiceImpl.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/service/impl/SupervisionAnalyticsServiceImpl.java:93>)|chosenTerm|明确学期选择，不混计跨学期课程|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-15-N|正常路径|选定学期有效课程、重复评价、无效班次及评价ID明细|依次执行本项页面步骤；核对结果并回查数据。|选择学期后由明细评价ID追溯已覆盖课程，手算与页面一致|本次端到端及关联自动化通过|
|TC-15-B1|异常/边界|重复评价不重复计数|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|重复评价不重复计数|本次关联自动化通过|
|TC-15-B2|权限/状态|无效开课、其他学期和未授权专业不进入分母|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|无效开课、其他学期和未授权专业不进入分母|本次关联自动化通过|


- 本次通过：[Sprint2MysqlTest#reviewDelayAndSemesterCoverage](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/Sprint2MysqlTest.java:230>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.Sprint2MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#managementCountsApprovedBeforeFeedbackButTeacherCannotSee](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:70>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#categoriesExcludeOtherTermsUnauthorizedMajorsAndPending](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:77>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/supplement/us15-coverage.png

**讲解要点：**

- 课程覆盖数不是评价条数，也不是班次数
- 管理统计可计入已审核但尚未对教师公开的评价
- 所有统计都受课程可见范围约束


### US-16 低覆盖与低评价预警

角色：主任。原件表7第4行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.2:覆盖统计巡检作为教研室主任，我希望查看低覆盖或低评价课程预警，以便安排核查与教学帮扶。

**验收标准：** 阈值可配置并注明来源；零覆盖可识别；预警需人工复核，不能直接作为考核结论。

**页面操作：**

1. 统计与反馈 → 选择学期 → 查询
2. 切换质量预警
3. 查看零覆盖、低覆盖或低分预警
4. 核对显示阈值与来源
5. 回查相关课程与评价明细，人工复核

**前置及输入：** 低分阈值75与原始均分74.99；无权重/缺失维度。使用该角色授权数据，各套测试夹具独立。

![US-16关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-16-annotated.svg)

**实现链路：**

![US-16六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-16.svg)

**HTTP接口：**

- `GET /api/v1/sprint3/alerts?term=`

**存储：** EntityManager / CourseOfferingRepository → 聚合审核评价与课程；配置low-score-threshold、low-coverage-threshold

**数据变化：** 只读生成预警；阈值、来源及样本随结果返回，不直接写考核结论。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:63>)|async function fetchData|按角色和已生效筛选拉取统计结果|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:37>)|public Object alerts(|接收筛选并调用统计服务|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:79>)|public List<Map<String,Object>> alerts|按配置阈值及原始均分判断，缺失/未知权重不伪装低分|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:111>)|completeKnown|缺失/未知权重不能作为零分参与低分判断|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-16-N|正常路径|低分阈值75与原始均分74.99；无权重/缺失维度|依次执行本项页面步骤；核对结果并回查数据。|零覆盖课程被识别，低分阈值及配置来源可见|本次端到端及关联自动化通过|
|TC-16-B1|异常/边界|无样本或未知历史权重 → 不错误判为低分|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|无样本或未知历史权重 → 不错误判为低分|本次关联自动化通过|
|TC-16-B2|权限/状态|边界均分74.99 → 低于75仍应预警|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|边界均分74.99 → 低于75仍应预警|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#unknownWeightsAndMissingDimensionStayOutOfLowScores](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:80>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#lowScoreThresholdUsesUnroundedMean](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:130>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/us16-alerts.png

**讲解要点：**

- 零覆盖单独识别
- 按模板计算，缺失分和未知权重不能补零
- 74.99的比较应在四舍五入前进行


### US-17 本人评价汇总与雷达图

角色：教师。原件表7第5行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.3:综合分析报表作为任课教师，我希望查看本人课程评价汇总和雷达图，以便分析教学改进方向。

**验收标准：** 只展示授权范围；样本量与维度口径明确；缺失项不当作零分；不得把匿名性写成无条件保证。

**页面操作：**

1. 统计与反馈 → 学期及课程筛选 → 查询
2. 评价分析 → 查看四维雷达和数据表
3. 核对样本量、维度有效值和归一化尺度
4. 比较缺失维度、明确0分与旧版未知权重
5. 仅查询本人授权课程且已到期反馈

**前置及输入：** 本人到期反馈、2个样本、100分归一化；明确0分与缺失值。使用该角色授权数据，各套测试夹具独立。

![US-17关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-17-annotated.svg)

**实现链路：**

![US-17六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-17.svg)

**HTTP接口：**

- `GET /api/v1/sprint3/radar?term=&courseType=&keyword=`

**存储：** EntityManager → t_supervision_evaluation权重快照与课程授权

**数据变化：** 只读：按已公开有效评价聚合；缺失为null而非0，未知权重样本单独说明。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:57>)|const managedRadar=|四维完整才绘图；否则提示查看数据表|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:36>)|public Object radar(|教师统计入口|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:115>)|public Map<String,Object> radar|授权、到期过滤、逐维有效样本和100分尺度归一化|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:24>)|public Snapshot snapshot|教师反馈须在本人授权范围且到期|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:57>)|const managedRadar=|维度不完整时只提供表格，不补造雷达分值|
|[backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java:19>)|t_supervision_evaluation|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-17-N|正常路径|本人到期反馈、2个样本、100分归一化；明确0分与缺失值|依次执行本项页面步骤；核对结果并回查数据。|已公开本人样本显示样本数及归一化四维数据|本次端到端及关联自动化通过|
|TC-17-B1|异常/边界|审核未到期 → 无教师样本|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|审核未到期 → 无教师样本|本次关联自动化通过|
|TC-17-B2|权限/状态|缺失/未知权重 → 不生成伪零分或错误完整雷达|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|缺失/未知权重 → 不生成伪零分或错误完整雷达|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#unknownWeightsAndMissingDimensionStayOutOfLowScores](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:80>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#explicitZeroIsCountedAndVersionsAreSeparate](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:81>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#managementCountsApprovedBeforeFeedbackButTeacherCannotSee](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:70>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/us17-radar.png

**讲解要点：**

- 雷达图100分尺度与模板原始维度权重不同
- 明确0分纳入，缺失不补零
- 教师工作台旧BOPPPS徽标有歧义，四维不等同BOPPPS


### US-19 按课程类别分析问题分布

角色：督导。原件表7第7行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.3:综合分析报表作为教学督导，我希望按课程类别查看常见问题分布，以便形成学期教研总结。

**验收标准：** 使用审核后的评价标签；可追溯分类规则与样本数；小样本不输出排名结论。

**页面操作：**

1. 统计与反馈 → 问题分布
2. 选择学期和课程类别 → 查询
3. 比较专业核心、公共选修与未分类分组
4. 展开查看数据表与来源明细
5. 核对标签、样本数和分类版本

**前置及输入：** 专业核心/公共选修/未分类；审核标签、未标注及其他学期。使用该角色授权数据，各套测试夹具独立。

![US-19关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-19-annotated.svg)

**实现链路：**

![US-19六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-19.svg)

**HTTP接口：**

- `GET /api/v1/sprint3/problems?term=&courseType=&uncategorized=`

**存储：** EntityManager → t_supervision_evaluation.problem_tags_json及课程course_type

**数据变化：** 只读聚合标签出现率；包含未标注样本与未分类课程，返回来源评价。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:58>)|function params|未分类使用独立uncategorized参数，避免混入全部类别|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:38>)|public Object problems(|问题分类统计API|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:90>)|public Map<String,Object> problems|只取审核后标签，按类别分组且保留样本和分类版本|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Policy.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Policy.java:9>)|PROBLEMS=|问题标签白名单；不是自由捏造调研结论|
|[backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/supervision/entity/SupervisionEvaluation.java:19>)|t_supervision_evaluation|持久化实体对应的实际表名|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-19-N|正常路径|专业核心/公共选修/未分类；审核标签、未标注及其他学期|依次执行本项页面步骤；核对结果并回查数据。|核心、选修、未分类分组正确；选择一类后仅剩该类|本次端到端及关联自动化通过|
|TC-19-B1|异常/边界|未审核、其他学期或越权评价 → 不计入|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|未审核、其他学期或越权评价 → 不计入|本次关联自动化通过|
|TC-19-B2|权限/状态|未标注评价仍进入适当样本口径|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|未标注评价仍进入适当样本口径|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#courseCategoriesDeduplicateAndUseAllSamplesAsDenominator](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:74>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#categoriesExcludeOtherTermsUnauthorizedMajorsAndPending](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:77>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/supervisor-categories.png

**讲解要点：**

- 类别来自课程，问题标签来自已审核评价
- 一个样本多标签不等于多个评价样本
- 小样本只描述分布，不输出排名或教学优劣结论


### US-18 教学质量报表导出

角色：主任。原件表7第6行。核心软件本轮验证通过；人工正式签收另行办理。

**需求原文：** 活动3.3:综合分析报表作为教研室主任，我希望导出教学质量汇总报表，以便支持教研分析。

**验收标准：** 导出字段与页面筛选一致；统计值可回查明细；教师评价用途与可见范围受权限控制。

**页面操作：**

1. 统计与反馈 → 质量汇总
2. 输入学期、课程类别、关键词及教师筛选 → 查询
3. 核对页面已生效筛选与来源评价ID
4. 下载Excel或下载CSV
5. 核对导出包含全部筛选结果，而非仅当前分页

**前置及输入：** 同筛选page=1,size=1，导出CSV/XLSX全量与评价ID。使用该角色授权数据，各套测试夹具独立。

![US-18关键页面：入口与结果标注](20261016-exp1-exp4-feature-trace-v1-images/shot-18-annotated.svg)

**实现链路：**

![US-18六层实现链路](20261016-exp1-exp4-feature-trace-v1-images/chain-18.svg)

**HTTP接口：**

- `GET /api/v1/sprint3/quality`
- `GET /api/v1/sprint3/quality/export?format=xlsx或csv`

**存储：** EntityManager / QualityReportWriter → 课程、班次、模板及评价；CSV/XLSX为只读导出

**数据变化：** 页面分页，导出全量已生效筛选；保留来源评价ID和模板分组。

|关键源码位置|方法/锚点|职责与要点|
|---|---|---|
|[frontend/src/components/Sprint3Workspace.vue](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/components/Sprint3Workspace.vue:87>)|function download|只使用已成功生效的筛选，未应用变更时禁用导出|
|[frontend/src/api/index.ts](</D:/2026Autumn Semester File/classroom-ai-demo/frontend/src/api/index.ts:756>)|sprint3Api|前端请求封装；身份令牌由统一client附加|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/Sprint3Controller.java:39>)|public ResponseEntity<byte[]> export|限制主任和格式，返回no-store下载响应|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityStatisticsService.java:121>)|public byte[] export|一致性快照内取得全量结果，调用QualityReportWriter|
|[backend/src/main/java/com/classroom/ai/modules/sprint3/QualityReportWriter.java](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/main/java/com/classroom/ai/modules/sprint3/QualityReportWriter.java:30>)|public static byte[] xlsx|导出汇总与明细，字段和内容可追溯|


**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。

|编号|场景|输入/条件|步骤|预期结果|实测关联|
|---|---|---|---|---|---|
|TC-18-N|正常路径|同筛选page=1,size=1，导出CSV/XLSX全量与评价ID|依次执行本项页面步骤；核对结果并回查数据。|相同筛选页面与Excel/CSV统计一致，记录可回查评价ID|本次端到端及关联自动化通过|
|TC-18-B1|异常/边界|分页为1条时导出仍包含全量筛选结果|构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。|分页为1条时导出仍包含全量筛选结果|本次关联自动化通过|
|TC-18-B2|权限/状态|未授权角色导出或非法格式 → 拒绝|切换授权或状态后重试；同时核对UI和HTTP/数据库结果。|未授权角色导出或非法格式 → 拒绝|本次关联自动化通过|


- 本次通过：[Sprint3MysqlTest#exportIncludesEntireFilterWhilePageIsBounded](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:94>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml
- 本次通过：[Sprint3MysqlTest#exportUsesRepeatableReadAcrossConcurrentEvaluationChange](</D:/2026Autumn Semester File/classroom-ai-demo/backend/src/test/java/com/classroom/ai/modules/sprint3/Sprint3MysqlTest.java:120>)；证据：docs/20261016-exp1-exp4-trace-evidence-v1/backend-full-xml/TEST-com.classroom.ai.modules.sprint3.Sprint3MysqlTest.xml

页面原始证据：docs/20261016-exp1-exp4-trace-evidence-v1/browser-final/sprint3-browser/director-quality.png

**讲解要点：**

- 原件未要求PDF，当前真实出口是CSV/XLSX
- 学期报表不自行包装成年度聚合
- 修改筛选但未查询时不能导出旧条件冒充新条件


## 测试结果与逐条验收覆盖

本轮前端23项通过并完成生产构建；后端重新打包。完整隔离数据库回归284项通过，失败/错误/跳过均为0；Sprint 2正式入口7套通过；Sprint 1真实浏览器覆盖3角色、5故事、10个子流程；Sprint 3真实业务8组通过。不同检查存在覆盖重叠，不能相加为功能数。

284项为整套后端防回归结果，包含少量本报告范围外检查；逐需求仅展开关联项。相同方法被多条需求引用不重复计数。

实际登记：`docs\20261016-exp1-exp4-trace-evidence-v1\executed-junit-tests.json`；逐验收点映射：`docs\20261016-exp1-exp4-trace-evidence-v1\requirements-coverage.json`。

|需求|原文验收点|本轮判断|
|---|---|---|
|US-01|编码唯一|本次软件检查通过|
|US-01|导入前预览|本次软件检查通过|
|US-01|缺项、重复与引用冲突返回错误清单|本次软件检查通过|
|US-01|确认后写入并记录操作者|本次软件检查通过|
|US-02|草稿与发布状态分开|本次软件检查通过|
|US-02|必填项完整|本次软件检查通过|
|US-02|发布人和更新时间可追溯|本次软件检查通过|
|US-03|支持多教师开课|本次软件检查通过|
|US-03|周次与节次展开正确|本次软件检查通过|
|US-03|以资料中的文管A447、95人课程为样例核对展示|本次软件检查通过|
|US-04|按学期过滤|本次软件检查通过|
|US-04|人数与累计人次分开标注|本次软件检查通过|
|US-04|查询结果与样例数据一致|本次软件检查通过|
|US-05|指标数量由导入的培养方案确定|本次软件检查通过|
|US-05|映射可编辑、保存版本并追溯，不硬编码12条|本次软件检查通过|
|US-06|单条件与组合条件均有测试|本次软件检查通过|
|US-06|越权不可查询|本次软件检查通过|
|US-06|记录测试数据量和响应时间，不承诺无条件300ms|本次软件检查通过|
|US-07|允许类型与大小限制可配置|本次软件检查通过|
|US-07|上传失败有提示|本次软件检查通过|
|US-07|资源绑定课程并校验访问权限|本次软件检查通过|
|US-08|多选标签可维护|本次软件检查通过|
|US-08|标签筛选结果正确|本次软件检查通过|
|US-08|无标签资源有明确显示|本次软件检查通过|
|US-09|已登录用户经权限校验后预览|本次软件检查通过|
|US-09|临时链接到期失效|本次软件检查通过|
|US-09|基础水印和访问日志随预览上线|本次软件检查通过|
|US-10|公开范围与角色权限共同生效|本次软件检查通过|
|US-10|搜索结果不得暴露未授权文件|本次软件检查通过|
|US-11|上传新版本保留旧版记录|本次软件检查通过|
|US-11|当前版本可识别|本次软件检查通过|
|US-11|基础鉴权与水印沿用US-09，不延后补安全控制|本次软件检查通过|
|US-12|校验课程、片段起止时间、标签和来源|本次软件检查通过|
|US-12|能按片段回看|本次软件检查通过|
|US-12|接口测试使用授权样例，不声称已实现自动切片|本次软件检查通过|
|US-13|教学态度、内容、方法、效果作为本版示例维度|本次软件检查通过|
|US-13|可配置权重|本次软件检查通过|
|US-13|支持暂存、必填校验和审核状态|本次软件检查通过|
|US-14|审核后按角色反馈并脱敏|本次软件检查通过|
|US-14|延迟时长可配置，演示默认24小时|本次软件检查通过|
|US-14|保留修改与审核日志|本次软件检查通过|
|US-15|明确分母为选定学期有效开课课程|本次软件检查通过|
|US-15|分子去重|本次软件检查通过|
|US-15|能由明细追溯覆盖率|本次软件检查通过|
|US-16|阈值可配置并注明来源|本次软件检查通过|
|US-16|零覆盖可识别|本次软件检查通过|
|US-16|预警需人工复核，不能直接作为考核结论|本次软件检查通过|
|US-17|只展示授权范围|本次软件检查通过|
|US-17|样本量与维度口径明确|本次软件检查通过|
|US-17|缺失项不当作零分|本次软件检查通过|
|US-17|不得把匿名性写成无条件保证|本次软件检查通过|
|US-18|导出字段与页面筛选一致|本次软件检查通过|
|US-18|统计值可回查明细|本次软件检查通过|
|US-18|教师评价用途与可见范围受权限控制|本次软件检查通过|
|US-19|使用审核后的评价标签|本次软件检查通过|
|US-19|可追溯分类规则与样本数|本次软件检查通过|
|US-19|小样本不输出排名结论|本次软件检查通过|
|US-20|权重和校验为100%|本次软件检查通过|
|US-20|模板版本可追溯|本次软件检查通过|
|US-20|新模板不改写历史评价记录|本次软件检查通过|
|US-21|接口接收建议、证据引用与教学环节标签|本次软件检查通过|
|US-21|教师可接受、调整并填写行动反馈|本次软件检查通过|
|US-21|BOPPPS作为可选标签，不等同四维评分|本次软件检查通过|



### 284项实际执行登记

|测试类|方法|结果|秒|
|---|---|---|---|
|ApiExceptionHandlerTest|malformedJsonUsesResponseEnvelope|本次通过|2.158|
|ApiExceptionHandlerTest|databaseConstraintErrorsDoNotExposeSqlOrReturnServerError|本次通过|0.048|
|ApiExceptionHandlerTest|invalidInputUsesJsonAndClientStatus|本次通过|0.018|
|CameraMonitorHttpTest|mismatchedFinishedOrFrozenSessionCannotReadFaces|本次通过|0.897|
|CameraMonitorHttpTest|allRolesGetOnlyCurrentRosterFacesAndRealCourseIdentity|本次通过|0.157|
|CameraMonitorHttpTest|anonymousAndForeignRequestsNeverReadFaceVectors|本次通过|0.096|
|CameraStreamSessionTest|finishedOrMismatchedSessionsHaveNoStreamSideEffects|本次通过|0.057|
|CameraStreamSessionTest|cacheAndDatabaseFailuresNeverReturnFalseSuccess|本次通过|0.219|
|CameraStreamSessionTest|nativeFrameSynchronizesAuthoritativeRosterMetricsToSession|本次通过|0.015|
|AttendanceHttpAuthorizationTest|foreignOfferingEndpointsAre403BeforeAnyWrite|本次通过|0.08|
|AttendanceHttpAuthorizationTest|deletedAccountOldTokenReceives401|本次通过|0.008|
|AttendanceHttpAuthorizationTest|explicitCurrentDoesNotReturnAnotherOfferingSession|本次通过|0.089|
|AttendanceHttpAuthorizationTest|spoofedBodyAndOldTokenRolesCannotOverrideCurrentAccount|本次通过|0.015|
|AttendanceHttpAuthorizationTest|profileUsesCurrentDatabaseIdentityRatherThanTokenClaims|本次通过|0.023|
|AttendanceHttpAuthorizationTest|anonymousAttendanceIs401|本次通过|0.016|
|AttendanceServiceTest|foreignOfferingCannotStartOrReadEvenWhenStudentCountExists|本次通过|0.112|
|AttendanceServiceTest|revokedSupervisorCannotUpdateOrFinishExistingSession|本次通过|0.009|
|AttendanceServiceTest|testStartAttendanceSessionBySupervisor|本次通过|0.015|
|AttendanceServiceTest|testFinishAttendanceSession|本次通过|0.003|
|AttendanceServiceTest|currentReturnsOlderVisibleSessionAndNeverOutsideOrFrozen|本次通过|0.008|
|AttendanceServiceTest|frozenOfferingRejectsUpdateAndFinishBeforeSessionMutation|本次通过|0.008|
|AttendanceServiceTest|frozenOfferingRejectsStartWithoutCreatingSession|本次通过|0.0|
|AttendanceServiceTest|testStartAttendanceSession|本次通过|0.008|
|AttendanceServiceTest|forgedFinishIdentityIsIgnored|本次通过|0.008|
|AttendanceServiceTest|forgedStartIdentityIsIgnored|本次通过|0.002|
|FaceHttpAuthorizationTest|anonymousHasNoFaceLibraryAccess|本次通过|0.003|
|FaceHttpAuthorizationTest|teacherAndSupervisorCannotReadRegisterSearchOrDeleteFaces|本次通过|0.016|
|FaceHttpAuthorizationTest|directorCanReadFaceLibrary|本次通过|0.01|
|AuthControllerTest|testRegisterSupervisor_PublicEndpointDisabled_Returns404|本次通过|0.098|
|AuthControllerTest|testGetMe_Unauthorized|本次通过|0.006|
|AuthControllerTest|testLogin_Success|本次通过|0.025|
|AuthControllerTest|testLogin_WrongPassword_Returns401|本次通过|0.007|
|AuthControllerTest|testGetMe_WithValidBearerToken_ReturnsUser|本次通过|0.008|
|DirectorSupervisorManagementTest|testCreateSupervisor_SuccessWithManagedMajor|本次通过|0.065|
|DirectorSupervisorManagementTest|testUpdateSupervisorMajors_Success|本次通过|0.0|
|DirectorSupervisorManagementTest|testCreateSupervisor_UnauthorizedMajor_Throws403|本次通过|0.008|
|DirectorSupervisorManagementTest|testAssignMajors_UnauthorizedMajor_Throws403|本次通过|0.003|
|JwtAccountIntegrityTest|deletedIdCannotInheritRecreatedSameNameAccount|本次通过|0.0|
|JwtAccountIntegrityTest|currentAccountRoleAndDepartmentOverrideOldClaims|本次通过|0.0|
|JwtAccountIntegrityTest|deletedAccountDoesNotAuthenticate|本次通过|0.0|
|JwtAccountIntegrityTest|legacyTokenWithoutIdStillReloadsExistingAccount|本次通过|0.008|
|JwtAccountIntegrityTest|mismatchedUsernameIsRejected|本次通过|0.0|
|AssociationIntegrityTest|selfAndMutualPrerequisitesAreRejected|本次通过|0.165|
|AssociationIntegrityTest|referencedCatalogIsImmutableButAnIdenticalReimportIsIdempotent|本次通过|0.015|
|AssociationIntegrityTest|associatedCourseCannotChangeMajorButUnusedCourseCan|本次通过|0.0|
|AssociationIntegrityTest|actualCrossDepartmentAssignmentGrantsCatalogReadOnly|本次通过|0.017|
|AssociationIntegrityTest|deletingAPrerequisiteCannotLeaveDanglingReferences|本次通过|0.005|
|AssociationIntegrityTest|noPrerequisiteMarkerIsEmptyAndNamesAreStoredAsStableCodes|本次通过|0.003|
|AssociationIntegrityTest|duplicateTargetSetsAreRejectedButDifferentGoalsRemainAllowed|本次通过|0.006|
|AssociationIntegrityTest|everyIndicatorWriteRejectsMismatchedCategoryBeforeSaving|本次通过|0.017|
|AssociationIntegrityTest|unchangedUnknownPrerequisitesDoNotBlockMetadataEditing|本次通过|0.0|
|AssociationIntegrityTest|editingIntoAnExistingMappingIsRejected|本次通过|0.009|
|AssociationIntegrityTest|existingOfferingCannotChangeCourse|本次通过|0.108|
|AssociationIntegrityTest|ambiguousPrerequisiteNamesRequireStableCodes|本次通过|0.006|
|AssociationIntegrityTest|omittedPlanIsPreservedAndRebindingNeedsCompleteMappings|本次通过|0.0|
|AssociationIntegrityTest|renamingAReferencedCourseNeedsStableCodeReferences|本次通过|0.006|
|AssociationIntegrityTest|currentAttendanceSkipsForeignSessionsAndSupportsExplicitOffering|本次通过|0.0|
|AssociationIntegrityTest|historyCannotBeEditedThroughWholeSyllabusSave|本次通过|0.008|
|AssociationIntegrityTest|attendanceRejectsForeignOfferingForAllReadAndWritePaths|本次通过|0.0|
|AssociationIntegrityTest|attendanceUsesServerIdentityAndRetainsAuthorizedSupervisorFlow|本次通过|0.009|
|AssociationIntegrityTest|bulkSaveRejectsDuplicateMappingsWithoutDeletingExistingRows|本次通过|0.0|
|AssociationIntegrityTest|frozenOfferingAttendanceCannotBeMutated|本次通过|0.0|
|AssociationMysqlTest|offeringCannotReassignExistingAttendanceToAnotherCourse|本次通过|0.314|
|AssociationMysqlTest|referencesProtectCatalogAndHistoryWhileNewVersionsStillWork|本次通过|0.211|
|AssociationMysqlTest|crossRoomTeacherReadsCatalogUsingActualAssignment|本次通过|0.054|
|AssociationMysqlTest|associationCountsProtectMajorEvenWhenOnlyAResourceExists|本次通过|0.085|
|AssociationMysqlTest|attendanceHasScopeServerIdentityAndFrozenHistoryProtection|本次通过|0.103|
|AssociationMysqlTest|prerequisiteCycleIsRejectedAndUnknownLegacyTextRemainsEditable|本次通过|0.082|
|AssociationMysqlTest|concurrentIdenticalMappingOnlySavesOneRow|本次通过|0.155|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.053|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.008|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.005|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.002|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.002|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.001|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.053|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.006|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.0|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.001|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.002|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.001|
|CourseAcceptanceRegressionTest|acceptanceRegressions|本次通过|0.002|
|CourseAuthorizationPrimaryTeacherTest|primaryTeacherCanSeeOfferingWithoutTeamRelationRow|本次通过|0.001|
|CourseAuthorizationServiceTest|testDirector_DepartmentIsolation|本次通过|0.001|
|CourseAuthorizationServiceTest|testSupervisor_MajorAuthorizationAndReadOnly|本次通过|0.003|
|CourseAuthorizationServiceTest|testUnauthenticated_Throws401|本次通过|0.004|
|CourseAuthorizationServiceTest|testTeacher_CourseBinding|本次通过|0.004|
|CourseBrowserMysqlTest|realBrowserWorkflow|本次通过|17.289|
|CourseContentRevisionTest|testPublish_MissingFields_Returns400|本次通过|0.011|
|CourseContentRevisionTest|testSaveDraft_VersionConflict_Returns409|本次通过|0.011|
|CourseContentRevisionTest|testSaveDraft_IncompleteAllowed|本次通过|0.006|
|CourseContentRevisionTest|testGetPublished_DraftEditingDoesNotPollutePublished|本次通过|0.01|
|CourseContentRevisionTest|testGetPublished_UnpublishedReturnsNull|本次通过|0.01|
|CourseContentRevisionTest|testPublish_Success_IncrementsPublishVersion|本次通过|0.005|
|CourseContentRevisionTest|testPublish_ForbiddenForNonRelatedTeacher|本次通过|0.01|
|CourseHttpAuthorizationTest|teacherCannotChangeOfferingsRosterOrSchedules|本次通过|0.009|
|CourseHttpAuthorizationTest|unauthorizedPublishedContentReturns403|本次通过|0.008|
|CourseHttpAuthorizationTest|supervisorCannotPreviewConfirmOrCreateCourse|本次通过|0.008|
|CourseHttpAuthorizationTest|supervisorCannotReadOrCreateUnpublishedDraft|本次通过|0.003|
|CourseHttpAuthorizationTest|teacherWithSameNameButNoAssociationCannotPublish|本次通过|0.0|
|CourseHttpAuthorizationTest|anonymousImportAndContentReadReturn401|本次通过|0.0|
|CourseHttpAuthorizationTest|authorizedPublishedContentRemainsReadable|本次通过|0.0|
|CourseImportTest|testPreviewImport_Success|本次通过|0.014|
|CourseImportTest|testConfirmImport_Success|本次通过|0.002|
|CourseImportTest|testPreviewImport_ValidationErrors|本次通过|0.008|
|CourseImportTest|testDownloadTemplate|本次通过|0.016|
|CourseOfferingHistoryTest|testTeacherRoleDataIsolation|本次通过|0.0|
|CourseOfferingHistoryTest|testHistoryStatistics_EnrollmentAndFrozenSnapshot|本次通过|0.0|
|CourseScheduleConflictTest|weeksAndPeriodsOverlapInclusively|本次通过|0.008|
|CourseScheduleConflictTest|sameOfferingDifferentClassroomStillConflicts|本次通过|0.0|
|CourseScheduleConflictTest|classroomConflictReturnsAllRowsAndReasons|本次通过|0.0|
|CourseScheduleConflictTest|semesterSeparationAndNamesAreNotTeacherIdentity|本次通过|0.0|
|CourseScheduleMultiTeacherConflictTest|collaboratorAcrossClassroomsBlocksAndIdentifiesTeacher|本次通过|0.0|
|CourseScheduleMultiTeacherConflictTest|semesterMoveAlsoChecksInternalOfferingOverlap|本次通过|0.0|
|CourseSchedulingMysqlTest|differentSemesterAndDisjointWeeksSucceed|本次通过|0.201|
|CourseSchedulingMysqlTest|everyNormalMutationRejectsArchivedOffering|本次通过|0.233|
|CourseSchedulingMysqlTest|changingSemesterRevalidatesAllSchedules|本次通过|0.244|
|CourseSchedulingMysqlTest|maintainsTeamAndNinetyFiveStudentsAndFiltersSample|本次通过|1.44|
|CourseSchedulingMysqlTest|semesterLockRemainsHeldUntilOuterTransactionCommits|本次通过|0.708|
|CourseSchedulingMysqlTest|concurrentRoomRequestsHaveExactlyOneWinner|本次通过|0.219|
|CourseSchedulingMysqlTest|failedArchiveRollsBackSnapshotAndStatus|本次通过|0.161|
|CourseSchedulingMysqlTest|concurrentTeacherRequestsAcrossRoomsHaveExactlyOneWinner|本次通过|0.19|
|CourseSchedulingMysqlTest|stableIdListsCountSameStudentAcrossOfferingsButNotTeachers|本次通过|0.212|
|CourseSchedulingMysqlTest|archiveIsIdempotentAndPreservesDisplayAndCountAfterOtherChanges|本次通过|0.274|
|CourseSchedulingMysqlTest|changingTeacherRevalidatesAndRollsBackWholeOffering|本次通过|0.253|
|CourseSchedulingMysqlTest|primaryTeacherCodeAuthorizesLegacyOfferingButNotUnrelatedTeacher|本次通过|0.186|
|CourseSchedulingMysqlTest|combinedSearchUsesIdsAndAndPermissionsWithCollaboratorDeduplication|本次通过|0.265|
|CourseSchedulingMysqlTest|concurrentArchiveAndRosterEditShareOfferingLock|本次通过|0.19|
|CourseSchedulingMysqlTest|sameOfferingAcrossRoomsBlockedAndEditingExcludesItself|本次通过|0.195|
|CourseSchedulingMysqlTest|concurrentOfferingChangeAndScheduleCannotIntroduceConflict|本次通过|0.204|
|CourseSchedulingMysqlTest|teacherCannotMaintainOfferingOrScheduleAndForgedEditRejected|本次通过|0.222|
|CourseSchedulingMysqlTest|archiveMigrationIsRepeatableAndPreservesUnknownLegacyData|本次通过|0.365|
|CourseSchedulingMysqlTest|emptyRosterDoesNotFallBackToStaleCountAndEmptyHistoryIsZero|本次通过|0.153|
|CourseSchedulingMysqlTest|allConflictsReturnHttp409WithReasonsAndCourseDetails|本次通过|0.23|
|CourseSchedulingMysqlTest|legacyTeacherDisplayFieldDoesNotGrantAccess|本次通过|0.178|
|CourseSupervisorRbacTest|testSupervisorQueryUnauthorizedMajor_ShouldThrowForbidden|本次通过|0.001|
|CourseSupervisorRbacTest|testSupervisorQueryOfferings_AutoFilterByAuthorizedMajors|本次通过|0.0|
|CourseSupervisorRbacTest|testSupervisorGetCourseById_UnauthorizedMajor_ShouldThrowForbidden|本次通过|0.0|
|CourseSupervisorRbacTest|testSupervisorGetCourseById_AuthorizedMajor_Success|本次通过|0.0|
|CourseWorkflowMysqlTest|concurrentDraftSavesDoNotLoseUpdates|本次通过|0.119|
|CourseWorkflowMysqlTest|validBatchImportsAllRowsAndRecordsOperator|本次通过|0.168|
|CourseWorkflowMysqlTest|archiveEditPreservesPublishedContentAndRejectsCodeOrScopeChange|本次通过|0.169|
|CourseWorkflowMysqlTest|deletedPrerequisiteRejectsConfirmation|本次通过|0.209|
|CourseWorkflowMysqlTest|completeDraftPublishAndReeditWorkflowRejectsStaleRequests|本次通过|0.268|
|CourseWorkflowMysqlTest|concurrentPublishHasOneWinnerAndOneConflict|本次通过|0.214|
|CourseWorkflowMysqlTest|legacyMigrationIsRepeatableAndPreservesAuditHistory|本次通过|0.561|
|CourseWorkflowMysqlTest|concurrentFirstReadsCreateOneDraft|本次通过|0.143|
|CourseWorkflowMysqlTest|deletedMajorRejectsConfirmation|本次通过|0.198|
|CourseWorkflowMysqlTest|concurrentBatchConfirmationWritesExactlyOnce|本次通过|0.143|
|CourseWorkflowMysqlTest|auditFailureRollsBackWholeBatchAndAllowsRetry|本次通过|0.202|
|MajorDepartmentMysqlTest|onlyLeadWritesSharedCatalogWhileParticipantsAndAuthorizedSupervisorsReadIt|本次通过|0.043|
|MajorDepartmentMysqlTest|participatingDepartmentCanBindCatalogAndKeepHistoricalMappings|本次通过|0.077|
|MajorDepartmentMysqlTest|sharedProfessionalScopeDoesNotGrantOtherDepartmentsCourses|本次通过|0.028|
|CourseImportServiceTest|testConfirmImport_RejectBatchWithErrors|本次通过|0.068|
|CourseImportServiceTest|testConfirmImport_DuplicateConsumption|本次通过|0.002|
|CourseImportServiceTest|testExportCourses|本次通过|0.008|
|CourseImportServiceTest|testPreviewImport_VariousValidationFailures|本次通过|0.012|
|CourseImportServiceTest|testPreviewImport_RejectTeachingQualityReport|本次通过|0.002|
|CourseImportServiceTest|testPreviewImport_FlexibleHeadersAndReorderedColumns|本次通过|0.003|
|CourseImportServiceTest|testPreviewImport_Success_WithBatchInternalPrerequisite|本次通过|0.002|
|CourseImportServiceTest|testConfirmImport_Success|本次通过|0.003|
|CourseImportServiceTest|testFileLimits|本次通过|0.008|
|CourseImportServiceTest|testDownloadTemplate|本次通过|0.003|
|CourseImportServiceTest|testConfirmImport_ForbiddenOperator|本次通过|0.002|
|CourseImportServiceTest|testConfirmImport_ExpiredBatch|本次通过|0.001|
|SyllabusHistoryHttpTest|versionOfAnotherCourseCannotBeReadThroughAnAuthorizedCourse|本次通过|0.008|
|SyllabusHistoryHttpTest|missingVersionIsRejected|本次通过|0.0|
|SyllabusHistoryHttpTest|unauthorizedCourseDoesNotExposeItsHistory|本次通过|0.0|
|SyllabusHistoryHttpTest|anonymousHistoryReadIsRejected|本次通过|0.0|
|SyllabusHistoryHttpTest|authorizedUsersReadTheRequestedSnapshot(RoleEnum)[1]|本次通过|0.016|
|SyllabusHistoryHttpTest|authorizedUsersReadTheRequestedSnapshot(RoleEnum)[2]|本次通过|0.008|
|SyllabusHistoryHttpTest|omittingVersionPreservesLatestQuery|本次通过|0.0|
|CsvParserUtilTest|testStandardCsv|本次通过|0.0|
|CsvParserUtilTest|testUtf8BomStripping|本次通过|0.0|
|CsvParserUtilTest|testQuotedCommas|本次通过|0.0|
|CsvParserUtilTest|testEscapedQuotes|本次通过|0.0|
|CsvParserUtilTest|testCrlfAndBlankLines|本次通过|0.011|
|CourseResourceServiceTest|testUploadSizeLimit_Over100MB_ShouldThrowException|本次通过|0.066|
|CourseResourceServiceTest|testUploadValidResource_ShouldGenerateWatermark|本次通过|0.0|
|MicroTeachingHttpAuthorizationTest|directorAndTeacherCanMountAndDeleteInScope|本次通过|0.026|
|MicroTeachingHttpAuthorizationTest|anonymousRequestsCannotReadMountOrDelete|本次通过|0.006|
|MicroTeachingHttpAuthorizationTest|stageQueriesUseOnlyReadableCourseIdsForEveryRole|本次通过|0.009|
|MicroTeachingHttpAuthorizationTest|directForeignCourseAndSliceIdsCannotBypassScope|本次通过|0.019|
|MicroTeachingHttpAuthorizationTest|threeRolesReadTheirAuthorizedCourse|本次通过|0.008|
|MicroTeachingHttpAuthorizationTest|supervisorCannotMountOrDeleteEvenAnAuthorizedCourse|本次通过|0.006|
|MicroTeachingHttpAuthorizationTest|crossDepartmentCollaboratorCanMaintainCourseButDirectorCannot|本次通过|0.004|
|MicroTeachingHttpAuthorizationTest|revokedAuthorizationAndMissingInputsNeverWrite|本次通过|0.012|
|MicroVideoHttpTest|supervisorCannotUploadAndForeignCourseCannotBeRead|本次通过|0.037|
|MicroVideoHttpTest|managedFilePathCannotBypassLoginAndCourseAuthorization|本次通过|0.006|
|MicroVideoHttpTest|validUploadUsesPrivateGeneratedPathAndDisguisedFilesAreRejected|本次通过|0.02|
|Sprint2MysqlTest|reviewDelayAndSemesterCoverage|本次通过|0.075|
|Sprint2MysqlTest|syllabusVersionsKeepTheirOwnMappings|本次通过|0.068|
|Sprint2MysqlTest|recommendedTemplateKeepsRealPlansSeparateAndSupportsMaintenance|本次通过|0.107|
|Sprint2MysqlTest|resourceVisibilityTagsAndExpiringPreview|本次通过|4.397|
|Sprint3BoundaryTest|invalidScoresRejected|本次通过|0.001|
|Sprint3BoundaryTest|exactDecimalWeights|本次通过|0.001|
|Sprint3BoundaryTest|missingAndZeroAreDifferent|本次通过|0.001|
|Sprint3BoundaryTest|auditAndFeedbackAreSeparate|本次通过|0.0|
|Sprint3BoundaryTest|analysisStructureAndLimits|本次通过|0.015|
|Sprint3BoundaryTest|sixStagesOnly|本次通过|0.0|
|Sprint3BoundaryTest|csvEscapingAndFormulaDefense|本次通过|0.002|
|Sprint3BoundaryTest|zeroWeightDoesNotRequireScore|本次通过|0.0|
|Sprint3BoundaryTest|decimalScoresUseAnExactDecimalSum|本次通过|0.0|
|Sprint3BoundaryTest|millisecondRangeBoundaries|本次通过|0.0|
|Sprint3BoundaryTest|forgedPublishedStateNeverOpensFeedback|本次通过|0.0|
|Sprint3BoundaryTest|staleVersionRejected|本次通过|0.0|
|Sprint3BoundaryTest|problemTagsDeduplicateAndRejectUnknown|本次通过|0.006|
|Sprint3BoundaryTest|xlsxHasTwoSafeWorksheets|本次通过|0.024|
|Sprint3BoundaryTest|assetSourcesAreMandatory|本次通过|0.0|
|Sprint3BoundaryTest|clientCannotWriteHistoryOrTeacherIdentity|本次通过|0.016|
|Sprint3MysqlTest|resourceCandidatesIncludeHistoryAndExcludeRevokedSeries|本次通过|0.365|
|Sprint3MysqlTest|evaluationConcurrentDraftEditHasOneWinner|本次通过|0.201|
|Sprint3MysqlTest|templateConcurrentPublishHasOneWinnerAndPublishedIsImmutable|本次通过|0.205|
|Sprint3MysqlTest|exportIncludesEntireFilterWhilePageIsBounded|本次通过|0.235|
|Sprint3MysqlTest|exportUsesRepeatableReadAcrossConcurrentEvaluationChange|本次通过|0.244|
|Sprint3MysqlTest|templateStaleEditRollsBack|本次通过|0.206|
|Sprint3MysqlTest|managementCountsApprovedBeforeFeedbackButTeacherCannotSee|本次通过|0.352|
|Sprint3MysqlTest|evidenceCandidatesRespectParentReleaseAndSameCourse|本次通过|0.287|
|Sprint3MysqlTest|publishedWithoutAuditIsNeverIncluded|本次通过|0.215|
|Sprint3MysqlTest|migrationIsRepeatableAndDoesNotInferLegacyWeights|本次通过|0.307|
|Sprint3MysqlTest|firstFeedbackRaceHasOneWinner|本次通过|0.218|
|Sprint3MysqlTest|unknownWeightsAndMissingDimensionStayOutOfLowScores|本次通过|0.363|
|Sprint3MysqlTest|coTeachersMaintainIndependentFeedbackRows|本次通过|0.315|
|Sprint3MysqlTest|revocationDuringPreviewGenerationIsRecheckedBeforeReturningBytes|本次通过|0.443|
|Sprint3MysqlTest|invalidProbeDoesNotSaveAssetAndUnavailableToolReturns503|本次通过|0.372|
|Sprint3MysqlTest|revocationClosesOldIdAndReadsFreshEpoch|本次通过|0.376|
|Sprint3MysqlTest|adminInitializationIsRepeatableAndNeverPromotesExistingUser|本次通过|0.233|
|Sprint3MysqlTest|courseCategoriesDeduplicateAndUseAllSamplesAsDenominator|本次通过|0.451|
|Sprint3MysqlTest|multipleAdviceAndFeedbackHistoryCannotBeOverwritten|本次通过|0.348|
|Sprint3MysqlTest|explicitZeroIsCountedAndVersionsAreSeparate|本次通过|0.352|
|Sprint3MysqlTest|resourceConcurrentUploadsAndFixedHistoricalIds|本次通过|0.296|
|Sprint3MysqlTest|cachedPreviewAndOldTicketsAreRejectedAfterRevocation|本次通过|0.528|
|Sprint3MysqlTest|administratorCannotReachAnyGenericOrFaceEntry|本次通过|0.28|
|Sprint3MysqlTest|stableIdentityAndCoTeacherPermissions|本次通过|0.172|
|Sprint3MysqlTest|unpublishedAndForgedWeightsCannotBeSubmitted|本次通过|0.155|
|Sprint3MysqlTest|lowScoreThresholdUsesUnroundedMean|本次通过|0.245|
|Sprint3MysqlTest|changedVideoHashBlocksPlaybackAndReplacementDoesNotMoveOldInterval|本次通过|0.309|
|Sprint3MysqlTest|evidenceMustBeSameCourseAndAccessible|本次通过|0.22|
|Sprint3MysqlTest|databaseRejectsCrossSeriesPointerAndDuplicateVersion|本次通过|0.226|
|Sprint3MysqlTest|categoriesExcludeOtherTermsUnauthorizedMajorsAndPending|本次通过|0.312|
|Sprint3MysqlTest|realVideoDurationAndAllSixStagesAreVerifiedOnServer|本次通过|0.361|
|SupervisionAnalyticsTest|testDashboardMetrics|本次通过|0.061|
|SupervisionAnalyticsTest|testAlertRules_YellowAndRed|本次通过|0.0|
|SupervisionAnalyticsTest|testAnnualReportExportCsv|本次通过|0.0|
|SupervisionServiceTest|incompleteDraftCanStillBeSaved|本次通过|0.007|
|SupervisionServiceTest|testEvaluationScoreOutOfRange_ShouldThrowException|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRejectsMissingScores(String)[1]|本次通过|0.003|
|SupervisionServiceTest|formalSubmissionRejectsMissingScores(String)[2]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRejectsMissingScores(String)[3]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRejectsMissingScores(String)[4]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRejectsMissingScores(String)[5]|本次通过|0.0|
|SupervisionServiceTest|explicitZeroScoresCanBeSubmitted|本次通过|0.0|
|SupervisionServiceTest|testEvaluationSubmit_ShouldEnterDesensitizationPeriod|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRequiresThreeDistinctNonemptyHighlights(String)[1]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRequiresThreeDistinctNonemptyHighlights(String)[2]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRequiresThreeDistinctNonemptyHighlights(String)[3]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRequiresThreeDistinctNonemptyHighlights(String)[4]|本次通过|0.0|
|SupervisionServiceTest|formalSubmissionRequiresThreeDistinctNonemptyHighlights(String)[5]|本次通过|0.0|
|SupervisionServiceTest|testEvaluationDraft_ShouldBeDraftStatus|本次通过|0.0|
|RegressionTest|reversedScheduleRangesAreRejected|本次通过|0.007|
|RegressionTest|commentsOver500CharactersAndNanScoresAreRejected|本次通过|0.001|
|RegressionTest|negativeFileSizeIsRejected|本次通过|0.0|
|RegressionTest|partialCacheFallsBackToCompleteDatabaseWithoutDuplicates|本次通过|0.225|
|RegressionTest|rejectsInvalidSimilarityThreshold|本次通过|0.002|
|RegressionTest|lockedSyllabusCannotBeEdited|本次通过|0.008|
|RegressionTest|unpublishedEvaluationDoesNotLeakIntoRadar|本次通过|0.009|
|RegressionTest|rejectsWrongDimensionBeforeWriting|本次通过|0.0|
|RegressionTest|teacherSeesPublishedAnonymousCopiesOnly|本次通过|0.0|
|RegressionTest|emptyPoseFrameClearsOldPoseAndDeduplicatesAttendance|本次通过|0.009|
|RegressionTest|rejectsPercentageWhereStreamRequiresFraction|本次通过|0.0|
|RegressionTest|zeroExpectedAttendanceDoesNotDivideByOneOrFailOnNull|本次通过|0.007|
|RegressionTest|defaultWeeksHaveValidHumanReadableLabel|本次通过|0.0|
|RegressionTest|csvEscapesCommasQuotesAndKeepsDecimalDotInOtherLocales|本次通过|0.0|
|RegressionTest|rejectsZeroNanAndNullVectors|本次通过|0.008|
|RegressionTest|finishedAttendanceCannotBeOverwritten|本次通过|0.002|
|RegressionTest|emptyRadarDoesNotInventScoresOrFeedback|本次通过|0.001|
|RegressionTest|negativeAttendanceIsRejected|本次通过|0.002|
|UploadPathsTest|rootAndBackendLaunchesUseTheSameSharedPictures|本次通过|0.004|
|UploadPathsTest|explicitAbsoluteAndRelativeUploadLocationsAreRespected|本次通过|0.005|
|VisualHttpAuthorizationTest|anonymousRequestsAreRejectedBeforeReadingOrWritingCaches|本次通过|0.008|
|VisualHttpAuthorizationTest|frozenOfferingRejectsStreamAndResetForEveryRoleBeforeAnySideEffect|本次通过|0.016|
|VisualHttpAuthorizationTest|emptyAuthorizedRosterDoesNotUseStudentsFromOtherClasses|本次通过|0.01|
|VisualHttpAuthorizationTest|allThreeRolesCannotBypassOfferingScopeThroughAnyRealtimeEndpoint|本次通过|0.035|
|VisualHttpAuthorizationTest|missingOfferingNeverFallsBackToGlobalRosterOrActiveClass|本次通过|0.006|



## 问题、边界与讲解提醒

|事项|复核说明|
|---|---|
|展示口径|TeacherDeskView.vue中的“BOPPPS 教学模型”和“BOPPPS 进阶教改建议”容易让人把四维评分等同BOPPPS；四维实际为教学态度、内容、方法、效果。当前仅记录，未修改业务代码。|
|上传配置提示|教师端setResourceFile仍固定以100MB作本地预检查，真实上传由服务端可配置策略决定。非默认PDF-only/1024字节实测生效；演示其他配置时应以服务器错误信息为准。|
|范围与格式|US-18原件要求质量报表导出，没有指定PDF；当前出口为CSV/XLSX。US-12是授权片段和元数据接口，不证明自动切片或自动课堂分析。|
|运行实例|源码基线不证明日常8080/5173实例已部署同版。测试期间观察到原有三个日常容器停止；本轮没有向其发送启停、重建或数据库写入命令，停止原因未由本轮证据确定。|
|补充脚本重试|补充截图第一次被未保存修改离开保护阻止；第二次错误等待了教师反馈页不展示的听课主题。均为采集脚本问题，诊断保留在过程目录；修正后新隔离环境完成，业务代码未改。|



## 文件与交付核验

交付检查通过：298个受保护文件指纹及HEAD保持不变，原始资料和业务源码未改写；专用测试端口无监听，本轮临时容器已清理。报告9组离线检查通过，0外部HTTP请求、0页面脚本错误；390px宽度无整页横向溢出。实际下载的XLSX包含2个工作表，CSV/XLSX均包含同筛选下3门合成课程。

21条需求具备本轮核心软件验证证据；BOPPPS展示口径、现场设备能力、真实业务资料和自然人正式签收按前述边界复核。未修复业务代码、未部署或提交推送。


## 来源索引与复测入口

引用日期2026-10-16。未使用外部联网业务资料。源码定位链接依赖当前工作区；HTML正文与图片可离线独立阅读。

|来源|文件或位置|用途|
|---|---|---|
|实验一正式项目规划|D:\2026Autumn Semester File\软管\实验一\output\实验一最终提交物\第二组_项目规划方案.docx|表5/6/7用户故事及表16完成定义；只读核对，原件SHA256 299cd2d510e7a8da609ec2fe513e3810e616d3562673bce09ee16e81688437bb|
|当前实现|D:\2026Autumn Semester File\classroom-ai-demo|exp4 / f5a8bccf6fe19155263bef87599419a1f5658020；精确源码行号见每项需求|
|本轮隔离证据|D:\2026Autumn Semester File\classroom-ai-demo\docs\20261016-exp1-exp4-trace-evidence-v1|本轮构建、JUnit XML、三个Sprint及补充浏览器结果；历史记录仅参考|
|规范与长期口径|D:\2026Autumn Semester File\classroom-ai-demo\AGENTS.md|目录规则；memory-bank中已确认的合成培养方案、授权与正式签收边界|
|技能|officecli-docx；ui-ux-pro-max|原件只读解析；离线图文报告可读性、导航和对比度。minimax-docx当前未安装。|


复测：`npm.cmd --prefix frontend test`；`npm.cmd --prefix frontend run build`；本轮隔离脚本及执行日志见过程证据目录。必须使用新的证据目录，避免覆盖本轮结果。
