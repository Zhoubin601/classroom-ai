# Classroom AI 数据补充操作说明

日期：2026-10-12。数据集标记：`DEMO-DATA-20261012-v1`。这是固定演示时点的合成数据集，不作为真实师生、考勤、督导或审核事实。正式课程建议保持DRAFT，不自动发布。

## 本轮交付与实测

- 主库32张业务表、333个实际字段均已检查；新增307条种子记录，另有2条实际接口访问日志；34条已有记录只补确定缺失或纠正文件事实。
- 学生80→140，开课18→25，选课172→256，排课18→25，考勤7→19，资源10→16，评价6→11。
- 本地库和克隆库反复执行不增记录、不重复改写；失败注入、守卫回滚和加密恢复均有实际证据。
- 后端206项普通测试及75项MySQL专项通过；前端13项及构建通过；独立库API/真实Chromium15组、本地库API8组通过。既有CourseBrowserMysqlTest本轮未跑，不把它计为通过。
- 依据用户粘贴任务、本地代码及实际MySQL/Redis查询。raw仅README，未提供正式课程资料；未使用外部资料。完整汇总、字段与关联检查见同目录DOCX和XLSX。

## 环境与文件

使用现有Docker Compose开发库 `classroom-mysql/classroom_ai`，原数据卷保留。OfficeCLI 1.0.156负责课件、正式DOCX及XLSX生成。Python脚本使用标准库；Windows DPAPI备份只支持本机当前用户。真实示例PDF保存在 `runtime/uploads/resources/`，4秒纯色视频保存在 `runtime/uploads/micro/`；交付包包含这些文件的副本。

本轮备份：`runtime/backups/20261012-data-completeness-v1.sql.dpapi`；解密一致性及文件哈希证据：`docs/20261012-data-completeness-evidence-v1/backup.json`。备份排除 `t_user_account` 和 `face_feature`，它们本轮未更改；不含上传文件，原上传文件也未修改。备份与敏感表不进入交付ZIP。DPAPI与Windows当前账号/机器绑定，不能把备份当作跨机通用明文SQL。

## 已有开发库重复执行

在项目根目录的PowerShell运行。为每次运行选择新证据目录和备份文件名，不覆盖此前版本。

```powershell
Set-Location -LiteralPath 'D:\2026Autumn Semester File\classroom-ai-demo'
$runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runEvidence = "docs/data-completeness-$runStamp"
python scripts/data-completeness.py audit --evidence "$runEvidence/audit-before.json"
python scripts/create-demo-office-assets.py
python scripts/seed-complete-demo.py plan --evidence-dir "$runEvidence/seed"
python scripts/data-completeness-backup.py backup --file "runtime/backups/$runStamp-business.sql.dpapi" --evidence "$runEvidence/backup.json"
python scripts/seed-complete-demo.py apply --environment local-development --backup-evidence "$runEvidence/backup.json" --evidence-dir "$runEvidence/seed"
python scripts/seed-complete-demo.py plan --evidence-dir "$runEvidence/repeat"
python scripts/seed-complete-demo.py apply --environment local-development --backup-evidence "$runEvidence/backup.json" --evidence-dir "$runEvidence/repeat"
python scripts/data-completeness.py audit --evidence "$runEvidence/audit-after.json"
```

`plan`只读，并生成本机可审阅SQL和数据来源计划。`apply`需要该计划与当前生成结果完全一致、验证过的备份以及明确的本地开发目标；其他环境拒绝。记录使用稳定课程编码、班级/学期、合成学号、资源文件名及父记录业务键，不依赖盲目固定自增ID。串行锁、事务、存在性条件和提交前校验避免重复或部分写入。

Office课件生成器保留已有同名文件，不覆盖用户文件。若源课件损坏，应选择新数据集/新版本修订后重新验证，不能用空文件或虚假URL代替。文件元数据根据实际字节和哈希填写，正常四舍五入显示值保留，明显错误的旧大小显示值会纠正。

## 全新环境与权限

全新环境按项目既有流程导入 `scripts/deploy/mysql/init/initialize.sql` 完整基线，再启动应用完成专业关联及Sprint2/3迁移。不要把基线重新导入已有用户数据库或清空数据卷。`TeachingDataInitializer`的已有账号初始化行为与本次种子分开，本次种子不改账号密码、角色或授权范围。

本机实际督导已授权五专业，所以五专业种子成功。全新基线的督导仅授权SE、CS；全五专业模式会安全拒绝。可以显式选择既有授权范围：

```powershell
python scripts/seed-complete-demo.py plan --majors SE,CS --container classroom-data-test-YOUR_RUN --database classroom_ai --evidence-dir docs/YOUR_RUN/plan
python scripts/seed-complete-demo.py apply --majors SE,CS --container classroom-data-test-YOUR_RUN --database classroom_ai --environment isolated-test --evidence-dir docs/YOUR_RUN/plan
```

这里 `classroom-data-test-YOUR_RUN` 指自己创建的独立测试容器；不要换成生产库。需要其他专业时，先由有权限的业务用户通过正常授权流程配置，再重新计划。脚本不会给督导扩权，不创建ADMIN账号。全新基线的SE、CS实测新增229条、重复执行零变化、接口可见4个合成班次。

## Redis

历史数据不写实时缓存，课堂推流、心跳和过期仍由原业务机制管理。新增60个合成学生没有人脸特征。

```powershell
python scripts/sync-demo-face-cache.py --evidence docs/cache-audit.json
# 仅在实际索引与MySQL不一致且需要对齐时显式执行
python scripts/sync-demo-face-cache.py --apply --evidence docs/cache-sync.json
```

本轮通过原授权 `/api/face/all` 回源机制，将3个不在当前MySQL中的旧缓存身份替换为正确的80个既有特征身份；没有生成或保存人脸向量。凭据仅在进程内使用，不写入证据。非默认演示凭据可由进程环境变量提供，使用后应退出该进程，不放进文档或仓库。

## 守卫回滚

回滚默认只生成SQL，不实际写入。必须使用本轮综合清单，其中包含种子记录、实际测试访问日志及文件显示大小修正的原值。

```powershell
python scripts/rollback-complete-demo.py --manifest docs/20261012-data-completeness-evidence-v1/live/rollback-manifest.json --baseline docs/20261012-data-completeness-evidence-v1/live/apply-before.json --sql-file docs/rollback-preview.sql
# 只有明确决定撤销该批数据时，才追加以下参数：
# --container classroom-mysql --environment local-development --apply --evidence docs/rollback-result.json
```

所有来源内记录的当前行哈希必须匹配，外部新增依赖必须为0，否则事务拒绝。原值只回写本轮确定修复的字段。后续人工修改的示例、新增真实访问日志或人脸记录会阻止强制回滚，不自动删除它们。文件不自动删除。生成SQL中ID来自经过哈希验证的清单，不能跨库直接照搬。

## 加密备份恢复演练

`restore-test`只允许本轮命名规则下的独立测试容器，不允许覆盖原业务库：

```powershell
python scripts/data-completeness-backup.py restore-test --target classroom-data-test-YOUR_RUN --file runtime/backups/20261012-data-completeness-v1.sql.dpapi --evidence docs/restore-result.json
```

恢复前应在该独立容器准备对应数据库和未更改的账号/特征表，再恢复业务表。完整演练入口 `scripts/tests/verify-data-completeness.py` 会创建自己的临时容器、在内存中克隆、恢复基准、执行种子/幂等/失败事务/守卫回滚/加密恢复，再清理自己创建的匿名测试卷。当前项目备份证据位于本任务证据目录；移到其他机器应先做该机备份再配置演练，不能直接复制当前DPAPI密文冒充可恢复备份。

## 证据、限制与复核

证据目录：`docs/20261012-data-completeness-evidence-v1/`。重点文件：`live/apply-before.json`、`live/rollback-manifest.json`、`live-final.json`、`final-protection.json`、`restart-protection.json`、`redis-sync.json`、`browser-final/results.json`、`live-api/results.json`、`fresh-results.json`、`recovery-final/results.json`。

需业务人员复核：1条旧FINISHED班次的真实归档依据、78条旧时序统计异常、3条原ACTIVE会话的结束状态、真实课程目标/考核权重、未分配课程的教师、旧大纲/微格来源与旧标签。没有依据的历史结束时间、审核人、正式指标和人脸特征不推填。既有DS2001内容口径与名称不匹配仍保留，不能按猜测覆盖人工内容。

本轮不切换分支，不提交或推送。原始资料与生产业务源码保留，任务产生的独立测试容器/匿名卷已清理，用户MySQL/Redis卷保留。正式交付文件只放output，过程证据留docs。


## 2026-10-13 远端发布资料的隐私处理

本次用户授权将不涉及隐私的交付资料和证据提交到 exp4。审计 JSON 的账号、人脸、学生及课堂个人记录逐行摘要已从发布副本中移除，保留表结构、数量、其他摘要和原测试结论；privacy_redaction 标记说明删减范围。完整审计原件保留本机 runtime/backups/20261013-publish-private-originals-v1/，不会随 Git 发布。发布版不是完整个人记录的校验基线；跨机器回滚/恢复必须使用该机自己生成的完整审计、清单及可解密备份。此处理没有访问或改写数据库。
