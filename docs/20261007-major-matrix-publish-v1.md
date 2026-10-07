# 专业关联、指标矩阵与界面修复提交记录

用户于2026-10-07明确要求提交至远端仓库。目标为origin/main（https://github.com/Zhoubin601/classroom-ai），开始时本地和远端均为8beeac6，无分叉，无需覆盖远端历史。

## 提交范围

- 唯一牵头与显式参与教研室关联、统一专业范围及目录权限，一次性关联迁移和3项MySQL测试；课程写权限继续限定本室。
- 主任专业范围的加载/空状态、目录版本与新大纲入口；主任/教师大项和指标编号双向联动；排课筛选响应式布局。
- 全课程批量矩阵脚本、24门方案与快照、实际页面与持久化验证结果。脚本仅手动执行，不在启动时写入矩阵。
- 同会话已授权的需求文档口径修订、Sprint2检查及云端运行准备记录，与docs/、memory-bank/过程结论同步；云端未运行的事实保留，不当作通过。
- 保留并不纳入本次提交的无关旧文件：design-system/与output/两个Sprint1源码zip。本轮不修改raw，不推送构建产物、运行日志、凭据或数据库卷。

## 验证依据

- 专业关联修复时：后端普通113项执行通过（40项环境跳过），隔离MySQL7项通过，前端10项通过及构建；9项浏览器业务检查和2项当时日常只读检查。记录docs/20261006-major-department-rules-v1.md和相应evidence-v1/。
- 大项/排课修复时：类型检查与构建通过，主任/教师联动、四种视口、真实筛选与重置通过，见docs/20261006-indicator-schedule-ui-verification-v1.md。
- 批量配置：24门实际页面通过，原6份大纲/13条指标和课程档案哈希一致。2026-10-07本机服务重启后持久化复核再次通过，重复预检拟新增为0。
- 本次提交前检查：Git差异、暂存文件范围、脚本语法及可疑凭据检查；既有通过结果仅注明其实际执行日期，不冒充重新运行整套后端。

## 在其他运行数据库使用批量脚本

Git同步代码、方案和证据，不会自动同步本机MySQL数据。其他环境先启动仓库的MySQL/后端，在项目根目录指定新的过程目录，再预览该环境实际课程：

```powershell
$env:MATRIX_EVIDENCE_DIR = 'docs/runtime/matrix-setup-v1'
node scripts/batch-course-matrices.cjs --preview
```

检查新目录的plan.json，确认符合该环境示例课程范围，然后在进程环境设置MATRIX_LOGIN_PASSWORD（不写文件），执行：

```powershell
node scripts/batch-course-matrices.cjs --apply
node scripts/batch-course-matrices.cjs --verify
Remove-Item Env:\MATRIX_LOGIN_PASSWORD
```

版本与主题配置针对当前示例；未知课程会在预检阶段停止。原矩阵、锁定及历史版本继续保留，新建议保持DRAFT，实际内容、目标与权重需复核。不对其他环境承诺与本机24门课程数量一致。

浏览器证据脚本密码同样只从进程环境读取；截图与JSON不保存JWT。具体执行日期与权限测试范围见各过程文档。
