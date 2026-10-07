# 三角色功能实机问题修复与复测

日期：2026-10-07。项目：classroom-ai-demo。

本轮已修复上一轮发现的四类问题，并更新本机 8080 后端。实际使用 Google Chrome + Playwright、当前 Vue 构建、Spring Boot、隔离 MySQL/Redis 和 LibreOffice 验证，没有用模拟接口替代业务后端。原始资料及前序修复保持完整，未提交或推送 Git。

## 修复结果

| 问题 | 修复后的行为 | 实测结果 |
|---|---|---|
| 实时大屏读取、推流、重置可绕过班次权限 | 概览、趋势、学生状态、推流、重置均沿用课程统一授权；必须明确传 `offeringId`。每个班次使用独立 Redis 缓存 | 未授权三角色均 403；缺少班次 400；越权请求不写入记录。两个同名班次的概览、趋势、姿态和重置互不影响 |
| 冻结班次仍能模拟推流 | 后端写入与归档使用同一班次行锁，刷新后校验冻结状态；前端禁用冻结班次写操作，启动失败或运行中写入被拒绝即停止 | 冻结写入 409；三角色界面只读；旧页面启动失败后零推流；运行中归档后停止；真实 MySQL 并发冻结后请求被拒绝且无新增记录 |
| 教师、督导获得全校候选名单，界面显示编辑入口 | 两角色只返回授权班次已选学生，`available: []`；隐藏选入、移出按钮。主任保留所属班级的名单维护，冻结名单只读 | 教师与督导 API 和页面均只读；主任选入/移出及人数同步通过 |
| 原件下载返回 JSON MIME | 按文件扩展名设置实际媒体类型，未知类型使用二进制类型，保留附件及原有下载权限 | PDF、DOCX、PPTX 类型正确，下载字节与上传字节完全一致；教师本人及主任可下载，共享教师和督导仍 403 |

同时修正空名单人数回退到全校人数的问题；旁听识别编号不再用于反查其他班级学生姓名、头像。旧版 `DashboardView` 复用当前考勤组件，避免保留无班次参数的另一条调用路径。模拟会话、名单均成功后才启动定时器，切班或离开页面使尚未完成的启动请求失效。

所有实时 API 调用方均需携带有效登录认证及明确的 `offeringId`；不再根据班名或全局活动会话猜测归属。原生摄像头视觉进程的认证和班次上下文接入、现场识别效果未在本轮验收。

下载响应因现有全局 UTF-8 配置可能包含 `charset=UTF-8` 参数；验收核对实际媒体类型及原始字节，不将参数本身视为内容损坏。

## 验证结果与证据

| 验证组 | 结果 | 证据 |
|---|---|---|
| 缺陷与边界实机验收 | 13/13 通过，浏览器无未捕获异常 | `docs/20261007-role-fixes-evidence-v1/fix-results.json` |
| 完整三角色页面业务 | 23/23 通过，21 张截图，无未捕获异常 | 同目录 `all-features-proxy/results.json` |
| Office 文件及业务守卫 | 6/6 通过；真实转换、PDF 渲染、翻页、共享撤销、超限/损坏文件拦截、模板锁定、评价校验 | 同目录 `office-strict/fix-acceptance-results.json` |
| Java 回归 | 149 通过，47 条依赖独立环境的用例条件跳过，零失败/错误；其中新增实时 HTTP 权限回归 5/5 | 同目录 `java-test-summary.json`、`maven-final.log` |
| 前端 | 类型检查与生产构建通过，10/10 测试通过 | 同目录 `frontend-build-final.log`、`frontend-tests.log` |
| 本机部署后真实接口 | 三角色按当前账号授权复核通过；教师、督导候选名单均为零 | 同目录 `daily-readonly-results.json` |

上述分组有覆盖交叉，不相加为独立测试总数。Java 条件跳过的 47 条没有当作通过；本轮另有直接使用真实 MySQL 的并发冻结验证。

本机督导账号的实际授权与隔离种子不同：本机现有班次 10 的课程详情和实时接口均允许读取。部署复核以当前真实授权为准；隔离环境中三种角色的越权拒绝均已验证。

首轮测试过程保留在 `attempt1/`：名单弹窗断言等待不足，以及下载断言误将合法 MIME 参数视为失败，均修正测试后重跑通过。Java 首次回归还发现前序认证改造遗留的测试夹具未安装 JWT 过滤器，已补齐真实过滤器及当前账号数据，未放宽认证规则。

## 本机更新与数据保护

- 已构建新后端镜像并重新创建 `classroom-backend`，保留原 MySQL、Redis 和上传目录。未执行测试数据导入或业务写操作到本机数据库。
- 更新前后 25 张业务表的记录数和校验和一致；已部署 JAR 的 SHA-256 与实机测试 JAR 一致。详见 `deployment.json`、`daily-business-before.json`、`daily-business-after.json`、`summary.json`。
- 回滚镜像：`classroom-ai-demo-classroom-backend:before-role-fixes-20261007-190356`。仅保留镜像，不自动恢复数据库。
- 测试专用 MySQL、Redis、两个后端、网络及前端预览进程已清理；本机三个原有服务保留。详见 `cleanup.json`。
- `raw/README.md` 与上一轮原始资料哈希一致，两张用户原图、旧报告、前序证据未改写。证据不保存登录令牌、数据库密码或生成的 Spring 测试密码。

## 修改文件

生产代码共 8 个文件（均位于项目根目录下）：

- `backend/src/main/java/com/classroom/ai/service/impl/VisualDashboardServiceImpl.java`
- `backend/src/main/java/com/classroom/ai/service/VisualDashboardService.java`
- `backend/src/main/java/com/classroom/ai/controller/VisualDashboardController.java`
- `backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseServiceImpl.java`
- `backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java`
- `frontend/src/api/index.ts`
- `frontend/src/views/AttendanceDashboardView.vue`
- `frontend/src/views/DashboardView.vue`

新增 `backend/src/test/java/com/classroom/ai/VisualHttpAuthorizationTest.java`；更新 `RegressionTest.java` 与 `modules/auth/AuthControllerTest.java` 的相关测试。过程更新写入 `docs/source-notes.md`、`docs/analysis-notes.md`、`docs/plan.md`、`docs/questions.md`；新增证据目录 `docs/20261007-role-fixes-evidence-v1/`。具体源文件哈希及相对上一轮验收的改动清单保存在该目录。

## 资料来源及需复核内容

来源为用户提供的两张图 `codex-clipboard-5560e7d6-5b89-4e59-a3c9-45b20c03b4be.png`（权限矩阵）、`codex-clipboard-c6d5a7e1-cb14-4b78-aca5-2f20b9bf4cb7.png`（业务关联图），以及上一轮 `output/20261007-role-integration-check-v1.md` 和对应实机证据。源码、当前账号授权和实际接口结果用于判断；未使用外部资料。

四类软件缺陷的修复和复测已完成。图中明确标注的质量 CSV 全量、主任学生底库全库、微格切片缺少课程范围校验仍保留现有规则，需业务复核。现场摄像头/GPU、人脸识别效果、微格视频播放与实际墙钟等待 24 小时未测，不宣称这些环节已通过本轮验收。
