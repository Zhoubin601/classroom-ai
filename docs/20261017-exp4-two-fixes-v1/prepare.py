import json
import subprocess
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

root = Path(__file__).resolve().parents[2]
evidence = Path(__file__).resolve().parent
previous = json.loads((root / 'docs/20261017-exp4-acceptance-recheck-v1/baseline.json').read_text(encoding='utf-8'))
baseline = {'startedAt': datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(), 'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip(), 'protectedBeforeFix': previous['protectedSha256'], 'sourceBaselineNote': 'Source matched the previous audit baseline immediately before the authorized two-defect fix. Existing docs changes and untracked evidence are preserved.'}
(evidence / 'baseline.json').write_text(json.dumps(baseline, ensure_ascii=False, indent=2), encoding='utf-8')
entries = {
 'source-notes.md': '来源名称：用户本轮修复和Playwright验证授权；链接或文件名：本聊天；引用日期：2026-10-17。\n来源名称：本机已复现缺陷及实验一原规划摘录；链接或文件名：docs/20261017-exp4-acceptance-check-v1.md、docs/20261017-exp4-acceptance-recheck-v1/original-plan-extract.txt、upload-policy-probe/render-confirmation/results.json；引用日期：2026-10-17。不重写原件，不引入外部业务资料。',
 'analysis-notes.md': '用户已明确授权修复R-01与R-02，无需再次确认实施方向。上传策略由已登录接口提供，主上传与版本上传共享类型/大小校验及动态提示；加载失败允许重试，不退回固定100MiB。教师/督导评分文案明确四维，BOPPPS仅保留真实可选环节。采用ui-ux-pro-max的错误恢复与清晰校验指引。',
 'plan.md': '1. 新增与后端实际校验一致的已登录上传策略接口。\n2. 两个前端入口共用策略读取/校验、动态accept/上限和失败重试；修正四维评分文案。\n3. 重新构建当前源码，执行必要单元/后端测试、Sprint2正式入口及150MiB/1024字节真实Chrome边界回归。\n4. 保存截图/日志和最终保护清理结果；不自动提交、推送或替换日常部署。',
 'questions.md': '用户明确授权修复并Playwright验证，实施范围已确认。旧R-01/R-02待修复状态将由本轮结果更新；自然人批准与硬件现场范围维持既有后续安排。本轮不覆盖原始资料、旧报告或旧测试证据。',
}
for name, entry in entries.items():
    with (root / 'docs' / name).open('a', encoding='utf-8', newline='\n') as handle:
        handle.write('\n\n## 2026-10-17 exp4 两项缺陷修复与Playwright验证\n\n' + entry + '\n')
print('Fix baseline and process notes recorded.')
