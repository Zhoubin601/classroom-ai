"""Author deterministic synthetic DOCX handouts through OfficeCLI, then export PDFs.

No existing file is overwritten. Source handouts live in docs/; final media are
runtime upload assets. The final delivery package may include their verified copies.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'docs/20261012-data-completeness-evidence-v1'
MARK = 'DEMO-DATA-20261012-v1'
LESSONS = {
    'SE': ('软件工程导论', '需求到验收的开发流程', ['描述一个任务管理工具的使用场景，列出用户目标与约束。', '将场景拆分为三条可检验的需求，给出输入、处理和预期输出。', '用需求、设计、实现和测试四个阶段说明交付路径，记录小组分工。'], '用需求说明、流程图及测试用例解释方案；讨论隐私保护与版本管理。'),
    'CS': ('计算机组成原理', '指令执行与存储层次', ['画出取指、译码和执行的简化数据通路，说明寄存器与存储器作用。', '比较两组缓存命中率对平均访存时间的影响，写出计算过程。', '设计两个能区分顺序执行和流水线行为的仿真用例。'], '提交数据通路图与仿真记录；讨论吞吐量、延迟及硬件资源之间的取舍。'),
    'AI': ('人工智能导论', '搜索策略与模型评价', ['用状态空间表示小型路径规划问题，明确初始状态与目标状态。', '比较广度优先和启发式搜索的访问次序，说明评价依据。', '为合成分类样本划分训练与验证集合，解释准确率的适用范围。'], '提交搜索轨迹和模型评价说明；讨论数据偏差、隐私与结论的适用范围。'),
    'DS': ('分布式大数据计算', '数据分区与聚合', ['将合成订单按键分区，说明每个分区的处理职责。', '设计映射与聚合步骤，计算各类订单数量并核对总数。', '比较不同分区数量的负载，讨论数据倾斜和故障恢复。'], '提交数据流图与聚合核对表；说明吞吐量、重试与幂等之间的关系。'),
    'SEC': ('信息安全攻防实践', '受控环境中的风险分析', ['在隔离的合成系统中列出资产、输入入口与信任边界。', '为一个输入校验缺口设计不影响外部系统的验证用例。', '给出参数校验、最小权限与日志审计三项修复措施。'], '提交威胁模型与修复验证说明；仅使用合成目标，讨论授权和数据保护边界。'),
}


def run(*args):
    result = subprocess.run(args, capture_output=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def main():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    source = EVIDENCE / 'sample-handouts'
    source.mkdir(exist_ok=True)
    files = []
    for major, (course, topic, steps, discussion) in LESSONS.items():
        for version in range(1, 3 if major == 'SE' else 2):
            name = f'{MARK}-{major}-v{version}'
            doc = source / (name + '.docx')
            pdf = ROOT / 'runtime/uploads/resources' / (name + '.pdf')
            if not doc.exists():
                run('officecli', 'create', str(doc))
                ops = []
                def para(text, style='Normal', size='11pt', bold=False):
                    ops.append({'command':'add','parent':'/body','type':'paragraph','props':{'text':text,'style':style,'font':'Calibri','font.ea':'Microsoft YaHei','size':size,'bold':str(bold).lower(),'spaceAfter':'8pt','lineSpacing':'1.25x','color':'17324D' if bold else '263648'}})
                para('Classroom AI 合成教学示例', 'Title', '23pt', True)
                para(course + ' · ' + topic, 'Subtitle', '15pt')
                para('来源：' + MARK + f' | 版本：v{version} | 日期：2026-10-12', size='9pt')
                para('此文件由数据治理脚本生成，用于演示资源、版本与预览功能。课程案例、练习和评价均为合成示例，不是学校正式教学材料、培养方案或真实师生记录。')
                para('学习目标', 'Heading1', '18pt', True)
                para('理解课程相关概念，通过可复核案例分析并解释结果；用讨论、实验记录和简要说明展示学习过程。')
                para('案例任务', 'Heading1', '18pt', True)
                for i,step in enumerate(steps,1):
                    para(f'{i}. {step}')
                if version == 2:
                    para('新版补充：同组互评两份验收条件，指出边界输入和遗漏步骤，再修订一条测试用例。')
                para('讨论与形成性反馈', 'Heading1', '18pt', True)
                para(discussion)
                para('演示反馈口径：概念解释、步骤可复核性、证据与结论的一致性。示例分数不计入任何真实课程成绩。')
                para('使用说明', 'Heading1', '18pt', True)
                para('资源标签为理论、讨论或实验、研讨；版本v2保留v1文件。预览与下载由现有账号授权控制。未录制真人、不包含生物识别特征。')
                ops.append({'command':'add','parent':'/','type':'footer','props':{'type':'default','align':'center','size':'9pt','text':'合成示例 · ','field':'page'}})
                batch = source / (name + '.batch.json')
                batch.write_text(json.dumps(ops,ensure_ascii=False),encoding='utf-8')
                run('officecli','batch',str(doc),'--input',str(batch),'--stop-on-error')
                run('officecli','close',str(doc))
                run('officecli','validate',str(doc))
            if not pdf.exists():
                # Copy into the mounted upload folder only after authoring has completed.
                run('docker','cp',str(doc),'classroom-backend:/tmp/' + doc.name)
                run('docker','exec','classroom-backend','soffice','-env:UserInstallation=file:///tmp/demo-data-pdf-profile','--headless','--convert-to','pdf','--outdir','/app/runtime/uploads/resources','/tmp/' + doc.name)
            if not pdf.is_file():
                raise RuntimeError('PDF export missing: ' + str(pdf))
            files.append({'file':str(pdf.relative_to(ROOT)),'bytes':pdf.stat().st_size,'sha256':hashlib.sha256(pdf.read_bytes()).hexdigest(),'authored_by':'officecli','synthetic':True})
    video = ROOT / 'runtime/uploads/micro' / (MARK + '.mp4')
    video.parent.mkdir(parents=True,exist_ok=True)
    if not video.exists():
        run(str(ROOT/'runtime/tools/ffmpeg/ffmpeg.exe'),'-v','error','-f','lavfi','-i','color=c=0x17324D:s=640x360:r=25:d=4','-c:v','libx264','-pix_fmt','yuv420p',str(video))
    probe = json.loads(run(str(ROOT/'runtime/tools/ffmpeg/ffprobe.exe'),'-v','error','-show_entries','format=duration:stream=codec_type','-of','json',str(video)))
    assert float(probe['format']['duration']) == 4
    files.append({'file':str(video.relative_to(ROOT)),'bytes':video.stat().st_size,'sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'duration_ms':4000,'synthetic':True,'no_personal_imagery':True})
    (EVIDENCE/'sample-files.json').write_text(json.dumps(files,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'pdf_files':6,'synthetic_video':True,'authored_by':'officecli'}))


if __name__=='__main__':
    main()
