# 工作区切换记录

日期：2026-10-09。用户要求把当前工作区切换到刚推送的 fix-exp3。

- 当前主目录：D:\2026Autumn Semester File\classroom-ai-demo
- 原分支/提交：main / 68255ad9a4ebe5b52df9b7a7f18eba32db97cf78
- 新分支/提交：fix-exp3 / fa387b87023eef5a1b7ab7a0a8d754ff1833de75，与 origin/fix-exp3 一致。
- 原主目录407个未提交文件逐一按原始字节备份并校验：D:\2026Autumn Semester File\classroom-ai-demo\runtime\branch-switch-backups\20261009-143732
- Git stash：2697b78f154e480e0646cd027d5519e54a006556；说明：On main: codex-preserve-main-before-fix-exp3-20261009-143732
- 完整备份清单和切换验证：D:\2026Autumn Semester File\classroom-ai-demo\runtime\branch-switch-backups\20261009-143732\switch-record.json
- Git存档已生成；大型二进制文件的反向清理耗时过长，停止本次工具创建的apply子进程后，使用已验证备份恢复索引并完成切换。
- 原 fix-exp3 工作区 C:\Users\a3185\.codex\worktrees\8810\classroom-ai-demo 保留在同一提交，改为分离 HEAD，未修改其文件。
- 292个原未跟踪文件保留/恢复到主目录；81个与分支跟踪文件重合的原版本保存在备份及stash，没有覆盖fix-exp3代码。
- 原main未提交源码改动保存在备份和stash，没有自动应用到新分支。
- 本轮测试报告及证据仍在docs/和output/原路径，按原字节核对。此前测试对象仍是main工作区的未提交修复，不改写为fix-exp3测试结果。
- raw/前后哈希相同。切换后已跟踪文件与fix-exp3提交一致。没有新提交或推送。
