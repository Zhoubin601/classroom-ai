# Demo范围确认与微格权限修复

日期：2026-10-09。工作分支：fix-exp3，基于fa387b8，本轮修改尚未提交或推送。

用户已确认：Demo数据量较小，主任质量CSV继续全量导出，主任学生底库继续全量访问。这两项不再属于待业务复核事项。教师与督导仍不能使用这两个主任专用接口。

用户确认微格切片采用三角色与课程范围限制。此前三角色工作台已隔离，但微格接口仅要求登录，查询、挂载、删除没有校验课程归属；本轮已经补齐。

| 角色 | 微格切片读取范围 | 挂载、删除 |
| --- | --- | --- |
| 教研室主任 | 本教研室课程 | 本教研室课程 |
| 任课教师 | 本人主讲或协同授课课程 | 本人主讲或协同授课课程 |
| 教学督导 | 授权专业课程 | 禁止 |

课程读取与写入复用CourseAuthorizationService。按课程读取先校验授权；按教学环节读取在数据库查询中限制为可读课程ID；删除按实际切片所属课程校验，不能借请求参数伪造归属。授权被收回后，旧登录令牌按服务端最新权限执行。

## 实测结果

- 新增真实HTTP鉴权链回归：8/8通过，使用实际SecurityFilterChain、JWT过滤器、授权服务和微格业务服务，仓储为测试替身。
- Chrome/Playwright独立库专项：11/11通过。浏览器实际登录并通过fetch访问真实Vite代理、Spring Boot、MySQL；没有模拟HTTP响应。微格尚无独立操作页面，本轮是浏览器中的真实接口验收。
- Java全部回归及构建：214项中167通过、47条件跳过，零失败、零错误；跳过不算通过。
- 当前Vue类型检查和生产构建通过；浏览器无未捕获JavaScript异常。

本轮浏览器实际验证：匿名四类接口401；授权课程读取、主任与教师挂载删除成功；三角色直接访问无权限课程及切片ID403且数据库记录不变；督导即使有课程读取权限也不能挂载、删除；按教学环节检索不暴露范围外元数据；跨教研室协同教师维护成功；收回专业授权后旧登录立即拒读；Demo全量CSV和学生底库保持有效。

## 修改文件与证据

业务代码：

- backend/src/main/java/com/classroom/ai/modules/resource/service/impl/MicroTeachingServiceImpl.java
- backend/src/main/java/com/classroom/ai/modules/resource/repository/MicroTeachingSliceRepository.java

测试：新增backend/src/test/java/com/classroom/ai/modules/resource/MicroTeachingHttpAuthorizationTest.java、scripts/tests/micro-scope-browser.cjs；更新scripts/tests/role-connectivity-browser.cjs，将旧微格范围例外断言改为授权范围和督导只读断言，将前两项明确标记为用户确认的Demo规则。

过程记录：docs/source-notes.md、docs/analysis-notes.md、docs/questions.md、docs/plan.md；长期确认口径记录在memory-bank/project-context.md。本轮日志、JSON、三张实际登录截图及运行/清理脚本位于docs/20261009-micro-scope-evidence-v1/，总索引summary.json。

## 来源、保护与未完成项

来源名称：用户当前明确确认；文件名：当前会话；引用日期：2026-10-09。来源名称：本地fix-exp3源码及本轮运行证据；文件名：上述业务代码、测试、docs/20261009-micro-scope-evidence-v1/summary.json；引用日期：2026-10-09。

raw/只有README.md，没有新增正式业务原始资料，未使用外部资料。raw/README.md哈希保持9FFF72BCEC428D473107FC01F04E5204D0FA60C45C301D3DF68E10E201059269。仅向独立测试库写入合成数据，临时服务、网络与前端进程已清理；没有部署日常实例或写入日常业务库。旧报告与旧证据保留作为当时状态记录。

本轮三项业务口径已确认、微格元数据权限整改及复测完成，没有继续待确认的范围政策。摄像头原生401整改、现场识别与真实微格视频播放仍未完成；本轮没有实际播放视频，外部sliceUrl的媒体访问控制不在本次元数据接口验证范围内。修改当前仅在本地fix-exp3工作区。
