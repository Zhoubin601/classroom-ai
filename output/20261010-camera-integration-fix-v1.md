# 摄像头与微格全链路修复及实机验收

日期：2026-10-10。项目：爱教学。工作区：`D:/2026Autumn Semester File/classroom-ai-demo`，分支 `fix-exp3`。本次修改保留在工作区，未提交或推送 Git。

## 结论

本次已确认范围已打通：真实摄像头画面、人脸与姿态识别、所选班次考勤、实时大屏、停止及归档、真实微格视频上传播放。日常服务已更新，可访问 [本机平台](http://127.0.0.1:5173)，后端为 `http://127.0.0.1:8080`。

真实摄像头单人验收：应到1人、实到1人、出勤率100%；连续成功上报4帧，生成4个趋势点并写入4条对应MySQL记录，归档状态为FINISHED。此结果验证软件链路及本次现场身份匹配，不代表多人、遮挡或不同光照条件下的总体识别准确率。

## 修复内容与源码

| 范围 | 修复行为 | 主要文件（相对于项目目录） |
| --- | --- | --- |
| 认证与班次绑定 | 本地启动前验证登录、角色、班次与活动考勤会话；原生请求携带认证、offeringId和attendanceSessionId | `frontend/dev/camera-plugin.mjs`、`vision/monitor_transport.py` |
| 人脸底库 | 提供活动班次上下文，只读取所选班次已选学生的512维特征；教师及督导不需要全校人脸库权限 | `backend/src/main/java/com/classroom/ai/controller/CameraMonitorController.java` |
| 实时考勤 | 后端按真实选课名单去重计数；未匹配人脸单独统计，不计入本班出勤；同步活动会话、Redis及MySQL | `backend/src/main/java/com/classroom/ai/dto/ClassroomStreamDTO.java`、`backend/src/main/java/com/classroom/ai/service/impl/VisualDashboardServiceImpl.java`、`vision/classroom_monitor.py` |
| 状态与停机 | 画面就绪和成功上报分开判断；连接错误可见；切班、离开页面、退出登录及冻结后停止原生进程；内部视频控制需授权 | `frontend/src/api/index.ts`、`frontend/src/api/types.ts`、`frontend/src/views/AttendanceDashboardView.vue`、`frontend/src/App.vue`、`frontend/vite.config.ts` |
| 归档 | 摄像头停止并等待在途上报后，使用服务端最后接收的考勤值归档；最后一帧为0时保留0 | `frontend/src/views/AttendanceDashboardView.vue` |
| 微格视频 | 新增课程视频入口，支持MP4/WebM上传、授权读取和播放器；教师/主任按课程维护，督导只读；静态文件路径不可绕过鉴权 | `frontend/src/views/MicroTeachingView.vue`、`backend/src/main/java/com/classroom/ai/modules/resource/controller/MicroTeachingVideoController.java`、`backend/src/main/java/com/classroom/ai/modules/resource/service/MicroTeachingVideoService.java`、`backend/src/main/java/com/classroom/ai/modules/auth/security/SecurityConfig.java` |
| 微格原有权限 | 保留并验证工作区已有的课程范围修复；禁止把平台受控文件路径再次挂载到其他课程 | `backend/src/main/java/com/classroom/ai/modules/resource/service/impl/MicroTeachingServiceImpl.java`、`backend/src/main/java/com/classroom/ai/modules/resource/repository/MicroTeachingSliceRepository.java` |

新增/调整测试包括 `CameraMonitorHttpTest.java`、`CameraStreamSessionTest.java`、`MicroVideoHttpTest.java`、`frontend/tests/camera-plugin.test.mjs`、`vision/tests/test_monitor_transport.py`、`vision/tests/test_edge_regressions.py`。

## 测试结果

环境：真实Chrome 154.0.8037.99、Playwright 1.61.1-beta、真实Vite页面、Spring Boot、独立MySQL/Redis、LibreOffice及本机摄像头Index 0。浏览器业务接口未mock，摄像头未使用fake device。

| 测试组 | 最终结果 |
| --- | --- |
| 课程、排课、发布、授权、评价、统计等功能回归 | 23/23通过 |
| Office资源实际转换与渲染、大纲和评价约束 | 6/6通过 |
| 三角色范围与业务边界 | 27/27通过 |
| 考勤范围、冻结、并发、模拟及归档 | 13/13通过 |
| 微格课程范围专项 | 11/11通过 |
| 摄像头与真实微格播放专项 | 14/14通过 |
| 连续摄像头帧与趋势专项 | 通过：4帧、4趋势点、4数据库记录 |
| 后端自动化 | 223项：176通过，47条件跳过，0失败 |
| 前端自动化 | 13/13通过 |
| Python视觉/传输自动化 | 9/9通过 |
| 更新后的日常实例 | 三角色登录、课程/班次读取、微格入口、大屏及认证保护通过 |

各组有交叉，不相加作为独立用例总数。跳过项不计为通过。

实机专项另验证：三角色真实Python传输均可更新获授权班次；匿名401、越权403、缺上下文400、结束/冻结409；停止后数据库不再增长且旧视频授权失效；原生内部控制未授权403；切班及退出登录释放摄像头；冻结后显示错误并停止；归档保留最终零值。

微格使用浏览器MediaRecorder生成有实际画面帧的合成WebM。教师完成真实上传、列表读取、授权媒体获取、解码和播放；播放器时间推进超过0.6秒，解码尺寸320×180，读取画面像素验证非空。督导实际播放同一视频；越权、匿名、直接文件路径及督导删除均被拒绝。没有录制或上传真人课堂视频。

早期测试尝试保留在docs：新Mockito桩设定、测试夹具漏填协同教师空名单、取证查询表名错误、合成视频缺少帧，以及预览期间重新构建导致页面重载失败均已修正或复测。最终14/14实机结果和13/13稳定构建回归作为验收依据。

## 日常服务更新与数据保护

只重建并重启 `classroom-backend`，启动本机Vite 5173；保留原 `classroom-mysql` 和 `classroom-redis` 容器及其数据卷。临时测试数据库、容器、网络和摄像头进程已清理。现场测试人的特征只登记在隔离数据库，未写入日常业务库；含摄像头画面的截图仅保存在本地docs。

部署JAR SHA256：`e62f0c0010c02c3d6ecbe4937511656605e88e177bf7c187e30a353a95e1a369`，与实测JAR一致。

| 日常业务表 | 更新前 | 更新后 |
| --- | ---: | ---: |
| 课程 | 24 | 24 |
| 班次 | 18 | 18 |
| 学生 | 80 | 80 |
| 人脸档案 | 80 | 80 |
| 考勤会话 | 7 | 7 |
| 微格记录 | 2 | 2 |

日常冒烟未写入业务测试夹具。未修改raw原始资料；测试凭据及人脸向量未作为交付资料保存。

## 证据位置

过程及证据目录：`docs/20261010-camera-integration-evidence-v1/`。

- `summary.json`：各组结果汇总。
- `camera-micro/results.json`：14项实机结果。
- `camera-micro/real-camera-connected.png`：真人匹配、出勤与姿态大屏，含授权保存的本地摄像头画面。
- `camera-trend/results.json`、`camera-trend/continuous-real-camera-trend.png`：连续真实帧、趋势和持久化证明。
- `camera-micro/teacher-real-video-playback.png`、`camera-micro/supervisor-real-video-playback.png`：真实解码播放证明。
- `camera-micro/synthetic-micro.webm`：无真人的合成视频测试素材。
- `fix-results.json`、`micro-scope/browser-results.json`：考勤及微格权限回归。
- `daily-before.json`、`daily-after.json`、`daily-smoke.json`：部署指纹、业务表数量及三角色冒烟。
- `cleanup.json`：临时环境清理结果。

## 使用与复核

登录后进入“课堂智能考勤大屏”，选择有权限、未冻结的班次，再点击“打开摄像头开启考勤”；页面会等待真实成功上报后显示“摄像头与考勤数据已连接”。只有当前班次名单中且已建档的人脸会计入本班出勤。结束后点击“结束考勤并归档下课”，可在考勤归档记录中核对。

“微格教学视频”入口支持教师/主任上传有效MP4/WebM；督导按授权课程预览。历史示例切片若引用不存在或不可访问的视频，仍需提供有效文件或播放地址。本次未自动替换原有微格记录；原始真实课堂视频资料未提供。平台视频删除移除课程记录，原上传文件的磁盘回收尚未实现。

需用户复核：实际班次名单与人脸建档是否一致、现场多人和光照条件下的识别效果、原有微格切片地址是否有效。该现场准确率评估及原视频补充未由单人和合成视频测试代替。

## 资料来源

- 用户附图：`C:/Users/a3185/Desktop/1.png`、`C:/Users/a3185/Desktop/2.png`，用于功能与权限范围对照。
- 用户实机截图：`C:/Users/a3185/AppData/Local/Temp/codex-clipboard-70abc0f6-1d3e-43f2-9287-c816c3cf3bd9.png`，用于区分摄像头画面已显示和后端401。
- 当前会话确认：全链路范围、实际启用摄像头及只保存本地截图、验证后更新5173/8080。
- 当前工作区源码、docs历史检查记录及memory-bank已确认的Demo权限口径。raw除目录说明外资料未提供。本轮未使用外部网络资料。
- 使用 `ui-ux-pro-max` 技能指导连接状态、可见错误及恢复反馈；文件：`C:/Users/a3185/.agents/skills/ui-ux-pro-max/SKILL.md`。

引用日期：2026-10-10。
