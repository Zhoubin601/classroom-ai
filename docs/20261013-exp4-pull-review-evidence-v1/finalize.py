from pathlib import Path
import hashlib,json,re,subprocess

root=Path(__file__).resolve().parents[2]
evidence=Path(__file__).resolve().parent
# Only sanitize new evidence, never original or cloud evidence files.
redacted=[]
for p in evidence.rglob('*'):
    if p.is_file() and p.suffix in ('.log','.xml'):
        content=p.read_text(encoding='utf-8-sig',errors='replace')
        clean=re.sub(r'(?im)(Using generated security password:)\s+\S+',r'\1 [REDACTED]',content)
        clean=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED-JWT]',clean)
        if clean!=content:
            p.write_text(clean,encoding='utf-8')
            redacted.append(str(p.relative_to(evidence)))
baseline=json.loads((evidence/'protected-before.json').read_text(encoding='utf-8-sig'))
mismatches=[]
for item in baseline:
    p=Path(item['path'])
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest().upper()!=item['sha256'].upper():
        mismatches.append(item['path'])
source_diff=subprocess.check_output(['git','diff','--name-only','--','frontend','backend','scripts/tests','raw'],cwd=root,text=True).strip()
protection={'protectedFiles':len(baseline),'mismatches':mismatches,'trackedSourceDifferences':source_diff.splitlines(),'redactedFiles':redacted}
(evidence/'protection-final.json').write_text(json.dumps(protection,ensure_ascii=False,indent=2),encoding='utf-8')
assert not mismatches, mismatches
assert not source_diff, source_diff
entries={
 'source-notes.md':'''\n\n## 2026-10-13 最新 exp4 拉取本机复验来源
来源名称：用户当前拉取/检查请求及 Docker 启动后的继续验证授权；链接或文件名：本聊天；引用日期：2026-10-13。
来源名称：Git远端最新提交；链接或文件名：origin/exp4，20de6a85f6a475d8e67b9fcd2690aa8c3a833a46（基准5900488）；引用日期：2026-10-13。网络仅用于授权的Git fetch，没有新增外部业务资料。
原始资料：raw/README.md，仅目录说明；业务原始资料未提供。背景：memory-bank/project-context.md、云端更新附带的current-fix和final-recheck记录；本轮测试以直接执行为准，不以云端结果代替。
实测来源：docs/20261013-exp4-pull-review-evidence-v1/ 的 backend-summary.json、backend-full.log、frontend-tests.log、frontend-build.log、vision-tests.log、legacy-browser/、business-browser/、resource-browser/、login-browser/、fixes-browser/、ui-browser-wait/、login-authenticated-dev/、login-authenticated-production/、protection-final.json、cleanup.json。合成视频沿用docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4。ui-ux-pro-max Skill用于交互检查；未生成Office正式文件。
''',
 'analysis-notes.md':'''\n\n## 2026-10-13 最新 exp4 本机检查结论
已fast-forward由5900488到20de6a8，原有工作区内容保留。Docker恢复后后端单次284全部通过、无跳过；前端23、Python9、实验二五故事和Sprint3八组真实业务回归通过，资源专项7、登录专项3及七问题8组通过。
补充检查确认旧身份探测成功先返回/新交互登录后完成仍使页面旧账号与新Token错配，开发与生产均复现。sessionEpoch在登录开始时未增加，旧用户安装导致LoginView卸载，后续事件无法更新App。既有登录专项只覆盖新登录完成后旧成功返回，不能据此判定全部时序安全。此为最新版残留问题，不宣称由新提交首次引入，不宣称后端越权。
既有UI脚本即时读取异步保存后的aria-selected失败；仅在docs副本使用assertEventually等待后15组通过，原脚本和业务源码未改。早期开发诊断误拦截源码模块，修正夹具后最终复现有效。详情docs/20261013-exp4-pull-review-v1.md。只检查，未修复/提交/推送/部署。
''',
 'questions.md':'''\n\n## 2026-10-13 最新 exp4 本机复验后续
无需补充业务资料即可确认本轮登录竞态。未完成：登录开始时失效旧启动探测及补齐相反返回顺序的回归；本次用户要求拉取/检查/验证，未擅自修改业务代码。可依据docs/20261013-exp4-pull-review-v1.md进行后续修复。
待复核：真人摄像头、多人识别和GPU现场效果。本轮Docker已恢复，真实MySQL/Redis业务验证完成，不沿用此前“引擎不可用”的限制为当前结论。本轮没有重跑旧库两次升级、Windows一键启动或性能容量测试。
'''
}
for filename,addition in entries.items():
    with (root/'docs'/filename).open('a',encoding='utf-8') as f:f.write(addition)
print(json.dumps({'protectedFiles':len(baseline),'mismatches':mismatches,'sourceDifferences':source_diff,'redactedFileCount':len(redacted)},ensure_ascii=False))
