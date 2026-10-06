# 实验二分支收尾记录

日期：2026-10-06。依据：用户要求将 feat/exp2-supplement-auth-fix 合入 main，完成实验二收尾并清理其他历史分支；用户此前明确 exp3 为提前开发。

## 执行范围

- 合并 origin/feat/exp2-supplement-auth-fix 到 main 并同步远端。
- 清理已合入 main 的实验二历史功能分支：feat/exp2-supplement-auth-fix、feat/foundation-auth、feat/us01-us02-course-content；本地另有 feat/us03-us04-us06-schedule-query，祖先核对通过后一起清理。
- 保留 feat/exp3-sprint2 和 backup/pre-exp3-20260923，不把实验三成果并入实验二。
- 不调整发布标签、不重新打包交付物、不清理用户文件。

## 来源

- 来源名称：classroom-ai Git 远端。
- 链接：https://github.com/Zhoubin601/classroom-ai。
- 引用日期：2026-10-06。
- 原始资料：本轮为用户指令与 Git 仓库状态，没有新增业务原始文件；raw/README.md 仅为目录规则。
- 合并前 main：7b1a589279bca1ca4eb421febc6a7bdbfb4436b4。
- 补充分支：64a88f2b7d64214a0974ced295247e692516ccb0。
- 实验三分支：347a51a0fe05838a1391b3127b97a66319a37989。
- 实验三前备份：36605fb666f35989e0f6adafd9bb76b488831624。
- 历史功能分支：foundation-auth 为 4e9ce00，us01-us02 为 6e9642f，本地 us03-us04-us06 为 b315ee4；完整历史在 main 中保留。

## 预检与保护

- fetch 后实时核对远端分支。main 尚未包含补充分支的 64a88f2 合并提交。
- git merge-tree --write-tree --name-only 预检成功，无冲突，结果 tree 为 758eb9645a8474e603f461cf30511e09ff755e05，与原 main 完全相同。
- 因而本次合并补齐历史关系，不撤销 main 已有的初始化、构建或启动修复，也不增加实验三文件。
- 使用 runtime/worktrees/exp2-closeout-20261006 的独立 main 工作区执行合并，原工作区保持 feat/exp3-sprint2。
- 合并前记录原工作区已修改前端、脚本、长期背景和 raw 文件的 SHA256，完成后逐项核对。原有文档改动保留，过程记录只追加。

## 执行状态

- 已完成合并并推送 main；新提交为 a4da3d8de421bca9549d8ce302537dd084752fca，父提交为原 main 的 7b1a589 与补充分支的 64a88f2。
- 已验证合并前后文件树完全相同，补充分支与三个历史功能分支的提交均包含于新 main；实验三分支不是 main 的祖先。
- 使用单次原子推送更新 main 并删除三个远端历史分支；删除操作以各分支原 SHA 的 lease 校验防止误删他人并发更新。
- 已清理远端与本地 feat/exp2-supplement-auth-fix、feat/foundation-auth、feat/us01-us02-course-content，以及仅存在于本地的 feat/us03-us04-us06-schedule-query。历史提交保留在 main，不删除用户文件。
- 实时远端再次查询只剩 main（a4da3d8）、feat/exp3-sprint2（347a51a）和 backup/pre-exp3-20260923（36605fb），默认分支仍为 main。
- 本地主分支与 origin/main 一致；原工作区仍在 feat/exp3-sprint2，没有 stash、切换或提交其未提交改动。
- 已核对合并前记录的 18 个前端、脚本、背景和 raw 文件哈希，均保持一致；本轮收尾说明在此之后追加，不覆盖既有内容。
- 分支整合与历史分支清理已完成；临时独立工作区在确认干净后移除。

## 验证边界与复核事项

- 本轮验证 Git 历史、文件树、分支边界、原工作区保护及远端同步。文件树不发生变化，因此不以本次历史合并为由重复应用回归，也不宣称本轮应用测试通过。
- 原有 v0.1.0-sprint1 本地/远端指向差异与本地 v3 标签仍需后续发布复核，本次不处理。
- 实验二会议记录真实性、人工验收等既有事项见 docs/questions.md；本次仅完成分支收尾，不将这些事项认定为已完成。
