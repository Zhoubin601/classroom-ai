import html, json
from pathlib import Path

def esc(s): return html.escape(str(s), quote=True)

class Diagram:
    def __init__(self, slug, title, subtitle, width, height, sources):
        self.slug, self.title, self.subtitle = slug, title, subtitle
        self.width, self.height, self.sources = width, height, sources
        self.nodes, self.edges, self.groups = [], [], []

    def node(self, id, x, y, w, h, title, lines=(), tone='blue'):
        self.nodes.append(dict(id=id,x=x,y=y,w=w,h=h,title=title,lines=list(lines),tone=tone))

    def group(self, x,y,w,h,title):
        self.groups.append(dict(x=x,y=y,w=w,h=h,title=title))

    def edge(self,a,b,label,points,labelpos=None,logic=False):
        self.edges.append(dict(a=a,b=b,label=label,points=points,labelpos=labelpos,logic=logic))

    def mermaid(self):
        rows = ['%% 2026-10-10 / fix-exp3 / 7464f9c / v1', '%% '+self.subtitle, 'flowchart TB']
        for n in self.nodes:
            label = '<br/>'.join([n['title']]+n['lines']).replace('"','&quot;')
            rows.append('  %s["%s"]' % (n['id'], label))
        for e in self.edges:
            connector = '-.->' if e['logic'] else '-->'
            label = '|"'+e['label'].replace('"','&quot;')+'"|' if e['label'] else ''
            rows.append('  %s %s%s %s' % (e['a'],connector,label,e['b']))
        rows.append('%% 来源：'+self.sources)
        return '\n'.join(rows)+'\n'

    def svg(self):
        tone = {'blue':('#2563eb','#eff6ff'),'green':('#047857','#ecfdf5'),'purple':('#7c3aed','#f5f3ff'),'gray':('#475569','#f8fafc'),'amber':('#a16207','#fffbeb')}
        rows = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}" role="img" aria-labelledby="title desc">',
                '<title id="title">'+esc(self.title)+'</title>', '<desc id="desc">'+esc(self.subtitle)+'</desc>',
                '<defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto"><path d="M0,0 L9,4.5 L0,9" fill="#64748b"/></marker></defs>',
                '<style>text{font-family:"Microsoft YaHei","PingFang SC",Arial,sans-serif;fill:#1e293b}.head{font-size:36px;font-weight:700}.sub{font-size:20px;fill:#475569}.node-title{font-size:24px;font-weight:700}.node-line{font-size:19px;fill:#475569}.edge-label{font-size:18px;fill:#475569;paint-order:stroke;stroke:#fff;stroke-width:8px;stroke-linejoin:round}.footer{font-size:16px;fill:#64748b}.group-label{font-size:20px;fill:#475569;font-weight:700}</style>',
                f'<rect width="{self.width}" height="{self.height}" fill="#fff"/>',
                '<rect x="40" y="40" width="5" height="54" rx="2" fill="#2563eb"/>',
                f'<text class="head" x="65" y="70">{esc(self.title)}</text>',
                f'<text class="sub" x="65" y="106">{esc(self.subtitle)}</text>']
        for g in self.groups:
            rows += [f'<rect x="{g["x"]}" y="{g["y"]}" width="{g["w"]}" height="{g["h"]}" rx="18" fill="#f8fafc" stroke="#cbd5e1" stroke-width="2"/>',f'<text class="group-label" x="{g["x"]+22}" y="{g["y"]+32}">{esc(g["title"])}</text>']
        for e in self.edges:
            dash = ' stroke-dasharray="8 7"' if e['logic'] else ''
            rows.append('<polyline points="'+ ' '.join('%s,%s'%tuple(p) for p in e['points'])+'" fill="none" stroke="#64748b" stroke-width="2.5" stroke-linejoin="round"'+dash+' marker-end="url(#arrow)"/>')
            if e['labelpos']:
                x,y=e['labelpos']
                for i,line in enumerate(e['label'].split('\n')):
                    rows.append(f'<text class="edge-label" text-anchor="middle" x="{x}" y="{y+25*i}">{esc(line)}</text>')
        for n in self.nodes:
            accent, fill = tone[n['tone']]
            rows += [f'<g data-node="{n["id"]}" data-x="{n["x"]}" data-y="{n["y"]}" data-w="{n["w"]}" data-h="{n["h"]}">',
                     f'<rect x="{n["x"]}" y="{n["y"]}" width="{n["w"]}" height="{n["h"]}" rx="14" fill="{fill}" stroke="{accent}" stroke-width="1.6"/>',
                     f'<text class="node-title" x="{n["x"]+22}" y="{n["y"]+38}">{esc(n["title"])}</text>']
            for i,line in enumerate(n['lines']):
                rows.append(f'<text class="node-line" x="{n["x"]+22}" y="{n["y"]+72+31*i}">{esc(line)}</text>')
            rows.append('</g>')
        rows += [f'<line x1="60" y1="{self.height-74}" x2="{self.width-60}" y2="{self.height-74}" stroke="#e2e8f0"/>',
                 f'<text class="footer" x="60" y="{self.height-44}">源码基线：fix-exp3 / 7464f9c　·　2026-10-10　·　来源：{esc(self.sources)}</text>', '</svg>']
        return '\n'.join(rows).replace('aria-labelledby="title desc"', 'aria-labelledby="'+self.slug+'-title '+self.slug+'-desc"').replace('id="title"', 'id="'+self.slug+'-title"').replace('id="desc"', 'id="'+self.slug+'-desc"').replace('id="arrow"', 'id="'+self.slug+'-arrow"').replace('url(#arrow)', 'url(#'+self.slug+'-arrow)')

def make():
    ds=[]
    d=Diagram('business-overview','01 / 业务全景','按当前页面与业务实现整理；教学 Demo，不代表学校正式制度。',1560,940,'App.vue；course / resource / supervision / attendance；视觉控制器')
    d.node('P',480,155,600,110,'爱教学 / Classroom AI Demo',['三个业务角色 · 课程档案与开课班次'], 'gray')
    cards=[('C',60,'课程与开课管理',['课程建档与 CSV 导入','主讲 / 协同教师与选课名单','排课时段和冲突检查','结课快照与历史统计'],'blue'),('T',430,'教学内容与资源',['内容草稿 / 正式发布','目录版本 / 大纲指标映射','课件标签与同室共享','授权水印预览与访问日志'],'green'),('Q',800,'督导评价与质量',['授权专业组合检索','评价暂存 / 提交 / 主任审核','延迟匿名反馈与雷达','学期覆盖率与评价明细'],'purple'),('V',1170,'课堂辅助',['班次摄像头与考勤会话','人脸 / 姿态与实时指标','单次考勤结束与归档','微格上传 / 挂载 / 播放'],'amber')]
    for id,x,title,lines,tone in cards:
        d.node(id,x,375,320,260,title,lines,tone)
        d.edge('P',id,'',[(780,265),(780,320),(x+160,320),(x+160,375)])
    d.node('NOTE',60,735,1440,100,'阅读顺序：业务 → 运行 → 权限 → 数据 → 状态',['功能入口、数据范围、业务状态分别核对；软件验收记录与正式批准证据分别说明。'],'gray')
    ds.append(d)

    d=Diagram('runtime-architecture','02 / 技术与本机运行结构','默认 Windows 本机 Vite + Python，Docker Compose 运行 Java / MySQL / Redis。',1640,1130,'Vite配置与camera-plugin；Compose；backend/Dockerfile；视觉传输')
    d.group(1070,290,500,650,'Docker Compose / 本机 Docker')
    d.node('U',560,155,450,100,'浏览器 / 三个角色',['JWT Bearer 认证请求'],'gray')
    d.node('F',70,340,360,180,'Vue / Vite · 5173',['业务 API 与静态照片代理','本地设备入口在插件中','静态 dist 不提供设备启停'],'blue')
    d.node('L',70,620,360,150,'本地摄像头插件',['身份 / 班次 / 活动会话核验','单个监控进程的生命周期'],'blue')
    d.node('P',620,620,380,150,'Python 视觉 · 8088',['InsightFace / 认证传输','按班次加载人脸上下文'],'purple')
    d.node('CAM',620,850,380,120,'摄像头与模型环境',['原生设备 / CUDA 与模型缓存'],'purple')
    d.node('B',1110,355,400,140,'Spring Boot · 8080',['Java21 / 业务权限 / 文件接口','数据库重载账号授权'],'green')
    d.node('DB',1100,560,220,120,'MySQL8.0',['宿主3308 → 3306'],'green')
    d.node('R',1340,560,200,120,'Redis7.2',['6379 / AOF'],'green')
    d.node('O',1110,780,400,110,'LibreOffice / 镜像内部',['Office 转 PDF，不是独立服务'],'gray')
    d.node('FILES',70,860,380,110,'runtime/uploads',['faces / resources / micro'],'amber')
    d.edge('U','F','页面与请求',[(560,205),(250,205),(250,340)],(390,190))
    d.edge('F','B','业务 API 代理',[(430,420),(1110,420)],(740,403))
    d.edge('F','L','本地设备入口',[(250,520),(250,620)],(330,565))
    d.edge('L','P','启动 / 停止',[(430,695),(620,695)],(525,674))
    d.edge('P','B','认证上下文 / 指标上报',[(1000,700),(1035,700),(1035,450),(1110,450)],(825,500))
    d.edge('P','F','视频流经插件代理',[(810,620),(810,565),(460,565),(460,490),(430,490)],(650,549))
    d.edge('CAM','P','画面与推理',[(810,850),(810,770)],(897,814))
    d.edge('B','DB','',[(1210,495),(1210,560)])
    d.edge('B','R','',[(1415,495),(1440,530),(1440,560)])
    d.edge('B','O','文件转换',[(1510,460),(1552,460),(1552,732),(1310,732),(1310,780)],(1390,720))
    d.edge('B','FILES','数据库元数据与本地文件分别交接',[(1110,480),(1025,480),(1025,995),(260,995),(260,970)],(630,1022))
    ds.append(d)

    d=Diagram('role-scopes','03 / 角色、数据范围与关键操作','权限由角色 + 操作 + 数据范围 + 状态共同决定；完整细节见权限矩阵。',1560,1030,'SecurityConfig；CourseAuthorizationService；ResourceAccessService；AttendanceAccessService')
    specs=[('D',60,'教研室主任 / DIRECTOR',['账号 department 对应本室','牵头 / 显式参与专业管理范围'],['课程建档、班次排课与名单','大纲锁定 / 本室评价审核','微格与课件维护','学生底库 / 质量CSV全量Demo'],'blue'),('T',560,'任课教师 / TEACHER',['主讲工号或协同教师关联','班次可读不自动扩到其他班次'],['教学内容草稿与正式发布','本人课程大纲、课件与微格','同室共享课件可预览','原文件下载仍要求课程写权'],'green'),('S',1060,'教学督导 / SUPERVISOR',['authorizedMajors 授权专业','缺专业 / 无授权默认拒绝'],['课程、大纲、资源只读审查','授权班次评价暂存与提交','微格视频授权预览','不能维护课程、课件与微格'],'purple')]
    for id,x,title,scope,actions,tone in specs:
        d.node(id,x,160,440,90,title,[],tone)
        d.node(id+'S',x,325,440,145,'数据范围',scope,tone)
        d.node(id+'W',x,545,440,230,'关键操作',actions,tone)
        d.edge(id,id+'S','',[(x+220,250),(x+220,325)])
        d.edge(id+'S',id+'W','',[(x+220,470),(x+220,545)])
        d.edge(id+'W','A','',[(x+220,775),(x+220,855)])
    d.node('A',60,855,1440,100,'三角色共同：可读且未冻结的班次，允许开启 / 更新 / 结束考勤',['结束单次考勤 ≠ 主任结课归档；督导的“课程只读”不等于所有业务只读。'],'amber')
    ds.append(d)

    d=Diagram('data-relations','04 / 核心数据关系','源码实体关系摘要：实线为 JPA 关联，虚线为 ID / 编码 / 版本逻辑关联。',1900,1410,'course/entity；resource/entity；supervision/entity；attendance/entity；代码实体索引CSV')
    d.node('M',60,165,330,110,'专业 Major',['t_major / 牵头 department'],'blue')
    d.node('MD',60,355,330,110,'参与教研室关联',['t_major_department'],'blue')
    d.node('PL',540,165,430,140,'培养方案示例指标目录',['t_training_indicator','majorCode + planVersion'],'blue')
    d.node('C',1060,165,370,110,'课程档案 Course',['t_course / department'],'green')
    d.node('CR',1510,165,330,110,'教学内容修订',['t_course_content_revision'],'green')
    d.node('SY',1040,445,360,130,'大纲版本',['t_course_syllabus','version / planVersion'],'green')
    d.node('GI',560,445,390,130,'课程指标映射',['t_graduation_indicator','course_id / syllabus_id'],'green')
    d.node('RE',1510,445,330,130,'教学课件',['t_course_resource','course_id'],'purple')
    d.node('MI',1510,665,330,130,'微格切片 / 上传视频',['t_micro_teaching_slice','course_id / sliceUrl'],'purple')
    d.node('O',1040,885,360,140,'开课班次',['t_course_offering','course_id / 学期 / 主讲'],'green')
    d.node('OT',560,885,390,140,'班次教师关联',['t_course_offering_teacher','offeringId / teacherId'],'amber')
    d.node('EN',60,885,400,140,'班次选课名单',['t_offering_student_enrollment','offeringId / studentNumber'],'amber')
    d.node('SC',60,1145,400,120,'排课时段',['t_course_schedule / offering_id'],'green')
    d.node('AT',560,1145,390,120,'考勤会话',['t_attendance_session / offering_id'],'green')
    d.node('EV',1040,1145,360,120,'督导评价',['t_supervision_evaluation','offering_id'],'purple')
    d.node('AU',1510,1145,330,120,'评价审核日志',['t_evaluation_audit_log','evaluationId'],'purple')
    d.node('NOTE',60,665,850,125,'模型边界',['标量ID/编码关系不等于已核验的数据库外键。','student、face_feature、classroom_record等见完整实体索引。'],'gray')
    d.edge('M','MD','major_id',[(225,275),(225,355)],(310,318))
    d.edge('M','PL','majorCode',[(390,235),(540,235)],(465,215),True)
    d.edge('M','C','majorId / majorCode',[(225,165),(225,135),(1245,135),(1245,165)],None,True)
    d.edge('C','CR','courseId',[(1430,220),(1510,220)],None,True)
    d.edge('C','SY','course_id',[(1245,275),(1245,445)],(1320,355))
    d.edge('PL','SY','按专业与方案版本匹配',[(755,305),(755,355),(1000,355),(1000,510),(1040,510)],(815,338),True)
    d.edge('SY','GI','syllabus_id',[(1040,525),(950,525)],None)
    d.edge('C','GI','course_id',[(1130,275),(1130,310),(980,310),(980,475),(950,475)],None)
    d.edge('C','RE','course_id',[(1430,250),(1465,250),(1465,510),(1510,510)],None)
    d.edge('C','MI','course_id',[(1405,275),(1438,275),(1438,730),(1510,730)],None)
    d.edge('C','O','course_id',[(1060,260),(1020,260),(1020,955),(1040,955)],None)
    d.edge('O','OT','offeringId',[(1040,960),(950,960)],None,True)
    d.edge('O','EN','offeringId',[(1245,1025),(1245,1080),(260,1080),(260,1025)],(650,1060),True)
    d.edge('O','SC','offering_id',[(1060,1025),(1000,1025),(1000,1110),(260,1110),(260,1145)],None)
    d.edge('O','AT','offering_id',[(1140,1025),(1140,1050),(755,1050),(755,1145)],None)
    d.edge('O','EV','offering_id',[(1320,1025),(1320,1145)],(1410,1100))
    d.edge('EV','AU','evaluationId',[(1400,1215),(1510,1215)],None,True)
    ds.append(d)

    d=Diagram('evaluation-workflow','05 / 督导评价状态与角色协作','按 SupervisionServiceImpl 实际状态转换绘制；反馈延迟默认 24 小时、可配置。',1600,1010,'SupervisionServiceImpl；SupervisionController；application.yml')
    d.group(60,160,500,630,'教学督导 / 本人授权班次评价')
    d.group(620,160,400,630,'教研室主任 / 本室审核')
    d.group(1080,160,460,630,'到期状态刷新 / 教师读取')
    d.node('D',130,270,320,120,'DRAFT / 草稿',['督导暂存 / 可编辑删除'],'purple')
    d.node('P',670,270,300,120,'PENDING_REVIEW',['待主任审核'],'blue')
    d.node('R',130,530,320,145,'REJECTED / 退回',['本人可修改、重提或删除'],'purple')
    d.node('A',670,530,300,145,'APPROVED_PENDING',['审核通过 / 设置发布时间'],'blue')
    d.node('F',1150,530,300,145,'PUBLISHED / 已发布',['教师读取匿名反馈'],'green')
    d.edge('D','P','督导正式提交',[(450,325),(670,325)],(560,300))
    d.edge('P','A','主任通过',[(820,390),(820,530)],(885,485))
    d.edge('P','R','主任驳回',[(670,360),(540,360),(540,600),(450,600)],(535,514))
    d.edge('R','P','修改并重提',[(280,530),(280,440),(760,440),(760,390)],(470,423))
    d.edge('A','F','到期后读取刷新',[(970,600),(1150,600)],(1060,575))
    d.node('NOTE',60,830,1480,100,'审核通过、计入覆盖、教师可见是不同条件',['覆盖率可计入审核通过待发布评价；教师反馈只读PUBLISHED；当前没有独立定时发布调度器。'],'gray')
    ds.append(d)
    return ds
