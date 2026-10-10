# AttendanceAccessService 测试导入缺陷复核

复核日期：2026-10-10。分支：`fix-exp3`，提交：`9f26625fbfad196decabd0998313e7a0fa4c45b5`。

结论：缺陷存在，已在真实 MySQL 8.0.36 上复现，并通过仅修改临时副本配置验证原因。

## 原因与位置

`backend/src/test/java/com/classroom/ai/modules/course/AssociationMysqlTest.java` 第35–36行采用 `@DataJpaTest` 切片测试，`@Import` 注册了 `AttendanceServiceImpl`，但没有注册其必需依赖 `AttendanceAccessService`。该依赖来自 `backend/src/main/java/com/classroom/ai/modules/attendance/service/impl/AttendanceServiceImpl.java` 第40行的 `@Autowired` 构造函数。

这里缺少的是 Spring Bean 的 `@Import` 配置，仅添加 Java 文件顶部的 `import` 声明不能解决问题。测试上下文启动时出现 `No qualifying bean of type 'com.classroom.ai.modules.attendance.service.AttendanceAccessService'`，导致七项测试报错；不是七项独立业务断言失败。

## 实际运行结果

两次运行都启用了 `EXP3_MYSQL_URL` 条件，使用一次性本地 MySQL 容器，每次运行前重新创建该容器内的测试数据库。

| 配置 | 测试数 | 通过 | 失败 | 报错 | 跳过 | Maven退出码 |
|---|---:|---:|---:|---:|---:|---:|
| 原项目配置 | 7 | 0 | 0 | 7 | 0 | 1 |
| 临时副本补入Bean | 7 | 7 | 0 | 0 | 0 | 0 |

最小改动：在该测试的 `@Import` 列表追加 `AttendanceAccessService.class`；临时副本使用类的完整限定名，因此没有增加 Java 导入声明。补丁见 `docs/20261010-attendance-test-import-check-v1/temporary-fix.patch`。

## 以前为什么没有发现

此前提交前常规测试日志 `docs/20261010-fix-exp3-push-evidence-v1/validation/camera-backend-tests-log.txt` 第171行记录该类七项全部跳过。没有设置匹配的一次性 MySQL 环境变量时，这组测试不会执行。因此该次常规测试通过不能证明这组集成测试通过。本次缺陷不会推翻此前单独实际执行的 Sprint2MysqlTest 4/4 或浏览器验证结果。

## 证据与边界

来源为当前本地测试/业务源码、先前常规测试日志，以及本次真实运行日志和 Surefire XML；没有使用外部资料。完整结果索引：`docs/20261010-attendance-test-import-check-v1/summary.json`。原配置与临时配置的日志分别为该目录的 `original.log` 和 `temporary-fixed.log`，XML保存在对应子目录中。`run.ps1` 和 `prepare.py` 可重建独立副本并复现对照。

所有原项目 `backend/src` 文件的 SHA256 与运行前一致，HEAD未变。本次仅新增复核报告、证据和过程记录，未修改原项目业务或测试源码，未提交或推送。一次性 MySQL 容器已清理。当前实际代码修复仍未应用；建议复核并将上述最小配置修复纳入后续提交。本结果证明测试配置缺陷及修复效果，不据此推断生产环境存在相同故障。
