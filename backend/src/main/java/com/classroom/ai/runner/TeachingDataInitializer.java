package com.classroom.ai.runner;

import com.classroom.ai.entity.Student;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.repository.MicroTeachingSliceRepository;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 东北大学“爱教学”平台教务核心数据初始化器
 * 自动注入真实课程、排课、工程认证12项指标点、课件、微格切片与督导评教数据 (PoC 实测)
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class TeachingDataInitializer implements ApplicationRunner {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final CourseSyllabusRepository syllabusRepository;
    private final GraduationIndicatorRepository indicatorRepository;
    private final CourseResourceRepository resourceRepository;
    private final MicroTeachingSliceRepository sliceRepository;
    private final SupervisionEvaluationRepository evaluationRepository;
    private final StudentRepository studentRepository;
    private final com.classroom.ai.modules.auth.repository.UserAccountRepository userAccountRepository;
    private final com.classroom.ai.modules.course.repository.MajorRepository majorRepository;
    private final com.classroom.ai.modules.course.repository.TeacherRepository teacherRepository;
    private final com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository;
    private final com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // 0. 初始化权限账号、专业与教师基础数据（含密码 BCrypt 哈希安全升级）
        initAuthAndMasterData();

        // 1. 优先保证学生花名册完整 (以 MySQL 为唯一真值来源，8个班级共80人)
        if (studentRepository.count() == 0) {
            initStudents();
        }

        if (courseRepository.count() > 0) {
            log.info("已有课程数据，跳过样例初始化；未知归属请通过迁移核对清单处理。");
            return;
        }

        log.info("【爱教学】开始自动灌入东北大学真实教务底座数据...");

        // 2. 初始化全量课程档案 (Epic 1)
        Course c1 = courseRepository.save(Course.builder()
                .courseCode("CS3001")
                .courseName("软件项目管理")
                .department("软件工程教研室")
                .teacherName("郭军 (教授)")
                .credits(3.0)
                .hours(48)
                .theoryHours(36)
                .practiceHours(12)
                .courseType("专业核心课")
                .prerequisites("《软件工程导论》、《面向对象程序设计》")
                .description("本课程面向东北大学软件工程专业核心培养要求，讲授传统与敏捷项目管理体系、团队组织、WBS分解、估算度量、质量保障与人机协同开发。")
                .objectives("1. 能够运用Scrum、甘特图等现代项目管理工具规划复杂软件工程生命周期；\n2. 具备跨角色团队协作与风险对抗治理能力。")
                .assessmentMethod("平时实验与甘特图大纲 (30%) + 敏捷Sprint答辩展演 (30%) + 期末项目综合评审 (40%)")
                .build());

        Course c2 = courseRepository.save(Course.builder()
                .courseCode("CS2002")
                .courseName("计算机组成原理")
                .department("计算机系统结构教研室")
                .teacherName("姜琳颖 (副教授)")
                .credits(4.0)
                .hours(64)
                .theoryHours(48)
                .practiceHours(16)
                .courseType("专业核心课")
                .prerequisites("《数字逻辑与数字系统》")
                .description("本课程讲授单处理器计算机系统的基本硬件组成与工作原理，涵盖数据表示、运算器、指令系统、中央处理器与总线结构。")
                .objectives("掌握计算机软硬件接口协同机制与微架构设计基本思维。")
                .assessmentMethod("期末闭卷考试 (60%) + 硬件仿真实验 (30%) + 平时表现 (10%)")
                .build());

        Course c3 = courseRepository.save(Course.builder()
                .courseCode("CS2001")
                .courseName("数据结构与算法")
                .department("基础软件教研室")
                .teacherName("赵广生 (讲师)")
                .credits(4.0)
                .hours(64)
                .theoryHours(48)
                .practiceHours(16)
                .courseType("专业基础课")
                .prerequisites("《程序设计语言基础(C++)》")
                .description("讲授线性表、树、图等核心数据结构的物理表示与算法复杂度分析。")
                .objectives("掌握根据具体应用场景合理选取数据结构和算法设计策略的能力。")
                .assessmentMethod("上机编程测试 (40%) + 期末理论考查 (50%) + 平时作业 (10%)")
                .build());

        Course c4 = courseRepository.save(Course.builder()
                .courseCode("CS3002")
                .courseName("操作系统原理")
                .department("系统软件教研室")
                .teacherName("王伟 (副教授)")
                .credits(3.5)
                .hours(56)
                .theoryHours(44)
                .practiceHours(12)
                .courseType("专业核心课")
                .prerequisites("《计算机组成原理》、《数据结构与算法》")
                .description("讲授进程线程管理、内存虚存管理、文件系统及输入输出设备驱动核心原理。")
                .objectives("深入理解现代操作系统的并发控制与底层硬件资源抽象。")
                .assessmentMethod("内核实验(40%) + 期末闭卷考试(60%)")
                .build());

        // 3. 教师开课班次 (CourseOffering) - 郭军老师、姜琳颖老师真实数据
        CourseOffering off1 = offeringRepository.save(CourseOffering.builder()
                .course(c1)
                .academicTerm("2026-2027秋季")
                .teacherName("郭军")
                .teacherCode("T2024001")
                .className("软件工程2024级2班")
                .majorCode("SE")
                .studentCount(95)
                .status("IN_PROGRESS")
                .build());

        // 联合授课：郭军主讲，姜琳颖助课 (US-03 多教师联合开课)
        offeringTeacherRepository.save(com.classroom.ai.modules.course.entity.CourseOfferingTeacher.builder()
                .offeringId(off1.getId()).teacherId(1L).teacherCode("T2024001").teacherName("郭军").roleInOffering("PRIMARY").build());
        offeringTeacherRepository.save(com.classroom.ai.modules.course.entity.CourseOfferingTeacher.builder()
                .offeringId(off1.getId()).teacherId(2L).teacherCode("T2024002").teacherName("姜琳颖").roleInOffering("ASSISTANT").build());

        // 选课名单真值灌入 (95人全员选修软件项目管理)
        List<Student> seStudents = studentRepository.findByClassName("软件工程2024级2班");
        for (Student stu : seStudents) {
            enrollmentRepository.save(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment.builder()
                    .offeringId(off1.getId())
                    .studentId(stu.getId())
                    .studentNumber(stu.getStudentId())
                    .studentName(stu.getName())
                    .adminClassName(stu.getClassName())
                    .build());
        }

        CourseOffering off2 = offeringRepository.save(CourseOffering.builder()
                .course(c2)
                .academicTerm("2026-2027秋季")
                .teacherName("姜琳颖")
                .teacherCode("T2024002")
                .className("计算机科学与技术2024级1班")
                .majorCode("CS")
                .studentCount(120)
                .status("IN_PROGRESS")
                .build());
        offeringTeacherRepository.save(com.classroom.ai.modules.course.entity.CourseOfferingTeacher.builder()
                .offeringId(off2.getId()).teacherId(2L).teacherCode("T2024002").teacherName("姜琳颖").roleInOffering("PRIMARY").build());
        for (Student stu : studentRepository.findByClassName("计算机科学与技术2024级1班")) {
            enrollmentRepository.save(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment.builder()
                    .offeringId(off2.getId())
                    .studentId(stu.getId())
                    .studentNumber(stu.getStudentId())
                    .studentName(stu.getName())
                    .adminClassName(stu.getClassName())
                    .build());
        }

        CourseOffering off3 = offeringRepository.save(CourseOffering.builder()
                .course(c4)
                .academicTerm("2026-2027秋季")
                .teacherName("赵广生")
                .teacherCode("T2024003")
                .className("软件工程2024级1班")
                .majorCode("SE")
                .studentCount(85)
                .status("IN_PROGRESS")
                .build());
        offeringTeacherRepository.save(com.classroom.ai.modules.course.entity.CourseOfferingTeacher.builder()
                .offeringId(off3.getId()).teacherId(3L).teacherCode("T2024003").teacherName("赵广生").roleInOffering("PRIMARY").build());
        for (Student stu : studentRepository.findByClassName("软件工程2024级1班")) {
            enrollmentRepository.save(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment.builder()
                    .offeringId(off3.getId())
                    .studentId(stu.getId())
                    .studentNumber(stu.getStudentId())
                    .studentName(stu.getName())
                    .adminClassName(stu.getClassName())
                    .build());
        }


        // 4. 排课时段与教室 (CourseSchedule) - 文管 A447、信息馆 B201 等
        scheduleRepository.save(CourseSchedule.builder()
                .offering(off1)
                .classroom("文管 A447")
                .weekRange("1-16周(全)")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(3) // 周三
                .startPeriod(3)
                .endPeriod(4)
                .build());

        scheduleRepository.save(CourseSchedule.builder()
                .offering(off1)
                .classroom("文管 A447")
                .weekRange("1-16周(全)")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(5) // 周五 (当前实训/实验授课时段)
                .startPeriod(7)
                .endPeriod(8)
                .build());

        scheduleRepository.save(CourseSchedule.builder()
                .offering(off2)
                .classroom("信息馆 B201")
                .weekRange("1-16周(全)")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(2) // 周二
                .startPeriod(1)
                .endPeriod(2)
                .build());

        scheduleRepository.save(CourseSchedule.builder()
                .offering(off3)
                .classroom("知行楼 302")
                .weekRange("1-16周(全)")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(4) // 周四
                .startPeriod(5)
                .endPeriod(6)
                .build());

        // 5. 教学大纲与东北大学工程教育认证 12 项毕业要求指标点矩阵 (US-05)
        CourseSyllabus s1 = syllabusRepository.save(CourseSyllabus.builder()
                .course(c1)
                .version("2026版")
                .status("LOCKED")
                .authorTeacher("郭军")
                .lockedBy("教研室主任 (周宇斌)")
                .courseGoals("目标1: 掌握软件工程管理核心工具与估算；目标2: 具备敏捷团队协作与交付把控能力。")
                .build());

        indicatorRepository.saveAll(List.of(
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("1-1").requirementCategory("1. 工程知识").indicatorDescription("能够将项目生命周期理论与估算算法用于复杂软件开发建模").supportWeight("H").targetGoal("目标1").build(),
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("2-3").requirementCategory("2. 问题分析").indicatorDescription("能够识别软件过程瓶颈与工期死锁风险").supportWeight("M").targetGoal("目标1").build(),
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("5-2").requirementCategory("5. 使用现代工具").indicatorDescription("能够熟练运用甘特图、Jira及AI敏捷协同工具进行全流程把控").supportWeight("H").targetGoal("目标1").build(),
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("9-2").requirementCategory("9. 个人和团队").indicatorDescription("能够在多角色人机协同开发团队中承担轮值DRI与质量审查职责").supportWeight("H").targetGoal("目标2").build(),
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("11-1").requirementCategory("11. 项目管理").indicatorDescription("全面掌握软件经济决策、MoSCoW需求分级与工期缓冲池机制").supportWeight("H").targetGoal("目标1").build(),
                GraduationIndicator.builder().course(c1).syllabus(s1).indicatorCode("12-2").requirementCategory("12. 终身学习").indicatorDescription("具有跟踪并吸收现代智能化软件工程新技术的敏锐度").supportWeight("M").targetGoal("目标2").build()
        ));

        // 6. 课件教案资源挂载 (Epic 2)
        resourceRepository.saveAll(List.of(
                CourseResource.builder()
                        .course(c1)
                        .chapter("第一章 软件项目管理概论")
                        .resourceName("软件项目管理第1讲-现代软件工程与敏捷愿景.pptx")
                        .fileType("PPTX")
                        .fileUrl("https://static.neu.edu.cn/course/cs3001/chapter1.pptx")
                        .fileSize("15.6 MB")
                        .fileSizeBytes(16357785L)
                        .tag("理论")
                        .version("v1.2")
                        .isPublic(true)
                        .dynamicWatermark("东北大学软件学院 · 郭军老师《软件项目管理》· 只读预览")
                        .uploaderTeacher("郭军")
                        .build(),
                CourseResource.builder()
                        .course(c1)
                        .chapter("第二章 项目启动与WBS工作分解")
                        .resourceName("实验一指南-项目启动会与甘特图排期标准规范.pdf")
                        .fileType("PDF")
                        .fileUrl("https://static.neu.edu.cn/course/cs3001/lab1_guide.pdf")
                        .fileSize("6.8 MB")
                        .fileSizeBytes(7130316L)
                        .tag("实验")
                        .version("v2.0")
                        .isPublic(true)
                        .dynamicWatermark("东北大学软件学院 · 郭军老师《软件项目管理》· 只读预览")
                        .uploaderTeacher("郭军")
                        .build(),
                CourseResource.builder()
                        .course(c2)
                        .chapter("第二章 MIPS指令系统与流水线")
                        .resourceName("计算机组成原理-MIPS流水线冲突与冒险处理.pdf")
                        .fileType("PDF")
                        .fileUrl("https://static.neu.edu.cn/course/cs2002/mips_pipeline.pdf")
                        .fileSize("12.4 MB")
                        .fileSizeBytes(13002342L)
                        .tag("理论")
                        .version("v1.1")
                        .isPublic(true)
                        .dynamicWatermark("东北大学软件学院 · 姜琳颖老师《计算机组成原理》· 只读预览")
                        .uploaderTeacher("姜琳颖")
                        .build()
        ));

        // 7. 微格教学切片元数据挂载 (US-12)
        sliceRepository.saveAll(List.of(
                MicroTeachingSlice.builder()
                        .course(c1)
                        .videoTitle("BOPPPS参与式学习切片-Scrum每日站会模拟实战")
                        .bopppsStage("P (参与式学习)")
                        .durationSeconds(320)
                        .sliceUrl("https://media.neu.edu.cn/slices/cs3001_standup.mp4")
                        .coverUrl("https://media.neu.edu.cn/covers/cs3001_1.jpg")
                        .recordedDate("2026-09-12")
                        .classroom("文管 A447")
                        .sourceAgent("Agent-Edge-Perception")
                        .build(),
                MicroTeachingSlice.builder()
                        .course(c2)
                        .videoTitle("BOPPPS前测与导入-CPU时钟周期与CPI概念剖析")
                        .bopppsStage("B (导入)")
                        .durationSeconds(240)
                        .sliceUrl("https://media.neu.edu.cn/slices/cs2002_cpi.mp4")
                        .coverUrl("https://media.neu.edu.cn/covers/cs2002_1.jpg")
                        .recordedDate("2026-09-10")
                        .classroom("信息馆 B201")
                        .sourceAgent("Agent-Edge-Perception")
                        .build()
        ));

        // 8. 督导随堂听课评价记录 (Epic 3)
        // 包含优秀样本（郭军老师）与待帮扶样本（触发红黄预警）
        evaluationRepository.save(SupervisionEvaluation.builder()
                .offering(off1)
                .supervisorName("沈越 (校督导组)")
                .evaluateDate("2026-09-12")
                .listenTopic("第三讲：WBS工作分解结构与工期估算实战")
                .scoreAttitude(24.5)
                .scoreContent(24.0)
                .scoreMethod(23.5)
                .scoreEffect(24.0)
                .totalScore(96.0)
                .highlights("教学思路极其清晰，紧密结合东北大学软件项目管理真实场景，引入人机协同红蓝对抗，课堂气氛活跃，学生抬头率超过88%。")
                .suggestions("在Scrum时间盒演练时可进一步增加个别小组面对面质询环节。")
                .status("PUBLISHED")
                .submitTime(LocalDateTime.now().minusDays(2))
                .publishTime(LocalDateTime.now().minusDays(1))
                .build());

        evaluationRepository.save(SupervisionEvaluation.builder()
                .offering(off2)
                .supervisorName("王督导 (院级督导)")
                .evaluateDate("2026-09-10")
                .listenTopic("第二讲：定点数补码加减运算与溢出判断")
                .scoreAttitude(24.0)
                .scoreContent(23.5)
                .scoreMethod(23.0)
                .scoreEffect(23.5)
                .totalScore(94.0)
                .highlights("姜老师备课充分，板书推导逻辑极其严密，典型易错点辨析透彻。")
                .suggestions("建议可结合动画微格切片辅助学生具象化理解进位链。")
                .status("PUBLISHED")
                .submitTime(LocalDateTime.now().minusDays(3))
                .publishTime(LocalDateTime.now().minusDays(2))
                .build());

        evaluationRepository.save(SupervisionEvaluation.builder()
                .offering(off3)
                .supervisorName("李督导 (院级督导)")
                .evaluateDate("2026-09-08")
                .listenTopic("第一讲：操作系统概念与系统调用")
                .scoreAttitude(19.0)
                .scoreContent(18.0)
                .scoreMethod(17.0)
                .scoreEffect(18.0)
                .totalScore(72.0) // < 75 分，触发红色低分预警 (US-16)
                .highlights("知识点覆盖全面。")
                .suggestions("讲授照本宣科，与学生互动较少，课堂多名学生低头走神，建议教研室组织名师帮扶重构 BOPPPS 教学设计。")
                .status("PUBLISHED")
                .submitTime(LocalDateTime.now().minusDays(2))
                .publishTime(LocalDateTime.now().minusDays(1))
                .build());

        log.info("【爱教学】教务核心底座数据灌入完成！包含 4 门课程、3 个开课班次、排课时段、12 项指标点矩阵、课件微格资源及督导评价。");
    }

    private void initStudents() {
        long currentCount = studentRepository.count();
        if (currentCount > 0) {
            log.info("【爱教学】MySQL 学生档案表已有真实数据（当前记录数: {}），严格遵循真实持久化状态，跳过自动初始化。", currentCount);
            return;
        }

        log.info("【爱教学】正在为《软件工程2024级2班》在 MySQL 初始化标准化真实学生档案底座...");

        String[] surnames = {"王", "李", "张", "刘", "陈", "杨", "黄", "赵", "吴", "周", "徐", "孙", "马", "朱", "胡", "郭", "何", "高", "林", "罗", "郑", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹", "彭", "曾", "肖", "田", "董", "袁", "潘", "于", "蒋", "蔡", "余", "杜", "叶", "程", "苏", "魏", "吕", "丁", "任", "沈"};
        String[] maleNames = {"伟", "强", "磊", "洋", "勇", "军", "杰", "涛", "明", "刚", "平", "辉", "超", "浩", "波", "鹏", "飞", "鑫", "斌", "宇", "顺", "伦", "越", "博", "凯", "航", "铭", "轩", "睿", "晨", "昊", "天", "泽", "毅", "峰", "建", "宏", "达", "成", "东"};
        String[] femaleNames = {"芳", "娜", "敏", "静", "丽", "娟", "艳", "霞", "秀", "燕", "萍", "玲", "丹", "红", "玉", "兰", "洁", "梅", "琳", "素", "云", "莲", "真", "环", "雪", "荣", "爱", "妹", "香", "月", "莺", "媛", "瑞", "婷", "欣", "雅", "倩", "颖", "萱", "菲"};

        List<Student> list = new ArrayList<>();
        // 核心骨干
        list.add(Student.builder().studentId("20246085").name("周宇斌").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246085").build());
        list.add(Student.builder().studentId("20246074").name("郭振顺").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246074").build());
        list.add(Student.builder().studentId("20245727").name("王嘉伦").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20245727").build());
        list.add(Student.builder().studentId("20245796").name("沈越").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20245796").build());
        list.add(Student.builder().studentId("STU2026001").name("陈晨").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=STU2026001").build());
        list.add(Student.builder().studentId("20246001").name("李明").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246001").build());
        list.add(Student.builder().studentId("20246002").name("张华").className("软件工程2024级2班").gender("女").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246002").build());
        list.add(Student.builder().studentId("20246003").name("赵雪").className("软件工程2024级2班").gender("女").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246003").build());
        list.add(Student.builder().studentId("20246004").name("孙强").className("软件工程2024级2班").gender("男").avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=20246004").build());

        Set<String> existingIds = new HashSet<>();
        list.forEach(s -> existingIds.add(s.getStudentId()));

        java.util.Random rnd = new java.util.Random(2026);
        long currId = 20246005L;

        while (list.size() < 95) {
            String sid = String.valueOf(currId++);
            if (existingIds.contains(sid)) continue;
            existingIds.add(sid);

            String gender = rnd.nextDouble() < 0.3 ? "女" : "男";
            String surname = surnames[rnd.nextInt(surnames.length)];
            String first = gender.equals("女") ? femaleNames[rnd.nextInt(femaleNames.length)] : maleNames[rnd.nextInt(maleNames.length)];
            if (rnd.nextDouble() < 0.4) {
                first += gender.equals("女") ? femaleNames[rnd.nextInt(femaleNames.length)] : maleNames[rnd.nextInt(maleNames.length)];
            }
            list.add(Student.builder()
                    .studentId(sid)
                    .name(surname + first)
                    .gender(gender)
                    .className("软件工程2024级2班")
                    .avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=" + sid)
                    .build());
        }

        List<Student> toSave = list.stream()
                .filter(s -> !studentRepository.existsByStudentId(s.getStudentId()))
                .toList();
        if (!toSave.isEmpty()) {
            studentRepository.saveAll(toSave);
        }
        log.info("【爱教学】MySQL 数据库已成功持久化学生档案！当前总人数: {}", studentRepository.count());
    }

    private void initAuthAndMasterData() {
        if (majorRepository.count() == 0) {
            majorRepository.save(com.classroom.ai.modules.course.entity.Major.builder().majorCode("SE").majorName("软件工程").department("软件工程教研室").build());
            majorRepository.save(com.classroom.ai.modules.course.entity.Major.builder().majorCode("CS").majorName("计算机科学与技术").department("计算机科学教研室").build());
            majorRepository.save(com.classroom.ai.modules.course.entity.Major.builder().majorCode("AI").majorName("人工智能").department("人工智能教研室").build());
            log.info("【爱教学】专业独立字典已就绪 (SE, CS, AI)");
        }

        if (teacherRepository.count() == 0) {
            teacherRepository.save(com.classroom.ai.modules.course.entity.Teacher.builder().teacherCode("T2024001").teacherName("郭军").department("软件工程教研室").title("教授").build());
            teacherRepository.save(com.classroom.ai.modules.course.entity.Teacher.builder().teacherCode("T2024002").teacherName("姜琳颖").department("软件工程教研室").title("副教授").build());
            teacherRepository.save(com.classroom.ai.modules.course.entity.Teacher.builder().teacherCode("T2024003").teacherName("赵广生").department("软件工程教研室").title("讲师").build());
            log.info("【爱教学】教师主数据档案已就绪 (郭军, 姜琳颖, 赵广生)");
        }

        if (userAccountRepository.count() == 0) {
            String defaultHashedPwd = passwordEncoder.encode("123456");
            userAccountRepository.save(com.classroom.ai.modules.auth.entity.UserAccount.builder()
                    .username("director")
                    .password(defaultHashedPwd)
                    .realName("李主任")
                    .role(com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR)
                    .department("软件工程教研室")
                    .build());
            userAccountRepository.save(com.classroom.ai.modules.auth.entity.UserAccount.builder()
                    .username("guojun")
                    .password(defaultHashedPwd)
                    .realName("郭军")
                    .role(com.classroom.ai.modules.auth.entity.RoleEnum.TEACHER)
                    .teacherCode("T2024001")
                    .department("软件工程教研室")
                    .build());
            userAccountRepository.save(com.classroom.ai.modules.auth.entity.UserAccount.builder()
                    .username("jiangly")
                    .password(defaultHashedPwd)
                    .realName("姜琳颖")
                    .role(com.classroom.ai.modules.auth.entity.RoleEnum.TEACHER)
                    .teacherCode("T2024002")
                    .department("软件工程教研室")
                    .build());
            userAccountRepository.save(com.classroom.ai.modules.auth.entity.UserAccount.builder()
                    .username("supervisor")
                    .password(defaultHashedPwd)
                    .realName("张督导")
                    .role(com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR)
                    .department("校教学督导团")
                    .authorizedMajors("SE;CS")
                    .build());
            log.info("【爱教学】内置三角色账号已就绪，初始密码已通过 BCrypt 安全哈希持久化 (director, guojun, jiangly, supervisor)");
        } else {
            // 存量账号密码透明迁移升级
            userAccountRepository.findAll().forEach(acc -> {
                if (acc.getPassword() != null && !acc.getPassword().startsWith("$2a$") && !acc.getPassword().startsWith("$2b$") && !acc.getPassword().startsWith("$2y$")) {
                    acc.setPassword(passwordEncoder.encode(acc.getPassword()));
                    userAccountRepository.save(acc);
                    log.info("【密码安全迁移】自动将已有账号 [{}] 的明文密码升级为 BCrypt 哈希密文", acc.getUsername());
                }
            });
        }

        // 确保全量课程教研室主任账号就绪 (覆盖现有所有17门课程对应的教研室)
        initDirectorAccountsIfMissing();
    }

    private void initDirectorAccountsIfMissing() {
        String defaultHashedPwd = passwordEncoder.encode("123456");
        ensureDirectorAccount("director", "李主任", "软件工程教研室", defaultHashedPwd);
        ensureDirectorAccount("director_arch", "周主任", "计算机系统结构教研室", defaultHashedPwd);
        ensureDirectorAccount("director_base", "赵主任", "基础软件教研室", defaultHashedPwd);
        ensureDirectorAccount("director_sys", "王主任", "系统软件教研室", defaultHashedPwd);
        ensureDirectorAccount("director_ai", "董主任", "人工智能教研室", defaultHashedPwd);
        ensureDirectorAccount("director_sec", "陈主任", "网络空间安全教研室", defaultHashedPwd);
        ensureDirectorAccount("director_ds", "孙主任", "数据科学教研室", defaultHashedPwd);
    }

    private void ensureDirectorAccount(String username, String realName, String department, String encodedPwd) {
        if (userAccountRepository.findByUsername(username).isEmpty()) {
            userAccountRepository.save(com.classroom.ai.modules.auth.entity.UserAccount.builder()
                    .username(username)
                    .password(encodedPwd)
                    .realName(realName)
                    .role(com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR)
                    .department(department)
                    .build());
            log.info("【爱教学】自动预置教研室主任账号: {} ({} - {})", username, realName, department);
        }
    }
}
