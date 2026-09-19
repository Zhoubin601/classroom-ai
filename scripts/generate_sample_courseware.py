import os
import sys
from pathlib import Path

# Ensure directory
target_dir = Path(__file__).resolve().parent.parent / "runtime" / "uploads" / "resources"
target_dir.mkdir(parents=True, exist_ok=True)
print(f"Target directory: {target_dir}")

# 1. Generate ch1_overview.docx
try:
    import docx
    from docx.shared import Inches, Pt, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.table import WD_TABLE_ALIGNMENT

    doc = docx.Document()

    # Title
    title_p = doc.add_paragraph()
    title_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title_p.add_run("东北大学软件学院课程教学大纲与实施方案")
    run.font.size = Pt(18)
    run.font.bold = True
    run.font.color.rgb = RGBColor(30, 41, 59)

    sub_p = doc.add_paragraph()
    sub_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    sub_run = sub_p.add_run("《软件项目管理》（课程编号：CS3001）")
    sub_run.font.size = Pt(14)
    sub_run.font.bold = True
    sub_run.font.color.rgb = RGBColor(79, 70, 229)

    doc.add_paragraph("一、基本信息")
    table = doc.add_table(rows=5, cols=4)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    headers = [
        ("课程名称", "软件项目管理", "课程代码", "CS3001"),
        ("主讲教师", "郭军（教授）", "学分/学时", "3.0 学分 / 48 学时"),
        ("授课班级", "软件工程 2024级 2班", "授课地点", "文管 A447"),
        ("先修课程", "《软件工程导论》", "课程性质", "专业核心必修课"),
        ("考核方式", "平时实验30% + 答辩30% + 期末40%", "教学模型", "BOPPPS 参与式教学")
    ]
    for r_idx, row_data in enumerate(headers):
        for c_idx, val in enumerate(row_data):
            cell = table.cell(r_idx, c_idx)
            cell.text = val
            cell.paragraphs[0].runs[0].font.size = Pt(10)

    doc.add_paragraph()
    doc.add_paragraph("二、课程目标与工程认证毕业要求指标点对应矩阵")
    p = doc.add_paragraph(
        "本课程面向中国工程教育专业认证（CEEAA）标准，培养学生在软件工程领域的全周期项目管理、团队协作与工程决策能力：\n"
        "1. 目标 1（工程知识与项目管理）：掌握 MoSCoW 需求分级、WBS 分解、关键路径法 CPM 与敏捷估算。（支撑指标点 1-1, 11-1）\n"
        "2. 目标 2（问题分析与方案设计）：能够针对复杂研发项目建立甘特图排期及工期缓冲池机制。（支撑指标点 2-1, 3-1）\n"
        "3. 目标 3（现代工具使用）：熟练使用 Jira、GitLab、Project 等数字化协同工具进行燃尽图追踪。（支撑指标点 5-1）\n"
        "4. 目标 4（个人团队与职业素养）：具备在人机协同开发团队中承担轮值 DRI、规范沟通与代码评审的职业素养。（支撑指标点 8-2, 9-2, 10-1）"
    )

    doc.add_paragraph()
    doc.add_paragraph("三、章节授课与实验实训进度计划")
    plan_table = doc.add_table(rows=6, cols=4)
    plan_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    plan_headers = ["周次", "章节主题", "授课形式", "实践/实验要求"]
    for c_idx, h in enumerate(plan_headers):
        cell = plan_table.cell(0, c_idx)
        cell.text = h
        cell.paragraphs[0].runs[0].font.bold = True

    plan_rows = [
        ("第 1-2 周", "软件项目管理导论与现代敏捷愿景", "理论授课", "组建敏捷团队，确立项目愿景章程"),
        ("第 3-4 周", "项目启动与 WBS 工作分解结构", "理论 + 实训", "实验一：完成项目 WBS 分解与工期估算"),
        ("第 5-6 周", "敏捷估算 Poker 与 Scrum 燃尽图", "研讨 + 实操", "模拟 Planning Poker 会议与燃尽图绘制"),
        ("第 7-10 周", "进度计划甘特图与 CCPM 缓冲池", "理论 + 实训", "实验二：关键路径计算与项目排期模拟"),
        ("第 11-16 周", "项目风险度量、质量保障与期末答辩", "综合答辩", "期末小组项目实战演示与代码评审报告")
    ]
    for r_idx, r_data in enumerate(plan_rows, start=1):
        for c_idx, val in enumerate(r_data):
            cell = plan_table.cell(r_idx, c_idx)
            cell.text = val
            cell.paragraphs[0].runs[0].font.size = Pt(9.5)

    docx_path = target_dir / "ch1_overview.docx"
    doc.save(docx_path)
    print(f"Generated docx: {docx_path} ({os.path.getsize(docx_path)} bytes)")
except Exception as e:
    print(f"Failed to generate docx: {e}", file=sys.stderr)

# 2. Generate ch1_overview.pdf using reportlab
try:
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib import colors
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont

    pdf_path = target_dir / "ch1_overview.pdf"

    # Register a standard Chinese font if available on Windows
    simhei_path = "C:/Windows/Fonts/simhei.ttf"
    font_name = "Helvetica"
    if os.path.exists("C:/Windows/Fonts/simsun.ttc"):
        pdfmetrics.registerFont(TTFont("SimSun", "C:/Windows/Fonts/simsun.ttc"))
        font_name = "SimSun"
    elif os.path.exists(simhei_path):
        pdfmetrics.registerFont(TTFont("SimHei", simhei_path))
        font_name = "SimHei"

    doc_pdf = SimpleDocTemplate(str(pdf_path), pagesize=A4, rightMargin=40, leftMargin=40, topMargin=40, bottomMargin=40)
    styles = getSampleStyleSheet()

    title_style = ParagraphStyle(
        'TitleStyle',
        parent=styles['Normal'],
        fontName=font_name,
        fontSize=16,
        leading=22,
        alignment=1,
        textColor=colors.HexColor('#1e293b')
    )
    heading_style = ParagraphStyle(
        'HeadingStyle',
        parent=styles['Normal'],
        fontName=font_name,
        fontSize=12,
        leading=18,
        textColor=colors.HexColor('#4f46e5')
    )
    body_style = ParagraphStyle(
        'BodyStyle',
        parent=styles['Normal'],
        fontName=font_name,
        fontSize=9.5,
        leading=14,
        textColor=colors.HexColor('#334155')
    )

    story = [
        Paragraph("<b>东北大学软件学院课程教学大纲与实施方案</b>", title_style),
        Paragraph("《软件项目管理》（CS3001）", heading_style),
        Spacer(1, 15),
        Paragraph("<b>一、基本信息</b>", heading_style),
        Spacer(1, 6),
        Table([
            ["课程名称", "软件项目管理", "课程代码", "CS3001"],
            ["主讲教师", "郭军 (教授)", "学分/学时", "3.0 学分 / 48 学时"],
            ["授课班级", "软件工程2024级2班", "授课地点", "文管 A447"],
            ["考核方式", "实验30% + 答辩30% + 期末40%", "教学模型", "BOPPPS 参与式教学"]
        ], style=[
            ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f8fafc')),
            ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
            ('FONTNAME', (0,0), (-1,-1), font_name),
            ('FONTSIZE', (0,0), (-1,-1), 9),
            ('ALIGN', (0,0), (-1,-1), 'CENTER'),
            ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ]),
        Spacer(1, 15),
        Paragraph("<b>二、毕业要求指标点与教学目标</b>", heading_style),
        Spacer(1, 6),
        Paragraph("1. 指标点 1-1 / 11-1：全面掌握项目 WBS 分解原理、关键路径法 CPM 与工期缓冲池度量。<br/>"
                  "2. 指标点 3-1 / 5-1：能够运用现代化敏捷估算 Poker、甘特图排期工具解决复杂工程管理问题。<br/>"
                  "3. 指标点 9-2 / 12-2：在多角色敏捷团队中担任轮值 DRI，具备终身学习与人机协同开发素养。", body_style),
        Spacer(1, 15),
        Paragraph("<b>三、授课与实验进度安排</b>", heading_style),
        Spacer(1, 6),
        Table([
            ["周次", "章节主题", "授课形式", "实践/实验要求"],
            ["第1-2周", "项目管理全景导论与敏捷愿景", "理论授课", "确立敏捷团队章程与角色分工"],
            ["第3-4周", "项目启动与WBS工作分解结构", "理论+实训", "实验一：WBS树状拆解与甘特图制作"],
            ["第5-6周", "敏捷估算Poker与Scrum燃尽图", "研讨+实训", "Sprint冲刺规划与斐波那契扑克估算"],
            ["第7-10周", "关键路径法CPM与关键链CCPM", "理论+实训", "实验二：关键路径与缓冲池排期"],
            ["第11-16周", "风险管理、质量度量与期末答辩", "综合考核", "团队期末项目实战答辩与复盘"]
        ], style=[
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#e0e7ff')),
            ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#94a3b8')),
            ('FONTNAME', (0,0), (-1,-1), font_name),
            ('FONTSIZE', (0,0), (-1,-1), 8.5),
            ('ALIGN', (0,0), (-1,-1), 'CENTER'),
        ])
    ]
    doc_pdf.build(story)
    print(f"Generated pdf: {pdf_path} ({os.path.getsize(pdf_path)} bytes)")
except Exception as e:
    print(f"Failed to generate pdf: {e}", file=sys.stderr)

# 3. Generate ch3_wbs.pptx using python-pptx
try:
    from pptx import Presentation
    from pptx.util import Inches, Pt
    from pptx.dml.color import RGBColor
    from pptx.enum.text import PP_ALIGN

    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    blank_layout = prs.slide_layouts[6]

    # Slide 1: Cover
    slide1 = prs.slides.add_slide(blank_layout)
    txBox = slide1.shapes.add_textbox(Inches(1.0), Inches(1.8), Inches(11.333), Inches(3.5))
    tf = txBox.text_frame
    p1 = tf.paragraphs[0]
    p1.text = "第3讲：WBS分解与甘特图实战"
    p1.font.size = Pt(40)
    p1.font.bold = True
    p1.font.color.rgb = RGBColor(30, 41, 59)
    p1.alignment = PP_ALIGN.CENTER

    p2 = tf.add_paragraph()
    p2.text = "Work Breakdown Structure & Gantt Chart Scheduling"
    p2.font.size = Pt(20)
    p2.font.color.rgb = RGBColor(79, 70, 229)
    p2.alignment = PP_ALIGN.CENTER

    p3 = tf.add_paragraph()
    p3.text = "\n东北大学软件学院 · 软件项目管理 (CS3001) · 主讲教师：郭军 教授"
    p3.font.size = Pt(16)
    p3.font.color.rgb = RGBColor(100, 116, 139)
    p3.alignment = PP_ALIGN.CENTER

    # Slide 2: Table of contents
    slide2 = prs.slides.add_slide(blank_layout)
    tb = slide2.shapes.add_textbox(Inches(1.0), Inches(0.8), Inches(11.333), Inches(1.0))
    p = tb.text_frame.paragraphs[0]
    p.text = "教学目录与核心目标 (BOPPPS 模型)"
    p.font.size = Pt(28)
    p.font.bold = True
    p.font.color.rgb = RGBColor(30, 41, 59)

    tb2 = slide2.shapes.add_textbox(Inches(1.5), Inches(2.2), Inches(10.0), Inches(4.5))
    tf2 = tb2.text_frame
    topics = [
        "1. 为什么需要 WBS？软件危机与范围蔓延（Scope Creep）痛点解析",
        "2. WBS 核心原则：100% 原则、独立互斥与交付物导向拆解",
        "3. 软件系统 WBS 分解标准四级范式（系统 -> 子系统 -> 模块 -> 工作包）",
        "4. 从 WBS 到活动网络图：单代号网络图 (PDM) 与紧前活动依赖",
        "5. 关键路径法 (CPM) 算法：最早/最迟开始时间与总浮动时间度量",
        "6. 数字化甘特图构建：工期缓冲池、里程碑控制点与实战案例"
    ]
    for idx, top in enumerate(topics):
        p_item = tf2.paragraphs[0] if idx == 0 else tf2.add_paragraph()
        p_item.text = top
        p_item.font.size = Pt(18)
        p_item.font.color.rgb = RGBColor(51, 65, 85)
        p_item.space_after = Pt(14)

    # Slide 3: WBS 100% Principle
    slide3 = prs.slides.add_slide(blank_layout)
    tb = slide3.shapes.add_textbox(Inches(1.0), Inches(0.8), Inches(11.333), Inches(1.0))
    p = tb.text_frame.paragraphs[0]
    p.text = "WBS 拆解的第一准则：100% 原则 (The 100% Rule)"
    p.font.size = Pt(28)
    p.font.bold = True
    p.font.color.rgb = RGBColor(30, 41, 59)

    tb3 = slide3.shapes.add_textbox(Inches(1.5), Inches(2.0), Inches(10.333), Inches(4.8))
    tf3 = tb3.text_frame
    rules = [
        "★ 完整性（Completeness）：下层所有交付物的总和必须 100% 等于上一层父节点，不遗漏任何必需工作。",
        "★ 互斥性（Mutually Exclusive）：同级工作包之间边界清晰、互不重叠，杜绝责任交叉与重复劳动。",
        "★ 可交付性（Deliverable-oriented）：以明确、可验证的成果（如文档、接口、组件、测试套件）作为节点名称。",
        "★ 颗粒度控制（8/80 规则）：单个工作包工时一般介于 8 小时（1天）至 80 小时（2周）之间。",
        "★ 责任人明确：每个最底层工作包（Work Package）必须指派唯一负责人（DRI - Directly Responsible Individual）。"
    ]
    for idx, r in enumerate(rules):
        p_r = tf3.paragraphs[0] if idx == 0 else tf3.add_paragraph()
        p_r.text = r
        p_r.font.size = Pt(17)
        p_r.font.color.rgb = RGBColor(71, 85, 105)
        p_r.space_after = Pt(16)

    # Slide 4: CPM & Gantt chart
    slide4 = prs.slides.add_slide(blank_layout)
    tb = slide4.shapes.add_textbox(Inches(1.0), Inches(0.8), Inches(11.333), Inches(1.0))
    p = tb.text_frame.paragraphs[0]
    p.text = "关键路径法 (CPM) 与甘特图排期演化"
    p.font.size = Pt(28)
    p.font.bold = True
    p.font.color.rgb = RGBColor(30, 41, 59)

    tb4 = slide4.shapes.add_textbox(Inches(1.5), Inches(2.0), Inches(10.333), Inches(4.8))
    tf4 = tb4.text_frame
    cpm_items = [
        "1. 关键路径定义：项目中耗时最长的网络路径，决定了整个项目的最短完工工期。",
        "2. 总浮动时间 (Total Float, TF)：TF = LS - ES = LF - EF。关键路径上各活动的浮动时间均为 0。",
        "3. 关键链缓冲池 (CCPM)：在关键路径末端设置项目缓冲（Project Buffer），在汇入路径设置汇入缓冲（Feeding Buffer）。",
        "4. 甘特图可视化呈现：横轴为日历时间序列，纵轴为 WBS 活动任务，条状长度直观表达工期与前置依赖。",
        "5. 随堂实战练习：给定 8 项任务及其逻辑依赖关系，计算关键路径并标定关键活动！"
    ]
    for idx, item in enumerate(cpm_items):
        p_i = tf4.paragraphs[0] if idx == 0 else tf4.add_paragraph()
        p_i.text = item
        p_i.font.size = Pt(17)
        p_i.font.color.rgb = RGBColor(71, 85, 105)
        p_i.space_after = Pt(16)

    pptx3_path = target_dir / "ch3_wbs.pptx"
    prs.save(pptx3_path)
    print(f"Generated pptx: {pptx3_path} ({os.path.getsize(pptx3_path)} bytes)")
except Exception as e:
    print(f"Failed to generate ch3_wbs.pptx: {e}", file=sys.stderr)

# 4. Generate ch5_scrum.pptx using python-pptx
try:
    prs5 = Presentation()
    prs5.slide_width = Inches(13.333)
    prs5.slide_height = Inches(7.5)

    blank_layout = prs5.slide_layouts[6]

    # Slide 1: Cover
    slide1 = prs5.slides.add_slide(blank_layout)
    txBox = slide1.shapes.add_textbox(Inches(1.0), Inches(1.8), Inches(11.333), Inches(3.5))
    tf = txBox.text_frame
    p1 = tf.paragraphs[0]
    p1.text = "第5讲：敏捷估算Poker与Scrum燃尽图"
    p1.font.size = Pt(40)
    p1.font.bold = True
    p1.font.color.rgb = RGBColor(30, 41, 59)
    p1.alignment = PP_ALIGN.CENTER

    p2 = tf.add_paragraph()
    p2.text = "Agile Planning Poker, Story Points & Sprint Burndown Charts"
    p2.font.size = Pt(20)
    p2.font.color.rgb = RGBColor(16, 185, 129)
    p2.alignment = PP_ALIGN.CENTER

    p3 = tf.add_paragraph()
    p3.text = "\n东北大学软件学院 · 软件项目管理 (CS3001) · 主讲教师：郭军 教授"
    p3.font.size = Pt(16)
    p3.font.color.rgb = RGBColor(100, 116, 139)
    p3.alignment = PP_ALIGN.CENTER

    # Slide 2: Planning Poker
    slide2 = prs5.slides.add_slide(blank_layout)
    tb = slide2.shapes.add_textbox(Inches(1.0), Inches(0.8), Inches(11.333), Inches(1.0))
    p = tb.text_frame.paragraphs[0]
    p.text = "敏捷估算扑克 (Planning Poker) 实战原理"
    p.font.size = Pt(28)
    p.font.bold = True
    p.font.color.rgb = RGBColor(30, 41, 59)

    tb2 = slide2.shapes.add_textbox(Inches(1.5), Inches(2.0), Inches(10.333), Inches(4.8))
    tf2 = tb2.text_frame
    poker_items = [
        "1. 故事点（Story Point）：衡量用户故事工作量、复杂度与不确定性的相对单位（Relative Sizing）。",
        "2. 斐波那契数列卡片：0, 1/2, 1, 2, 3, 5, 8, 13, 20, 40, 100, ?（数字越大不确定性呈指数上升）。",
        "3. 德尔菲法隐名出牌：Product Owner 宣讲故事 -> 团队提问澄清 -> 全员同时出牌，避免权威锚定效应（Anchoring Bias）。",
        "4. 分歧收敛讨论：出最高牌与最低牌的成员分别陈述理由，聚焦隐蔽风险与技术方案，开展第二轮出牌直至收敛。",
        "5. 优势：兼具群体智慧、速度敏捷、全员共识，有效提升研发团队估算一致性。"
    ]
    for idx, item in enumerate(poker_items):
        p_i = tf2.paragraphs[0] if idx == 0 else tf2.add_paragraph()
        p_i.text = item
        p_i.font.size = Pt(17)
        p_i.font.color.rgb = RGBColor(71, 85, 105)
        p_i.space_after = Pt(16)

    # Slide 3: Burndown Chart
    slide3 = prs5.slides.add_slide(blank_layout)
    tb = slide3.shapes.add_textbox(Inches(1.0), Inches(0.8), Inches(11.333), Inches(1.0))
    p = tb.text_frame.paragraphs[0]
    p.text = "Sprint 燃尽图 (Burndown Chart) 与团队速率 (Velocity)"
    p.font.size = Pt(28)
    p.font.bold = True
    p.font.color.rgb = RGBColor(30, 41, 59)

    tb3 = slide3.shapes.add_textbox(Inches(1.5), Inches(2.0), Inches(10.333), Inches(4.8))
    tf3 = tb3.text_frame
    burn_items = [
        "1. 横轴：Sprint 冲刺工作日（如 Day 1 至 Day 10）；纵轴：剩余总故事点数（Remaining Story Points）。",
        "2. 理想燃尽参考线：从第一天承诺点数均匀递减至最后一天为 0 点的直线（Ideal Line）。",
        "3. 实际燃尽曲线形态诊断：",
        "   - 凹型（Early Success）：提前完成工作，说明团队承诺偏保守或前期投入高效。",
        "   - 凸型（Late Finish）：前期平缓后期断崖，说明任务被阻断或联调集中在最后。",
        "   - 上扬型（Scope Creep）：冲刺中途追加需求，必须立刻触发 PO 范围谈判！",
        "4. 团队速率 (Velocity)：连续 3 个 Sprint 实际完成的故事点均值，是下一个冲刺承诺的客观依据。"
    ]
    for idx, item in enumerate(burn_items):
        p_i = tf3.paragraphs[0] if idx == 0 else tf3.add_paragraph()
        p_i.text = item
        p_i.font.size = Pt(17)
        p_i.font.color.rgb = RGBColor(71, 85, 105)
        p_i.space_after = Pt(16)

    pptx5_path = target_dir / "ch5_scrum.pptx"
    prs5.save(pptx5_path)
    print(f"Generated pptx: {pptx5_path} ({os.path.getsize(pptx5_path)} bytes)")
except Exception as e:
    print(f"Failed to generate ch5_scrum.pptx: {e}", file=sys.stderr)

# 5. Generate cs2002_intro.pdf and ai3001_intro.pptx
try:
    cs_pdf_path = target_dir / "cs2002_intro.pdf"
    if not cs_pdf_path.exists():
        import shutil
        if (target_dir / "ch1_overview.pdf").exists():
            shutil.copy(target_dir / "ch1_overview.pdf", cs_pdf_path)
            print("Copied cs2002_intro.pdf")

    ai_pptx_path = target_dir / "ai3001_intro.pptx"
    if not ai_pptx_path.exists():
        import shutil
        if (target_dir / "ch3_wbs.pptx").exists():
            shutil.copy(target_dir / "ch3_wbs.pptx", ai_pptx_path)
            print("Copied ai3001_intro.pptx")
except Exception as e:
    print(f"Failed to copy additional courseware: {e}")

print("All sample courseware generated successfully.")
