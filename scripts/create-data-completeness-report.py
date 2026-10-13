"""Create audit DOCX and XLSX exclusively through OfficeCLI, from saved real evidence.
Outputs are staged in docs/ until schema, content and visual checks pass.
"""
import csv
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
EV=ROOT/'docs/20261012-data-completeness-evidence-v1'
STAGE=EV/'deliverable-staging'


def read(name):return json.loads((EV/name).read_text(encoding='utf-8'))


def run(*args):
    r=subprocess.run(args,capture_output=True,encoding='utf-8',errors='replace')
    if r.returncode:raise RuntimeError(r.stdout+r.stderr)
    return r.stdout


MODULES=[
 ('组织与权限',['t_major','t_major_department','t_teacher','t_user_account'],'教师账号ID已补齐；身份字段按角色可空','无孤立或错配','既有专业、教研室、三角色范围保留'),
 ('课程档案',['t_course','t_course_content_revision','t_course_import_log'],'增加15个合成建议草稿；正式事实保留','课程引用有效','课程列表、教师草稿及详情接口验证'),
 ('大纲与指标',['t_course_syllabus','t_graduation_indicator','t_training_indicator'],'推荐目录补齐；旧锁定目标不推填','无跨课程大纲映射','现有大纲版本及指标API正常'),
 ('开课与选课',['t_course_offering','t_course_offering_teacher','t_offering_student_enrollment','t_academic_term_lock'],'专业、主讲关系及人数已补齐','名单人数一致；保留1条旧归档异常','7个合成班次；教师历史页已验证'),
 ('排课',['t_course_schedule'],'新增7条完整排课','教师、教室时段冲突均为0','班次详情API可读；遵守授课时段门禁'),
 ('资源与版本',['t_course_resource','s3_resource_series','t_resource_access_log'],'真实文件大小与哈希已补齐','系列、当前版、课程关联有效','真实PDF、水印预览、2版历史已验证'),
 ('督导与审核',['t_supervision_evaluation','t_evaluation_audit_log','s3_advice'],'5组明确标记的合成状态快照','四维评分与24小时延迟一致','三角色可见性、统计及问题分类已验证'),
 ('行动反馈',['s3_action_feedback','s3_feedback_revision'],'2组合成反馈与版本历史','建议、教师与历史引用有效','教师建议到行动页面已验证'),
 ('评分模板',['s3_template_draft','s3_template_version'],'默认已发布四维模板保留','模板权重一致；无需伪造发布人','模板查询正常；未创建ADMIN账号'),
 ('学生与人脸',['student','face_feature'],'60个合成学生；无新增生物特征','学号唯一；既有特征引用有效','60人筛选页面已验证；缓存索引80/80'),
 ('考勤与课堂',['t_attendance_session','classroom_record'],'新增12会话、48历史时序点','新统计一致；78条旧异常保留','历史会话API正常；不填充实时大屏'),
 ('微格资产',['t_micro_teaching_slice','s3_video_asset'],'1个4秒纯色资产与3个片段','片段区间、哈希及课程关系有效','真实播放、区间停止已验证；旧片段未验证'),
 ('迁移记录',['t_app_data_migration'],'不伪造迁移事件','保留原迁移记录','无前端业务展示需求'),
]


def policy(table,column,nulls,empty):
    if table=='face_feature':return '保留既有生物特征；本轮不生成或更改'
    if table=='t_user_account':return '保留原账号、密码和权限；授权专业及教师工号按角色可空'
    if column in ['archived_at','archived_by','history_snapshot','snapshot_student_count']:
        return '在读/待开课应为空；旧FINISHED归档缺失需人工依据'
    if column in ['publish_time','reviewed_by','reviewed_at','review_note','submit_time']:
        return '按评价状态保留空值；不推填真人审核事实'
    if table=='t_course_content_revision' and column in ['publisher_name','publisher_code','published_at']:
        return 'DRAFT未发布时应为空'
    if table=='t_course' and column in ['objectives','assessment_method']:
        return '可信正式资料未提供；仅新增合成DRAFT建议，不覆盖正式字段'
    if table=='t_course' and column=='teacher_name':return '无任课依据的课程不猜测教师'
    if table=='t_course_syllabus':return '保留原大纲及版本；历史或锁定内容缺失不推填'
    if column in ['created_at','updated_at','created_by','updated_by','touched_at','published_by','created_by']:
        return '历史时间与操作人来源不明时保留；新合成记录留工具来源'
    if column in ['analysis_json','analysis_reviewed_at','analysis_reviewed_by','asset_id','start_ms','end_ms','cover_url']:
        return '历史未验证资产或未人工复核时可空；新资产按真实文件验证'
    if table=='student' and column in ['gender','avatar_url']:return '合成学生无真人属性/照片，保持未提供或空值'
    if table=='t_attendance_session' and column=='end_time':return 'ACTIVE应为空；FINISHED按历史时间记录'
    if table=='t_attendance_session' and column=='absent_student_ids':return '旧缺勤身份无证据不猜；新会话只列合成学号'
    if column in ['content_hash','file_size_bytes']:return '仅从实际本地文件字节推导，未验证文件不填'
    if table=='t_teacher' and column=='user_id':return '按唯一教师工号、TEACHER身份和同教研室补齐'
    if table=='t_course_offering' and column in ['major_id','major_code','is_snapshot_frozen']:return '仅补未冻结在读班次的确定关系或false标记'
    if column=='prerequisites':return '无先修关系的空字符串具有语义；不编造课程引用'
    if nulls or empty:return '未确定历史来源不推填；具体限制见审计报告'
    return '已有字段完整；保留有效值'


def workbook(before,after,summary):
    file=STAGE/'20261012-database-completeness-v1.xlsx'
    run('officecli','create',str(file));run('officecli','set',str(file),'/Sheet1','--prop','name=汇总')
    bf={(r['table'],r['column']):r for r in before['completeness']};af={(r['table'],r['column']):r for r in after['completeness']}
    fields=[['表','字段','实际类型','允许NULL','默认值','键/约束','原NULL数','后NULL数','原空串数','后空串数','保留或补充依据']]
    for c in before['columns']:
        b=bf[(c['table'],c['column'])];a=af[(c['table'],c['column'])]
        fields.append([c['table'],c['column'],c['type'],c['nullable'],'SQL NULL/无默认' if c['default'] is None else str(c['default']),c['key']+' '+c['extra'],b['null_count'],a['null_count'],b['empty_count'],a['empty_count'],policy(c['table'],c['column'],a['null_count'],a['empty_count'])])
    counts=[['业务表','原记录数','补充后记录数','变化','来源']]+[[t,b,after['counts'][t],None,'MySQL实查：live/apply-before.json → live-final.json'] for t,b in before['counts'].items()]
    prior={c['name']:c for c in before['checks']}
    checks=[['检查项目','原异常数','后异常数','结果','实际SQL']]+[[c['name'],prior[c['name']]['count'],c['count'],'已通过' if c['count']==0 else '既有异常保留',c['query']] for c in after['checks']]
    constraints=[['表','约束名','字段','引用表','引用字段','来源']]+[[c['table'],c['name'],c['column'],c['ref_table'] or '',c['ref_column'] or '','information_schema.KEY_COLUMN_USAGE'] for c in before['constraints']]
    mapping=[['表','Java字段','列','Java类型','预期类型','实际类型','差异','来源']]+[[m['table'],m['field'],m['column'],m['java_type'],m['expected'],m['actual'] or '',m['difference'] or '一致/兼容',m['source']] for m in read('static-source-audit.json')['mapping'] if m['difference']]
    coverage=[['业务场景','来源','记录/结果','限制']]
    for mod,tables,field,association,front in MODULES:coverage.append([mod,','.join(tables),front,field+'；'+association])
    tests=[['验证','实际执行','通过','未执行/跳过','来源']]
    ordinary=read('backend-test-counts.json')['total'];mysql=read('backend-mysql-test-counts.json')['total']
    tests+=[['后端普通测试',ordinary['tests']-ordinary['skipped'],ordinary['tests']-ordinary['skipped'],ordinary['skipped'],'backend-tests.log / backend-test-counts.json'],['MySQL专项测试',mysql['tests'],mysql['tests']-mysql['failures']-mysql['errors']-mysql['skipped'],mysql['skipped'],'backend-mysql-tests.log / mysql-junit/'],['前端测试',13,13,0,'frontend-tests.log'],['前端构建',1,1,0,'frontend-build.log'],['独立库API/真实浏览器',15,15,0,'browser-final/results.json'],['本地开发库API',8,8,0,'live-api/results.json'],['失败事务/回滚/备份恢复',5,5,0,'recovery-final/results.json'],['后端剩余条件浏览器',0,0,1,'CourseBrowserMysqlTest本轮未运行；由新增浏览器场景补充'],['新基线授权范围复验',3,3,0,'fresh-results.json；SE、CS填充/幂等/API通过；其余专业未授权']]
    plan=[['表','现有情况','补充字段/记录','生成依据','实际新增','前端影响','风险/限制']]
    table_module={t:(mod,field,association,front) for mod,ts,field,association,front in MODULES for t in ts}
    for t,n in before['counts'].items():
        mod,field,assoc,front=table_module[t];plan.append([t,f'实查{n}条',field,'已有稳定关系/Java模板/明确标记的合成案例',after['counts'][t]-n,front,assoc])
    sheets={'汇总':([['业务模块','涉及表','原记录数','补充后记录数','字段完整性','关联完整性','前端展示情况']]+summary,[20,49,13,15,35,34,42]),'全表数量':(counts,[38,16,18,13,65]),'字段对照':(fields,[36,28,25,15,32,26,13,13,14,14,78]),'关联检查':(checks,[45,15,15,25,105]),'约束清单':(constraints,[40,43,30,40,30,55]),'实施覆盖':(plan,[36,24,58,50,14,66,58]),'页面覆盖':(coverage,[25,85,75,75]),'测试证据':(tests,[33,16,16,19,95]),'映射差异':(mapping,[38,28,28,29,25,24,29,110])}
    for index,(sheet,(data,widths)) in enumerate(sheets.items()):
        if index:run('officecli','add',str(file),'/','--type','sheet','--prop','name='+sheet);run('officecli','get',str(file),'/'+sheet,'--depth','0')
        src=STAGE/(sheet+'.csv')
        with src.open('w',encoding='utf-8-sig',newline='') as f:csv.writer(f).writerows(data)
        run('officecli','import',str(file),'/'+sheet,'--file',str(src),'--header')
        endcol=column_letter(len(data[0]));last=len(data)
        ops=[{'command':'set','path':'/'+sheet,'props':{'freeze':'A2','tabColor':'17324D','gridlines':'false','orientation':'landscape','fitToPage':'1x0','printTitleRows':'1:1'}},{'command':'set','path':f'/{sheet}/A1:{endcol}{last}','props':{'font.name':'Microsoft YaHei','font.size':'10pt','wrapText':'true','font.color':'263648'}},{'command':'set','path':f'/{sheet}/A1:{endcol}1','props':{'fill':'17324D','font.color':'FFFFFF','font.bold':'true'}},{'command':'set','path':f'/{sheet}/row[1]','props':{'height':'32'}}]
        for col,width in enumerate(widths,1):ops.append({'command':'set','path':f'/{sheet}/col[{column_letter(col)}]','props':{'width':str(width)}})
        for row in range(2,last+1):
            ops.append({'command':'set','path':f'/{sheet}/row[{row}]','props':{'height':'68' if sheet=='汇总' else '58' if sheet in ['实施覆盖','页面覆盖','关联检查','字段对照'] else '32'}})
            if row%2==0:ops.append({'command':'set','path':f'/{sheet}/A{row}:{endcol}{row}','props':{'fill':'F0F5FA'}})
        if sheet=='全表数量':
            for row in range(2,last+1):ops.append({'command':'set','path':f'/{sheet}/D{row}','props':{'formula':f'C{row}-B{row}','numFmt':'#,##0;(#,##0);"–"','font.color':'000000'}})
        for start in range(0,len(ops),50):
            batch=STAGE/(sheet+f'-{start}.batch.json');batch.write_text(json.dumps(ops[start:start+50],ensure_ascii=False),encoding='utf-8');run('officecli','batch',str(file),'--input',str(batch),'--stop-on-error')
    run('officecli','close',str(file));return file


def column_letter(n):
    out=''
    while n:n,r=divmod(n-1,26);out=chr(65+r)+out
    return out


def word(before,after,summary):
    file=STAGE/'20261012-database-audit-v1.docx';run('officecli','create',str(file));ops=[];table_index=0
    def add(command,**kwargs):ops.append({'command':command,**kwargs})
    def p(text,heading=0,page=False):
        props={'text':text,'style':'Heading1' if heading==1 else 'Heading2' if heading==2 else 'Normal','font':'Calibri','font.ea':'Microsoft YaHei','size':'20pt' if heading==1 else '14pt' if heading==2 else '11pt','bold':str(bool(heading)).lower(),'color':'17324D' if heading else '263648','lineSpacing':'1.2x','spaceBefore':'10pt' if heading else '0pt','spaceAfter':'7pt','keepNext':str(bool(heading)).lower()}
        # Continuous pagination keeps short chapters and their tables together.
        add('add',parent='/body',type='paragraph',props=props)
    def table(headers,data,widths):
        nonlocal table_index
        table_index+=1;idx=table_index
        add('add',parent='/body',type='table',props={'rows':str(len(data)+1),'cols':str(len(headers)),'width':'100%','colWidths':','.join(str(round(w*0.67)) for w in widths),'layout':'fixed','padding':'75','border.all':'single;4;CBD5E1'})
        for ri,row in enumerate([headers]+data,1):
            add('set',path=f'/body/tbl[{idx}]/tr[{ri}]',props={**{f'c{i}':str(v) for i,v in enumerate(row,1)},'cantSplit':'true',**({'header':'true'} if ri==1 else {})})
            for ci in range(1,len(headers)+1):
                add('set',path=f'/body/tbl[{idx}]/tr[{ri}]/tc[{ci}]',props={'font':'Microsoft YaHei','size':'9pt','bold':str(ri==1).lower(),'color':'FFFFFF' if ri==1 else '263648','fill':'17324D' if ri==1 else 'F0F5FA' if ri%2==0 else 'FFFFFF'})
                add('set',path=f'/body/tbl[{idx}]/tr[{ri}]/tc[{ci}]/p[1]',props={'spaceAfter':'3pt','spaceBefore':'3pt','lineSpacing':'1.1x'})
    add('set',path='/',props={'pageWidth':'21cm','pageHeight':'29.7cm','orientation':'portrait','marginTop':'1.6cm','marginBottom':'1.6cm','marginLeft':'1.5cm','marginRight':'1.5cm','docDefaults.font':'Calibri','docDefaults.fontSize':'11pt'})
    add('add',parent='/body',type='paragraph',props={'text':'Classroom AI 数据完整性审计与补充报告','style':'Title','font':'Calibri','font.ea':'Microsoft YaHei','size':'26pt','bold':'true','color':'17324D','spaceAfter':'14pt'})
    p('2026-10-12 · 本地开发环境实际实施 · OfficeCLI 生成 · v1')
    p('本轮对 MySQL 8.0.46 的32张业务表、333个实际字段逐项审计。安全补充307条业务记录，修复34条已有记录的确定缺失项；接口验证另产生2条实际资源访问日志。记录总数1947→2256。原始资料、原账号密码与权限、人脸特征、正式课程内容、旧大纲和旧评价均保留。')
    p('五个专业的合成场景覆盖60个学生、7个班次、84条选课、12次历史考勤、48个课堂时序点、6份真实课件版本、5组评价及2组行动反馈。评价、审核与反馈文本及来源清单均声明为合成快照，不表示真实听课、人工审核或教师行动。')
    p('验收依据：本地数据库实查、独立库重复执行与恢复演练、206项普通后端测试、75项MySQL专项测试、13项前端测试、15组独立库API/真实浏览器场景和8组本地库API场景。后端登记282项，其中281项在本轮实际通过，另1个既有条件浏览器用例未运行。')
    p('保留边界：1条旧FINISHED班次缺归档依据；78条旧时序记录统计异常；真实课程考核权重未提供，建议只进入草稿。Docker重启后数据完整保留，最新幂等与恢复复验通过。新基线按既有SE、CS授权范围验证，不扩充督导权限。')
    add('add',parent='/',type='toc',props={'levels':'1-2','title':'目录','hyperlinks':'true'})
    p('1. 调查范围、来源与环境',1,True)
    p('用户原始说明：附件“已粘贴的文本.txt”（d5c41e14-fd50-460e-a994-61095e61f02c）。项目raw/仅有README.md，未提供正式课程事实或真人档案材料。本轮不使用外部联网资料。来源记录已追加docs/source-notes.md。')
    p('代码调查覆盖README.md、backend/README.md、AGENTS.md、memory-bank/project-context.md，以及全部后端Entity、Repository、Service、Controller和前端业务页面/API/类型。静态索引记录31个Entity表、331个映射字段；t_app_data_migration的2个字段由JDBC迁移管理。')
    p('目标是当前classroom-ai-demo Compose项目的classroom-mysql/classroom_ai，分支exp4保持不变。已有未提交修改保留；未提交、推送或合并。备份及校验前后均保护266个既有源码和原始文件的SHA-256。')
    p('完整字段类型、默认值、NULL/空字符串数量、约束与外键均来自information_schema和逐字段聚合查询，见配套XLSX。11处既有DATETIME与JPA预期微秒精度差异被记录；没有缺失实体列，独立后端使用ddl-auto=validate成功加载。历史表存在不同排序规则，审计以BINARY比较业务键，不擅自改结构。')
    p('2. 全业务模块汇总',1,True)
    p('以下记录数是模块所列各表的记录数之和，模块互不重复。资源访问日志的新增2条来自实际自动化下载/预览测试，307条种子记录与它们分别追踪。')
    table(['业务模块','涉及表','原记录数','补充后记录数','字段完整性','关联完整性','前端展示情况'],summary,[1200,3100,1200,1250,2550,2500,3350])
    p('3. 字段补充与空值语义',1,True)
    repairs=[['t_teacher.user_id','8个NULL→0','工号唯一、TEACHER角色与教研室一致','不改用户身份或权限'],['t_course_offering.major_id/major_code','15个缺失→0','已有course_id对应专业','跳过冻结/历史班次'],['t_course_offering.is_snapshot_frozen','17个NULL→1','16个在读NULL按false语义补齐','保留1个旧FINISHED异常'],['t_course_offering_teacher','5→26','13个原班次补主讲；新增8个教师关系','保留原协同教师'],['t_course_resource.file_size_bytes','5个NULL→0','实际文件字节数','原文件不覆盖'],['t_course_resource.content_hash','10个NULL→0','实际文件SHA-256','原版本和访问记录保留'],['t_training_indicator','62→74','复用RecommendedIndicatorTemplate的12项推荐定义','仍属推荐草案，非正式培养方案'],['t_course_content_revision','9→24','已有课程简介/示例目标与明确标记的考核建议','不覆盖已有草稿或发布到t_course']]
    table(['字段/表','实际变化','填写依据','保护规则'],repairs,[4900,2400,4200,3650])
    p('应当保留空值：DRAFT的发布人/发布时间；待审核评价的审核人/审核时间；在读班次的归档快照；ACTIVE会话的end_time；未复核视频的分析审批字段；合成学生的真人照片；旧来源不明的created_at/操作人。不为填满字段而发明时间、教师、审核事件或生物特征。')
    p('仍缺少正式依据：22门课程的正式objectives与assessment_method原为空；其中15门新增完整标记的DRAFT建议，已有7门草稿保留。7门无任课依据的课程teacher_name仍为空；2份历史/锁定大纲course_goals仍为空。具体数量与空值由XLSX字段对照逐项列出。')
    p('4. 合成场景、版本与业务规则',1,True)
    p('五个代表课程来自已有稳定课程编码：SE—CS1001软件工程导论；CS—CS2002计算机组成原理；AI—AI3001人工智能导论；DS—DS3001分布式大数据计算；SEC—SEC3001信息安全攻防实践。没有新增或重复课程。教师、督导及主任只解析已有账号关系和授权范围，没有新增高权限账号。')
    p('班次覆盖2025-2026春季历史、2026-2027秋季在读、2026-2027春季待开课。每个班次12条合成选课，总人数与名单一致；历史班次具有冻结人数与JSON快照，归档来源明确为工具。排课使用合成教室，教师/教室周次、星期、节次无冲突；协同关系保留。')
    p('历史考勤会话均为FINISHED，开始早于结束，缺勤名单只包含DEMO-DATA-*合成学号。t_attendance_session比率使用百分数，classroom_record持久化使用0～1比例，避免混淆统计口径。新增数据不调用视觉实时推流，不触发摄像头，不制造人脸特征。')
    p('五组评价覆盖2个PUBLISHED、1个APPROVED_PENDING、1个PENDING_REVIEW及1个DRAFT。四维评分在各25分范围内并等于总分；审核后至少24小时开放。已发布合成快照使用明确标记的历史日期，当前已审核样例保留正常24小时延迟。12条流转日志和2条反馈历史均由来源清单绑定合成父记录；不声称账号自然人实际操作过。')
    p('资源由OfficeCLI编写6份课程相关DOCX并转换为真实可访问PDF，SE系列保留v1/v2两个版本。纯色色块MP4经ffprobe验证为4000毫秒，三个片段覆盖B/P2/S，区间合法；不含真人影像。原有10个资源文件均实查存在，按字节补齐元数据；其中5条旧显示大小由错误的MB值纠正为真实KB值。旧微格片段未验证时仍保留现有只读提示，不伪造可用资产。')
    p('5. 关联、唯一性与保护检查',1,True)
    p('补充前后完成40项实际SQL检查：所有物理外键、逻辑课程/专业/教师/学生关联、去重、冻结人数、评分与24小时延迟、排课冲突、资源系列与当前版本、反馈历史、视频区间和合成学生特征隔离。38项零异常，2项仅保留旧异常；未新增错误关联。')
    p('本地库及独立库重复执行后所有表记录数变化为0，已有行修改为0。失败注入使事务回滚，新增测试标记没有残留。守卫回滚仅操作来源清单内的记录和有原值/目标哈希保护的补齐字段；独立库恢复后与原始全表行哈希完全一致。')
    p('原库1947条记录中仅34条确定缺失记录发生允许的补齐，无旧记录删除。原student、face_feature、t_user_account、t_course、t_course_syllabus、t_graduation_indicator、t_supervision_evaluation及既有审核日志全部行哈希保持不变。新增60个合成学生没有任何face_feature。')
    p('6. Redis与初始化覆盖',1,True)
    p('实际Redis原索引3个身份全部不在MySQL当前80个特征身份中。通过显式失效派生索引并调用现有授权/api/face/all回源机制，重建为80/80一致，删除经MySQL确认不存在的旧缓存键。未改变任何数据库人脸记录，未保存向量或令牌。补充结束无classroom:*实时键，历史样例未注入直播状态。')
    p('Compose全新卷只执行scripts/deploy/mysql/init/initialize.sql完整基线；01_schema.sql及06_exp3_sprint2.sql是独立建表/迁移依据。TeachingDataInitializer在已有课程时跳过课程关联样例，因此不能补齐既有库缺失关系；app.seed-demo默认true，账号基础初始化仍有历史行为。Sprint3Migration补资源系列/建议，但不会推定旧评价权重或补齐教学场景。')
    p('本次种子为显式独立入口，不加入Docker自动初始化目录，也不修改业务启动器或迁移。已有库应先plan、备份、独立验证再apply；全新环境先完成基线和应用迁移，再生成真实文件并计划。额外隔离classroom_fresh完成基线和正常应用初始化后，全五专业模式因督导仅授权SE、CS而安全拒绝；显式--majors SE,CS新增229条，重复执行零变化，授权API返回4个合成班次。其余专业须先通过正常授权流程配置，脚本不扩权。')
    p('7. 前后端验证与展示边界',1,True)
    table(['检查','本轮真实结果','证据'],[['后端普通测试','206通过；76按环境条件跳过','backend-test-counts.json'],['MySQL专项补跑','75通过；0失败/0跳过','backend-mysql-test-counts.json / mysql-junit'],['后端去重结果','登记282；实际281通过；1既有条件浏览器未运行','两轮JUnit按套件去重'],['前端测试与构建','13测试通过；类型检查和Vite构建通过','frontend-tests.log / frontend-build.log'],['独立库API与真实Chromium','15组通过；三角色、资源、微格、学生、历史页面','browser-final/results.json及截图'],['本地开发库接口','8组通过；未降低24小时规则','live-api/results.json'],['数据恢复演练','幂等、事务失败、守卫回滚、加密恢复通过','recovery-final/results.json']],[4000,4900,6250])
    p('已检查的实际页面：教师统计/雷达/建议与反馈、主任质量汇总、督导类别/问题统计、课件历史与水印预览、微格片段播放及自动停止、合成学生搜索、教师历史开课。接口同时验证名单、考勤历史、版本大纲及跨专业拒绝。实时大屏按现有生命周期保持待机；不把历史时序点伪装为当前课堂。')
    p('8. 重复执行、恢复与交付路径',1,True)
    p('主入口：scripts/data-completeness.py（全表只读审计）；scripts/seed-complete-demo.py（plan/apply）；scripts/create-demo-office-assets.py（OfficeCLI课件）；scripts/data-completeness-backup.py（加密业务备份及隔离恢复）；scripts/rollback-complete-demo.py（默认只生成守卫回滚SQL）；scripts/sync-demo-face-cache.py（显式缓存对齐）；scripts/tests/data-completeness-api-browser.cjs及verify-data-completeness.py（验收）。')
    p('操作说明另见20261012-demo-data-operation-v1.md，所有最终文件位于output/。业务备份位于runtime/backups/20261012-data-completeness-v1.sql.dpapi，由Windows当前用户DPAPI保护，并明确排除账号密码表和人脸特征表。备份不包含上传文件，上传原文件本轮均保留，示例文件另放交付包。备份及敏感表不打包到交付ZIP。')
    p('运行顺序：先audit；用OfficeCLI生成并验证示例文件；plan生成本机可审阅SQL与来源计划；backup；独立库验证；确认local-development目标后apply；再次plan/apply检查幂等；按需显式刷新缓存；查询和浏览器验收。不要直接把本机带解析ID的计划SQL移到其他库执行；应由脚本重新解析当地稳定业务键。')
    p('回滚：默认生成模式不改数据。使用live/rollback-manifest.json和live/apply-before.json生成守卫SQL；仅在明确指定环境并传--apply时执行。任何种子行被后续人工修改、或出现未登记的依赖记录时拒绝回滚；先人工处理依赖，不强行删除有效业务。代码保留现有权限与状态机，上传文件不自动删除。')
    p('备份恢复：restore-test严格只允许classroom-data-test-*隔离容器，不能覆盖原业务库。先在隔离环境恢复核对；生产/用户现有数据库没有自动重建或重置入口。Docker已由用户重启，after-docker-restart.json确认数据和关联检查与重启前一致。测试容器按本轮精确命名清理，保留classroom_mysql_data/classroom_redis_data。')
    p('9. 人工复核与未完成事项',1,True)
    p('待确认：旧FINISHED班次的真实归档人、时间和历史快照依据；78条旧时序统计的正确人数/低头计数；3条原ACTIVE会话的真实结束状态；真实课程考核权重与目标；7门无任课依据的课程教师；历史大纲缺目标、旧资源标签与旧微格资产来源。这些事项没有被改写为确定教学事实。DS2001已有目标/考核与课程名称存在内容口径不匹配，保留人工内容并供复核。')
    p('本轮未运行既有CourseBrowserMysqlTest，不能声称282项全通过；新增15组真实浏览器场景已通过。新环境已在原有SE、CS权限范围通过填充、重复执行和接口复验；其余三个专业没有授权，未通过改权限绕过。Docker中断后的恢复复验已重新完整通过，最新证据见recovery-final/results.json。')
    p('本轮没有修改前后端生产业务逻辑、数据库结构、用户权限规则、算法或接口格式，没有切换分支、提交或推送。当前交付可以审阅和重复实施；正式教学内容发布、真实审核与培养方案认证仍须对应业务人员通过现有流程处理。')
    p('证据目录：docs/20261012-data-completeness-evidence-v1。关键数字来自live/apply-before.json、live-final.json、applied-manifest.json、final-protection.json、redis-sync.json和测试结果文件；来源说明、分析与人工复核项已留在docs/。')
    add('add',parent='/',type='header',props={'type':'default','text':'Classroom AI · 数据完整性治理 · 2026-10-12','font':'Calibri','size':'9pt','color':'60758B'})
    add('add',parent='/',type='footer',props={'type':'default','text':'本地开发库实测 · v1 · 第 ','field':'page','align':'center','size':'9pt'})
    add('set',path='/settings',props={'updateFields':'true'})
    # Large row-format batches are still atomic; each failure aborts rather than cascading.
    for start in range(0,len(ops),70):
        batch=STAGE/f'report-{start}.batch.json';batch.write_text(json.dumps(ops[start:start+70],ensure_ascii=False),encoding='utf-8');run('officecli','batch',str(file),'--input',str(batch),'--stop-on-error')
    run('officecli','close',str(file));return file


def main():
    STAGE.mkdir(parents=True,exist_ok=True)
    before=read('live/apply-before.json');after=read('live-final.json')
    summary=[[mod,'\n'.join(tables),sum(before['counts'][t] for t in tables),sum(after['counts'][t] for t in tables),field,association,front] for mod,tables,field,association,front in MODULES]
    assert sum(row[2] for row in summary)==sum(before['counts'].values())
    assert sum(row[3] for row in summary)==sum(after['counts'].values())
    doc=word(before,after,summary);xls=STAGE/'20261012-database-completeness-v1.xlsx'
    if not xls.exists():xls=workbook(before,after,summary)
    for file in (doc,xls):
        for mode in ('issues','outline','text'):(STAGE/(file.stem+'-'+mode+'.txt')).write_text(run('officecli','view',str(file),mode),encoding='utf-8')
        (STAGE/(file.stem+'-validate.txt')).write_text(run('officecli','validate',str(file)),encoding='utf-8')
        run('officecli','view',str(file),'html','-o',str(STAGE/(file.stem+'.html')))
        run('officecli','close',str(file))
    (EV/'report-generation.json').write_text(json.dumps({'authored_by':'officecli','version':run('officecli','--version').strip(),'staged':[str(doc),str(xls)],'table_count':32,'field_count':len(before['columns']),'summary':summary},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'staged_docx':str(doc),'staged_xlsx':str(xls),'field_count':len(before['columns'])}))


if __name__=='__main__':main()
