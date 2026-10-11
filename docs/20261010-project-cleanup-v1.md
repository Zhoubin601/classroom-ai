# 2026-10-10 项目文件清理记录

用户授权：清理项目中过期和重复文件。仅使用本地目录、Git与既有过程记录，未使用外部资料。

实际删除 **19,757个文件**，逻辑大小合计 **861,402,314字节（821.50 MiB）**。这是删除文件的大小合计，不是文件系统空闲空间测量；两份转存证据不计入删除数量。

| 清理对象 | 结果与依据 |
| --- | --- |
| runtime/worktrees/fix-exp3-local-check-20261006 | 旧测试提交已包含在当前HEAD中；独有未提交截图先保全后，使用git worktree remove移除 |
| runtime/worktrees/main-startup-check-20261006 | 无未提交修改，旧提交已包含在当前HEAD中；使用git worktree remove移除 |
| runtime/branch-switch-backups/20261009-143732/files | 删除343份与主目录同一相对路径文件SHA256一致的副本；保留64份不同内容备份 |
| docs/20261010-handover-build-v1/staging | 删除22份与output正式文件SHA256一致的暂存副本 |
| docs/20261010-attendance-test-import-check-v1/temporary-module | 删除测试源码副本和编译产物；XML/TXT与temporary-fixed已有存证一致；独有dumpstream先保全 |
| runtime/browser-tests | 删除21个9月23日至26日的旧隔离测试目录，90个临时输入和日志文件；没有进程使用目标路径 |
| Python缓存、旧空docker目录 | 删除可重建的缓存及没有文件的旧docker/mysql/init；当前部署文件在scripts/deploy |
| error.log与3份PID | 删除10月6日的旧根目录日志，以及没有对应进程的失效PID |

详细清单及删除到保留文件的映射：[plan.json](20261010-project-cleanup-v1/plan.json)。执行结果：[result.json](20261010-project-cleanup-v1/result.json)。保护指纹：[protected-before.json](20261010-project-cleanup-v1/protected-before.json)。实施脚本：[cleanup.ps1](20261010-project-cleanup-v1/cleanup.ps1)。记录与脚本均防止覆盖或重复执行。

两份独有证据转存到 `docs/20261010-project-cleanup-v1/recovered-evidence/`：旧工作区的teacher-published-feedback截图及2026-10-10T13-44-07_298.dumpstream。原始位置、目标位置及SHA256见清单。截图属于旧隔离工作区的历史证据，不作为当前业务验收的新结果。

清理完成时，**1,565项保护文件SHA256一致**，覆盖raw、当前源码/脚本、output正式文件、已有文档/素材、模型/上传文件、长期背景及64份不同内容备份。活动日志保留原位，不加入稳定指纹核对。Git仍为fix-exp3 / 7464f9cf5875ec3e416d313e11c9838f5a2bec15；refs、stash及已跟踪文件原有未提交状态一致。项目外Codex工作区C:/Users/a3185/.codex/worktrees/8810/classroom-ai-demo保留。

资料依据：raw/README.md（目录只有说明文件，额外业务原始资料未提供）、AGENTS.md、docs/目录整理说明.md、docs/20261009-workspace-switch-v1.md、交接delivery-check.json、测试summary.json、memory-bank/project-context.md及当前Git只读状态。引用日期为2026-10-10。

保留了raw、当前源码/依赖/build产物、数据库备份、模型、上传业务数据、验收记录和output正式交付物。不同日期或版本不自动认定过期，历史源码包v2/v3/v4及64份不同内容备份继续保留，后续可复核保留需求。本轮已核验的清理项全部完成。

切换备份去重后不再是自包含全量副本：switch-record.json保留原历史事实；删除的相同文件在主目录的路径与哈希以本轮plan.json为准，原stash继续保留。旧工作区提交仍可从Git历史追溯。临时测试副本属于当时版本，不保证历史核验脚本可直接在当前代码上重跑。

本轮未修改应用代码、部署、操作数据库或停止服务；没有新建output成品。过程说明追加到docs/source-notes.md、analysis-notes.md、plan.md和questions.md。raw未改写、移动、重命名或删除。
