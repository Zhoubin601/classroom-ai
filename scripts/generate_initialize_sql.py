import json
import random
import math

random.seed(2026)

def generate_vector(seed_val):
    rnd = random.Random(seed_val)
    vec = [rnd.gauss(0, 1) for _ in range(512)]
    norm = math.sqrt(sum(x * x for x in vec))
    return [round(x / norm, 6) for x in vec]

sql_lines = []
sql_lines.append("-- ==========================================================")
sql_lines.append("-- 爱教学平台全量基准教务与人脸特征数据库初始化脚本")
sql_lines.append("-- 脚本文件: initialize.sql")
sql_lines.append("-- 字符集: utf8mb4 / 排序规则: utf8mb4_unicode_ci")
sql_lines.append("-- 规格: 8位教师 (每人多门课程多时段排课)、8个班级 (每班严格10人共80人)、80条512维人脸底库")
sql_lines.append("-- ==========================================================\n")
sql_lines.append("SET NAMES utf8mb4;")
sql_lines.append("SET FOREIGN_KEY_CHECKS = 0;\n")

# 1. Majors
sql_lines.append("-- 1. 专业独立字典表 (t_major)")
sql_lines.append("DELETE FROM `t_major`;")
sql_lines.append("INSERT INTO `t_major` (`id`, `major_code`, `major_name`, `department`) VALUES")
sql_lines.append("(1, 'SE', '软件工程', '软件工程教研室'),")
sql_lines.append("(2, 'CS', '计算机科学与技术', '计算机系统结构教研室'),")
sql_lines.append("(3, 'AI', '人工智能', '人工智能教研室'),")
sql_lines.append("(4, 'DS', '数据科学与大数据技术', '数据科学教研室'),")
sql_lines.append("(5, 'SEC', '信息安全', '网络空间安全教研室');\n")

# 2. Teachers
teachers = [
    (1, 'T2024001', '郭军', '软件工程教研室', '教授'),
    (2, 'T2024002', '姜琳颖', '计算机系统结构教研室', '副教授'),
    (3, 'T2024003', '赵广生', '基础软件教研室', '讲师'),
    (4, 'T2024004', '王伟', '系统软件教研室', '副教授'),
    (5, 'T2024005', '董晓梅', '人工智能教研室', '副教授'),
    (6, 'T2024006', '刘博', '软件工程教研室', '副教授'),
    (7, 'T2024007', '陈立新', '网络空间安全教研室', '教授'),
    (8, 'T2024008', '孙志刚', '数据科学教研室', '讲师')
]
sql_lines.append("-- 2. 教师主数据档案表 (t_teacher)")
sql_lines.append("DELETE FROM `t_teacher`;")
sql_lines.append("INSERT INTO `t_teacher` (`id`, `teacher_code`, `teacher_name`, `department`, `title`) VALUES")
t_vals = [f"({tid}, '{code}', '{name}', '{dept}', '{title}')" for tid, code, name, dept, title in teachers]
sql_lines.append(",\n".join(t_vals) + ";\n")

# 3. User Accounts
users = [
    (1, 'director', '123456', '李主任', 'DIRECTOR', '软件工程教研室', None, None),
    (2, 'supervisor', '123456', '张督导', 'SUPERVISOR', '校教学督导团', None, 'SE;CS;AI;DS;SEC'),
    (3, 'guojun', '123456', '郭军', 'TEACHER', '软件工程教研室', 'T2024001', None),
    (4, 'jiangly', '123456', '姜琳颖', 'TEACHER', '计算机系统结构教研室', 'T2024002', None),
    (5, 'zhaogs', '123456', '赵广生', 'TEACHER', '基础软件教研室', 'T2024003', None),
    (6, 'wangwei', '123456', '王伟', 'TEACHER', '系统软件教研室', 'T2024004', None),
    (7, 'dongxm', '123456', '董晓梅', 'TEACHER', '人工智能教研室', 'T2024005', None),
    (8, 'liubo', '123456', '刘博', 'TEACHER', '软件工程教研室', 'T2024006', None),
    (9, 'chenlx', '123456', '陈立新', 'TEACHER', '网络空间安全教研室', 'T2024007', None),
    (10, 'sunzg', '123456', '孙志刚', 'TEACHER', '数据科学教研室', 'T2024008', None)
]
sql_lines.append("-- 3. 用户账号表 (t_user_account)")
sql_lines.append("DELETE FROM `t_user_account`;")
sql_lines.append("INSERT INTO `t_user_account` (`id`, `username`, `password`, `real_name`, `role`, `department`, `teacher_code`, `authorized_majors`) VALUES")
u_vals = []
for uid, un, pw, rn, rl, dp, tc, am in users:
    tc_s = f"'{tc}'" if tc else "NULL"
    am_s = f"'{am}'" if am else "NULL"
    u_vals.append(f"({uid}, '{un}', '{pw}', '{rn}', '{rl}', '{dp}', {tc_s}, {am_s})")
sql_lines.append(",\n".join(u_vals) + ";\n")

# 4. Courses (17 courses, each teacher multiple courses)
courses = [
    (1, 'CS3001', '软件项目管理', '软件工程教研室', '郭军 (教授)', 3.0, 48, 36, 12, '专业核心课', '《软件工程导论》', '面向软件工程核心培养要求，讲授敏捷与传统项目管理体系、团队组织、WBS分解与人机协同。'),
    (2, 'CS1001', '软件工程导论', '软件工程教研室', '郭军 (教授)', 2.5, 40, 32, 8, '专业基础课', '《程序设计基础》', '介绍软件生命周期、软件工程方法学与工程规范。'),
    (3, 'CS4002', '敏捷软件工程实训', '软件工程教研室', '郭军 (教授)', 2.0, 32, 8, 24, '专业选修课', '《软件工程导论》、《面向对象程序设计》', '实战Scrum敏捷迭代演练与现代DevOps工具链实操。'),
    (4, 'CS2002', '计算机组成原理', '计算机系统结构教研室', '姜琳颖 (副教授)', 4.0, 64, 48, 16, '专业核心课', '《数字逻辑与数字系统》', '讲授单处理器计算机硬件组成与微架构指令系统。'),
    (5, 'CS2003', '数字逻辑与系统设计', '计算机系统结构教研室', '姜琳颖 (副教授)', 3.0, 48, 36, 12, '专业基础课', '《大学计算机基础》', '讲授逻辑代数基础、组合逻辑电路与时序逻辑电路设计。'),
    (6, 'CS2001', '数据结构与算法', '基础软件教研室', '赵广生 (讲师)', 4.0, 64, 48, 16, '专业基础课', '《C++程序设计》', '系统讲授线性表、树、图等核心数据结构与经典算法。'),
    (7, 'CS1002', 'C++高级程序设计', '基础软件教研室', '赵广生 (讲师)', 3.5, 56, 40, 16, '专业基础课', '无', '面向对象编程思维、类模板与现代C++ STL标准库实战。'),
    (8, 'CS3002', '操作系统原理', '系统软件教研室', '王伟 (副教授)', 3.5, 56, 44, 12, '专业核心课', '《计算机组成原理》', '进程线程管理、虚存管理与文件系统底层调度核心原理。'),
    (9, 'CS3008', '嵌入式Linux系统', '系统软件教研室', '王伟 (副教授)', 3.0, 48, 32, 16, '专业核心课', '《操作系统原理》、《计算机系统结构》', 'ARM架构下的Linux内核裁剪、交叉编译与设备驱动开发。'),
    (10, 'AI3001', '人工智能导论', '人工智能教研室', '董晓梅 (副教授)', 3.0, 48, 36, 12, '专业核心课', '《高等数学》、《离散数学》', '启发式搜索、知识图谱、专家系统及现代深度学习基础。'),
    (11, 'AI3002', '机器学习与模式识别', '人工智能教研室', '董晓梅 (副教授)', 3.5, 56, 40, 16, '专业核心课', '《线性代数》、《概率论与数理统计》', '分类聚类算法、支持向量机、集成学习与模型评估。'),
    (12, 'SE3002', '敏捷开发与人机协同', '软件工程教研室', '刘博 (副教授)', 2.5, 40, 24, 16, '专业选修课', '《软件项目管理》', '生成式AI辅助编程、代码审查协同与敏捷迭代。'),
    (13, 'SE3003', 'DevOps与持续交付', '软件工程教研室', '刘博 (副教授)', 3.0, 48, 32, 16, '专业核心课', '《操作系统原理》、《软件工程导论》', '容器化编排、CI/CD流水线构建与可观测性工程实践。'),
    (14, 'CS3003', '计算机网络与安全', '网络空间安全教研室', '陈立新 (教授)', 3.5, 56, 42, 14, '专业核心课', '《计算机组成原理》', 'OSI七层模型、TCP/IP协议族、密码学与网络攻击防御。'),
    (15, 'SEC3001', '信息安全攻防实践', '网络空间安全教研室', '陈立新 (教授)', 3.0, 48, 20, 28, '专业核心课', '《计算机网络与安全》', '漏洞扫描分析、逆向工程、Web渗透测试与攻防演练。'),
    (16, 'DS2001', '数据库系统实现', '数据科学教研室', '孙志刚 (讲师)', 3.0, 48, 36, 12, '专业核心课', '《数据结构与算法》', '关系代数、B+树索引机制、事务ACID特性与查询优化器。'),
    (17, 'DS3001', '分布式大数据计算', '数据科学教研室', '孙志刚 (讲师)', 3.5, 56, 38, 18, '专业核心课', '《数据库系统实现》、《操作系统原理》', 'Hadoop与Spark计算引擎、流批一体处理与大规模数据分析。')
]
sql_lines.append("-- 4. 课程档案表 (t_course)")
sql_lines.append("DELETE FROM `t_course`;")
sql_lines.append("INSERT INTO `t_course` (`id`, `course_code`, `course_name`, `department`, `teacher_name`, `credits`, `hours`, `theory_hours`, `practice_hours`, `course_type`, `prerequisites`, `description`) VALUES")
c_vals = []
for cid, cc, cn, dept, tn, cr, hr, th, ph, ct, pr, desc in courses:
    c_vals.append(f"({cid}, '{cc}', '{cn}', '{dept}', '{tn}', {cr}, {hr}, {th}, {ph}, '{ct}', '{pr}', '{desc}')")
sql_lines.append(",\n".join(c_vals) + ";\n")

# 5. Classes & Students (8 classes * 10 students = 80 students)
classes_info = [
    ('软件工程2024级2班', 'SE', 20246001, [
        ('周宇斌', '男'), ('郭振顺', '男'), ('王嘉伦', '男'), ('沈越', '男'), ('陈晨', '男'),
        ('李明', '男'), ('张华', '女'), ('赵雪', '女'), ('孙强', '男'), ('周伟', '男')
    ]),
    ('软件工程2024级1班', 'SE', 20246101, [
        ('郑浩', '男'), ('梁敏', '女'), ('谢飞', '男'), ('宋博', '男'), ('唐琴', '女'),
        ('许航', '男'), ('韩梅', '女'), ('冯超', '男'), ('邓伟', '男'), ('曹军', '男')
    ]),
    ('计算机科学与技术2024级1班', 'CS', 20247001, [
        ('林杰', '男'), ('黄晓', '女'), ('徐博', '男'), ('杨洁', '女'), ('刘洋', '男'),
        ('吴静', '女'), ('朱峰', '男'), ('胡艳', '女'), ('高磊', '男'), ('何芳', '女')
    ]),
    ('计算机科学与技术2024级2班', 'CS', 20247101, [
        ('罗斌', '男'), ('彭丽', '女'), ('曾辉', '男'), ('肖文', '男'), ('田野', '男'),
        ('董婷', '女'), ('袁帅', '男'), ('潘虹', '女'), ('于波', '男'), ('蒋欣', '女')
    ]),
    ('人工智能2024级1班', 'AI', 20248001, [
        ('蔡阳', '男'), ('余琳', '女'), ('杜宇', '男'), ('叶晨', '男'), ('程龙', '男'),
        ('苏菲', '女'), ('魏东', '男'), ('吕薇', '女'), ('丁浩', '男'), ('任强', '男')
    ]),
    ('人工智能2024级2班', 'AI', 20248101, [
        ('沈亮', '男'), ('崔颖', '女'), ('陆峰', '男'), ('钱进', '男'), ('陶然', '男'),
        ('范冰', '女'), ('贾凯', '男'), ('顾萍', '女'), ('孟祥', '男'), ('薛飞', '男')
    ]),
    ('数据科学与大数据2024级1班', 'DS', 20249001, [
        ('郝云', '男'), ('白露', '女'), ('阎博', '男'), ('邱实', '男'), ('骆成', '男'),
        ('黎明', '男'), ('邵华', '女'), ('尹航', '男'), ('邹平', '男'), ('熊壮', '男')
    ]),
    ('信息安全2024级1班', 'SEC', 20249101, [
        ('金鑫', '男'), ('陆瑶', '女'), ('谭建', '男'), ('廖伟', '男'), ('贺敏', '女'),
        ('龚俊', '男'), ('庞博', '男'), ('聂丹', '女'), ('樊刚', '男'), ('盛夏', '女')
    ])
]

all_students = []
student_id_pk = 1
for cname, mcode, start_sid, members in classes_info:
    for idx, (name, gender) in enumerate(members):
        sid = str(start_sid + idx)
        avatar = f"https://api.dicebear.com/7.x/bottts/svg?seed={sid}"
        all_students.append((student_id_pk, sid, name, gender, cname, avatar))
        student_id_pk += 1

sql_lines.append("-- 5. 学生档案表 (student) - 8个班级，每班严格恰好10人 (共80人)")
sql_lines.append("DELETE FROM `student`;")
sql_lines.append("INSERT INTO `student` (`id`, `student_id`, `name`, `gender`, `class_name`, `avatar_url`) VALUES")
s_vals = [f"({pk}, '{sid}', '{name}', '{gender}', '{cname}', '{avatar}')" for pk, sid, name, gender, cname, avatar in all_students]
sql_lines.append(",\n".join(s_vals) + ";\n")

# 6. Face Features (80 rows with 512-dim Float JSON vector)
sql_lines.append("-- 6. 人脸高维特征向量表 (face_feature) - 80条规范512维浮点向量")
sql_lines.append("DELETE FROM `face_feature`;")
sql_lines.append("INSERT INTO `face_feature` (`id`, `student_id`, `feature_dim`, `feature_vector`, `image_path`) VALUES")
f_vals = []
for pk, sid, name, gender, cname, avatar in all_students:
    vec = generate_vector(int(sid) if sid.isdigit() else 2026)
    vec_json = json.dumps(vec)
    img_path = f"/uploads/faces/{sid}_snapshot.jpg"
    f_vals.append(f"({pk}, '{sid}', 512, '{vec_json}', '{img_path}')")
sql_lines.append(",\n".join(f_vals) + ";\n")

# 7. Course Offerings & Schedules (17 offerings, each 10 students, multi-class parallel periods)
# (off_id, course_id, teacher_name, teacher_code, class_name, term, student_count, classroom, dayOfWeek, startPeriod, endPeriod, weekRange)
offerings_data = [
    # 郭军 (3 courses)
    (1, 1, '郭军', 'T2024001', '软件工程2024级2班', '2026-2027秋季', 10, '文管 A447', 3, 3, 4, '1-16周(全)'), # ★周三 3-4节 并行
    (2, 2, '郭军', 'T2024001', '计算机科学与技术2024级2班', '2026-2027秋季', 10, '文管 B210', 1, 5, 6, '1-16周(全)'),
    (3, 3, '郭军', 'T2024001', '软件工程2024级1班', '2026-2027秋季', 10, '创新工场 101', 5, 3, 4, '1-16周(全)'),
    # 姜琳颖 (2 courses)
    (4, 4, '姜琳颖', 'T2024002', '计算机科学与技术2024级1班', '2026-2027秋季', 10, '信息馆 B201', 2, 1, 2, '1-16周(全)'), # ★周二 1-2节 并行
    (5, 5, '姜琳颖', 'T2024002', '人工智能2024级1班', '2026-2027秋季', 10, '信息馆 A305', 4, 1, 2, '1-16周(全)'),
    # 赵广生 (2 courses)
    (6, 6, '赵广生', 'T2024003', '计算机科学与技术2024级2班', '2026-2027秋季', 10, '知行楼 201', 1, 3, 4, '1-16周(全)'),
    (7, 7, '赵广生', 'T2024003', '软件工程2024级2班', '2026-2027秋季', 10, '机房 302', 4, 3, 4, '1-16周(全)'),
    # 王伟 (2 courses)
    (8, 8, '王伟', 'T2024004', '人工智能2024级2班', '2026-2027秋季', 10, '信息馆 405', 4, 5, 6, '1-16周(全)'),
    (9, 9, '王伟', 'T2024004', '信息安全2024级1班', '2026-2027秋季', 10, '研创楼 203', 2, 7, 8, '1-16周(全)'),
    # 董晓梅 (2 courses)
    (10, 10, '董晓梅', 'T2024005', '人工智能2024级1班', '2026-2027秋季', 10, '信息馆 B102', 3, 3, 4, '1-16周(全)'), # ★周三 3-4节 并行 (与班次1、12同阶段)
    (11, 11, '董晓梅', 'T2024005', '数据科学与大数据2024级1班', '2026-2027秋季', 10, '信息馆 C402', 5, 5, 6, '1-16周(全)'),
    # 刘博 (2 courses)
    (12, 12, '刘博', 'T2024006', '软件工程2024级1班', '2026-2027秋季', 10, '知行楼 302', 3, 3, 4, '1-16周(全)'), # ★周三 3-4节 并行 (与班次1、10同阶段)
    (13, 13, '刘博', 'T2024006', '软件工程2024级2班', '2026-2027秋季', 10, '创客空间 201', 2, 3, 4, '1-16周(全)'),
    # 陈立新 (2 courses)
    (14, 14, '陈立新', 'T2024007', '信息安全2024级1班', '2026-2027秋季', 10, '信息馆 A208', 2, 1, 2, '1-16周(全)'), # ★周二 1-2节 并行 (与班次4同阶段)
    (15, 15, '陈立新', 'T2024007', '计算机科学与技术2024级1班', '2026-2027秋季', 10, '网安靶场 102', 4, 7, 8, '1-16周(全)'),
    # 孙志刚 (2 courses)
    (16, 16, '孙志刚', 'T2024008', '数据科学与大数据2024级1班', '2026-2027秋季', 10, '数理馆 103', 5, 1, 2, '1-16周(全)'),
    (17, 17, '孙志刚', 'T2024008', '人工智能2024级2班', '2026-2027秋季', 10, '智算中心 204', 3, 7, 8, '1-16周(全)')
]

sql_lines.append("-- 7. 开课班次表 (t_course_offering)")
sql_lines.append("DELETE FROM `t_course_offering`;")
sql_lines.append("INSERT INTO `t_course_offering` (`id`, `course_id`, `academic_term`, `teacher_name`, `teacher_code`, `class_name`, `student_count`, `status`) VALUES")
off_vals = []
for oid, cid, tn, tc, cn, term, sc, cr, dow, sp, ep, wr in offerings_data:
    off_vals.append(f"({oid}, {cid}, '{term}', '{tn}', '{tc}', '{cn}', {sc}, 'IN_PROGRESS')")
sql_lines.append(",\n".join(off_vals) + ";\n")

sql_lines.append("-- 8. 开课排课时段与教室表 (t_course_schedule) - 含多班级同一时段并行授课场景")
sql_lines.append("DELETE FROM `t_course_schedule`;")
sql_lines.append("INSERT INTO `t_course_schedule` (`id`, `offering_id`, `classroom`, `week_range`, `start_week`, `end_week`, `day_of_week`, `start_period`, `end_period`) VALUES")
sch_vals = []
for oid, cid, tn, tc, cn, term, sc, cr, dow, sp, ep, wr in offerings_data:
    sch_vals.append(f"({oid}, {oid}, '{cr}', '{wr}', 1, 16, {dow}, {sp}, {ep})")
sql_lines.append(",\n".join(sch_vals) + ";\n")

# 9. Multi-Teacher Offering (t_course_offering_teacher)
sql_lines.append("-- 9. 班次多教师授课关联表 (t_course_offering_teacher)")
sql_lines.append("DELETE FROM `t_course_offering_teacher`;")
sql_lines.append("INSERT INTO `t_course_offering_teacher` (`offering_id`, `teacher_id`, `teacher_code`, `teacher_name`, `role_in_offering`) VALUES")
sql_lines.append("(1, 1, 'T2024001', '郭军', 'PRIMARY'),")
sql_lines.append("(1, 6, 'T2024006', '刘博', 'ASSISTANT'),")
sql_lines.append("(4, 2, 'T2024002', '姜琳颖', 'PRIMARY'),")
sql_lines.append("(10, 5, 'T2024005', '董晓梅', 'PRIMARY');\n")

# 10. Student Enrollments (t_offering_student_enrollment)
sql_lines.append("-- 10. 班次学生选课真值表 (t_offering_student_enrollment)")
sql_lines.append("DELETE FROM `t_offering_student_enrollment`;")
sql_lines.append("INSERT INTO `t_offering_student_enrollment` (`offering_id`, `student_id`, `student_number`, `student_name`, `admin_class_name`) VALUES")

enrollment_vals = []
for oid, cid, tn, tc, cn, term, sc, cr, dow, sp, ep, wr in offerings_data:
    c_stus = [s for s in all_students if s[4] == cn]
    for pk, sid, name, gender, _, _ in c_stus:
        enrollment_vals.append(f"({oid}, {pk}, '{sid}', '{name}', '{cn}')")

sql_lines.append(",\n".join(enrollment_vals) + ";\n")

# 11. Course Syllabus (t_course_syllabus)
sql_lines.append("-- 11. 课程教学大纲表 (t_course_syllabus)")
sql_lines.append("DELETE FROM `t_course_syllabus`;")
sql_lines.append("INSERT INTO `t_course_syllabus` (`id`, `course_id`, `version`, `status`, `author_teacher`, `course_goals`, `locked_by`) VALUES")
sql_lines.append("(1, 1, 'v2026.1', 'LOCKED', '郭军 (教授)', '目标1: 掌握敏捷Scrum与甘特图项目生命周期规划; 目标2: 具备人机协同与质量管控能力', '李主任'),")
sql_lines.append("(2, 4, 'v2026.1', 'PUBLISHED', '姜琳颖 (副教授)', '掌握单处理器计算机硬件微架构设计与软硬件协同思维', '李主任'),")
sql_lines.append("(3, 6, 'v2026.1', 'PUBLISHED', '赵广生 (讲师)', '掌握高阶算法复杂度分析与核心数据结构选型', '李主任'),")
sql_lines.append("(4, 8, 'v2026.1', 'PUBLISHED', '王伟 (副教授)', '掌握现代操作系统并发控制与内核资源抽象', '李主任');\n")

# 12. Graduation Indicators (t_graduation_indicator)
indicators = [
    (1, 1, '1-1', '1. 工程知识', '能够将项目生命周期理论与估算算法用于复杂软件开发建模', 'H', '目标1'),
    (2, 1, '2-3', '2. 问题分析', '能够识别软件过程瓶颈与工期死锁风险', 'M', '目标1'),
    (3, 1, '3-1', '3. 设计/开发解决方案', '能够设计覆盖敏捷迭代与持续交付的完整工程流程', 'H', '目标2'),
    (4, 1, '4-2', '4. 研究', '能够基于实验度量数据科学评价软件缺陷收敛趋势', 'M', '目标2'),
    (5, 1, '5-2', '5. 使用现代工具', '能够熟练运用甘特图、Jira及AI敏捷协同工具进行全流程把控', 'H', '目标1'),
    (6, 1, '6-1', '6. 工程与社会', '合理分析软件发布对行业与社会的合规性影响', 'M', '目标1'),
    (7, 1, '7-1', '7. 环境和可持续发展', '评价软件工程生命周期资源利用与系统可持续性', 'L', '目标2'),
    (8, 1, '8-2', '8. 职业规范', '具有软件工程师职业道德与软件知识产权保护规范', 'H', '目标1'),
    (9, 1, '9-2', '9. 个人和团队', '能够在多角色人机协同开发团队中承担轮值DRI与质量审查职责', 'H', '目标2'),
    (10, 1, '10-1', '10. 沟通', '能够撰写符合IEEE标准的工程规范与Sprint评审报告', 'M', '目标1'),
    (11, 1, '11-1', '11. 项目管理', '全面掌握软件经济决策、MoSCoW需求分级与工期缓冲池机制', 'H', '目标1'),
    (12, 1, '12-2', '12. 终身学习', '具有跟踪并吸收现代智能化软件工程新技术的敏锐度', 'M', '目标2')
]
sql_lines.append("-- 12. 工程教育认证 12 项毕业要求指标点矩阵表 (t_graduation_indicator)")
sql_lines.append("DELETE FROM `t_graduation_indicator`;")
sql_lines.append("INSERT INTO `t_graduation_indicator` (`id`, `course_id`, `syllabus_id`, `indicator_code`, `requirement_category`, `indicator_description`, `support_weight`, `target_goal`) VALUES")
ind_vals = [f"({iid}, {cid}, 1, '{code}', '{cat}', '{desc}', '{weight}', '{goal}')" for iid, cid, code, cat, desc, weight, goal in indicators]
sql_lines.append(",\n".join(ind_vals) + ";\n")

# 13. Resources (t_course_resource)
sql_lines.append("-- 13. 课程教学资源与课件表 (t_course_resource)")
sql_lines.append("DELETE FROM `t_course_resource`;")
sql_lines.append("INSERT INTO `t_course_resource` (`id`, `course_id`, `resource_name`, `chapter`, `tag`, `file_type`, `file_size`, `file_url`, `uploader_teacher`, `version`, `is_public`) VALUES")
sql_lines.append("(1, 1, '第1章：软件项目管理全景大纲.pdf', '第1章 项目管理导论', '教案大纲', 'PDF', '2.8 MB', '/uploads/resources/ch1_overview.pdf', '郭军', 'v1.0', 1),")
sql_lines.append("(2, 1, '第3讲：WBS分解与甘特图实战.pptx', '第3章 进度计划与WBS', '讲义课件', 'PPTX', '14.5 MB', '/uploads/resources/ch3_wbs.pptx', '郭军', 'v1.0', 1),")
sql_lines.append("(3, 1, '第5讲：敏捷估算Poker与Scrum燃尽图.pptx', '第5章 敏捷估算与度量', '讲义课件', 'PPTX', '18.2 MB', '/uploads/resources/ch5_scrum.pptx', '郭军', 'v1.0', 1),")
sql_lines.append("(4, 4, '计算机组成原理：微架构与指令集导引.pdf', '第1章 系统概论', '教案大纲', 'PDF', '5.1 MB', '/uploads/resources/cs2002_intro.pdf', '姜琳颖', 'v1.0', 1),")
sql_lines.append("(5, 10, '人工智能前沿与知识表示体系.pptx', '第1章 AI引论', '讲义课件', 'PPTX', '22.0 MB', '/uploads/resources/ai3001_intro.pptx', '董晓梅', 'v1.0', 1);\n")

# 14. Supervision Evaluations (t_supervision_evaluation)
sql_lines.append("-- 14. 督导随堂听评课量化评价表 (t_supervision_evaluation)")
sql_lines.append("DELETE FROM `t_supervision_evaluation`;")
sql_lines.append("INSERT INTO `t_supervision_evaluation` (`id`, `offering_id`, `supervisor_name`, `evaluate_date`, `listen_topic`, `score_attitude`, `score_content`, `score_method`, `score_effect`, `total_score`, `highlights`, `suggestions`, `status`, `submit_time`, `publish_time`) VALUES")
sql_lines.append("(1, 1, '张督导 (校级督导)', '2026-09-10', '第3讲：敏捷估算与WBS分解', 24.5, 24.0, 24.0, 24.5, 97.0, '教学组织严谨，将真实软件研发缺陷案例与WBS甘特图紧密结合，学生抬头率超过90%，人机互动热烈。', '建议课后作业进一步深化工期缓冲池度量。', 'PUBLISHED', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),")
sql_lines.append("(2, 4, '张督导 (校级督导)', '2026-09-12', '第2讲：数据表示与运算器微架构', 24.0, 23.5, 23.0, 23.5, 94.0, '姜老师备课充分，板书推导逻辑极其严密，典型易错点辨析透彻。', '建议结合动画微格切片辅助学生具象化理解进位链。', 'PUBLISHED', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),")
sql_lines.append("(3, 8, '李督导 (院级督导)', '2026-09-08', '第1讲：操作系统概念与系统调用', 19.0, 18.0, 17.0, 18.0, 72.0, '知识点覆盖全面。', '讲授照本宣科，与学生互动较少，课堂多名学生低头走神，建议教研室组织名师帮扶重构 BOPPPS 教学设计。', 'PUBLISHED', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY);\n")

sql_lines.append("SET FOREIGN_KEY_CHECKS = 1;")
sql_lines.append("-- ==========================================================")
sql_lines.append("-- 初始化脚本执行完毕")
sql_lines.append("-- ==========================================================\n")

content = "\n".join(sql_lines)
with open("d:/2026Autumn Semester File/classroom-ai-demo/initialize.sql", "w", encoding="utf-8") as f:
    f.write(content)

with open("d:/2026Autumn Semester File/classroom-ai-demo/scripts/deploy/mysql/init/initialize.sql", "w", encoding="utf-8") as f:
    f.write(content)

print(f"Successfully generated initialize.sql ({len(content)} bytes)")
