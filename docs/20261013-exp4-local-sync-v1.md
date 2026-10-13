# exp4 数据治理脚本精简同步

日期：2026-10-13（Asia/Shanghai）。用户已授权提交并推送到 exp4，随后明确选择“只提交数据治理脚本和必要说明”，并要求其余不需要的资料加入 ignore。

## 本次范围

纳入八个脚本：数据库审计、Windows DPAPI 业务备份、合成演示数据 plan/apply、守卫回滚、Redis 索引核对、合成课件生成，以及两项既有验收入口；另纳入本说明和精确的 .gitignore 规则。

截图、历史测试证据、报告生成过程、Word、Excel、操作包及其他本地过程资料不提交，文件留在原处。已跟踪的 source-notes.md、analysis-notes.md、questions.md 等本地修改保留，不通过 ignore 隐藏。stop_project.ps1 的意外断行恢复为原有 SilentlyContinue，若与 HEAD 一致则没有该文件的提交差异。

来源名称：用户当前授权、本地 Git 差异及项目脚本；链接或文件名：本聊天、scripts/data-completeness.py、scripts/seed-complete-demo.py、output/20261012-demo-data-operation-v1.md；引用日期：2026-10-13。原始资料：raw/README.md 仅为目录说明，业务原始资料未提供。本次不修改 raw，不更新业务数据库，不生成 Office 文件。

## 使用入口

在项目根目录执行，使用新的证据目录和备份文件名；不得覆盖已有用户数据库或数据卷。合成数据标记为 DEMO-DATA-20261012-v1，是固定日期的教学演示，不代表真实师生或审批事实。

```powershell
$runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runEvidence = "docs/runtime/data-completeness-$runStamp"
python scripts/data-completeness.py audit --evidence "$runEvidence/audit-before.json"
python scripts/create-demo-office-assets.py
python scripts/seed-complete-demo.py plan --majors SE,CS --evidence-dir "$runEvidence/seed"
python scripts/data-completeness-backup.py backup --file "runtime/backups/$runStamp-business.sql.dpapi" --evidence "$runEvidence/backup.json"
# 审阅计划后，明确决定补充本地开发数据时执行：
python scripts/seed-complete-demo.py apply --majors SE,CS --environment local-development --backup-evidence "$runEvidence/backup.json" --evidence-dir "$runEvidence/seed"
python scripts/data-completeness.py audit --evidence "$runEvidence/audit-after.json"
```

依赖：现有 Docker Compose 开发环境、已初始化并迁移的课堂数据库、Python、OfficeCLI、后端容器内 LibreOffice，以及 runtime/tools/ffmpeg/ 下的 ffmpeg.exe 与 ffprobe.exe。合成媒体需先生成且实际存在，脚本不会创建账户或扩大督导授权。其他专业需先具备正常业务授权，再通过 --majors 显式选择。

备份排除账户与人脸表，并使用 Windows 当前用户 DPAPI；密文不可当作跨机通用 SQL。restore-test 只允许独立 classroom-data-test-* 容器。回滚默认生成 SQL，使用本次执行产生的 applied-manifest.json 和 apply-before.json；行哈希变化或新增外部依赖会阻止回滚。

Redis 索引核对入口为 scripts/sync-demo-face-cache.py，默认只读，修复需显式 --apply；API/浏览器验收入口为 scripts/tests/data-completeness-api-browser.cjs。运行时凭据只由进程环境提供，不写入仓库。

scripts/tests/verify-data-completeness.py 是本机恢复演练入口，依赖同机当前用户可解密的备份及其父证据目录中的 backup.json，且默认使用固定演示专业范围。此脚本不能在缺少本地证据、媒体或授权的全新云端工作区直接当作通用 CI 执行。

## 验证与限制

提交前检查脚本语法、只读 --help、敏感信息、精确忽略范围及 raw 指纹，不为同步提交重新执行数据库写入或全量业务测试。既有数据治理实测来源为本地 recovery-final/results.json、browser-final/results.json；这些证据不在本次精简提交中，不宣称本轮重新跑过。

最新版登录竞态仍未修复，详见本地 docs/20261013-exp4-pull-review-v1.md；本次仅同步工具。业务数据、备份和运行环境不会随 Git 推送同步。最终提交号和推送状态以实时 Git 核验为准。

本轮提交前结果：7 个 Python 文件语法通过、6 个只读 CLI --help 通过、1 个 Node 脚本语法通过；PowerShell 停止脚本语法通过且无提交差异；raw 内容与 HEAD 一致。未执行数据补充、回滚或业务部署。
