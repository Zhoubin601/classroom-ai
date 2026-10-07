package com.classroom.ai.modules;

import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.impl.SyllabusServiceImpl;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.*;
import com.classroom.ai.modules.resource.service.ResourceAccessService;
import com.classroom.ai.modules.resource.service.ResourceFileService;
import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.*;
import com.classroom.ai.modules.supervision.service.impl.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=update", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CourseAuthorizationService.class, SyllabusServiceImpl.class, ResourceAccessService.class,
        ResourceFileService.class, SupervisionServiceImpl.class, SupervisionAnalyticsServiceImpl.class})
@EnabledIfEnvironmentVariable(named = "EXP3_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13317/exp3_test.*")
class Sprint2MysqlTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("EXP3_MYSQL_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:13317/exp3_test?"))
            throw new IllegalStateException("Sprint 2 tests require their disposable database");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired CourseRepository courses;
    @Autowired MajorRepository majors;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseSyllabusRepository syllabi;
    @Autowired GraduationIndicatorRepository mappings;
    @Autowired TrainingIndicatorRepository catalog;
    @Autowired SyllabusServiceImpl syllabusService;
    @Autowired CourseResourceRepository resources;
    @Autowired ResourceAccessService resourceAccess;
    @Autowired ResourceFileService files;
    @Autowired ResourceAccessLogRepository accessLogs;
    @Autowired SupervisionServiceImpl evaluations;
    @Autowired SupervisionEvaluationRepository evaluationRepo;
    @Autowired EvaluationAuditLogRepository auditLogs;
    @Autowired SupervisionAnalyticsServiceImpl analytics;

    @AfterEach void clear() { AuthContext.clear(); }

    private Course course(String code) {
        var major = majors.findByMajorCode("SE").orElseGet(() -> majors.save(Major.builder().majorCode("SE")
                .majorName("测试软件工程").department("软件工程教研室").build()));
        return courses.save(Course.builder().courseCode(code).courseName(code).department("软件工程教研室")
                .majorCode("SE").majorId(major.getId()).credits(3.0).hours(48).build());
    }

    private CourseOffering offering(Course course, String term, String status) {
        return offerings.save(CourseOffering.builder().course(course).academicTerm(term).status(status)
                .teacherName("郭军").teacherCode("T1").className("一班").studentCount(30).build());
    }

    private UserVO user(RoleEnum role, long id) {
        return UserVO.builder().id(id).username(role.name().toLowerCase()).realName(role == RoleEnum.TEACHER ? "郭军" : role.name())
                .role(role).department("软件工程教研室").teacherCode(role == RoleEnum.TEACHER ? "T1" : null)
                .authorizedMajors("SE").build();
    }

    @Test void syllabusVersionsKeepTheirOwnMappings() {
        Course c = course("EXP3-SYLLABUS"); offering(c, "2026秋", "IN_PROGRESS");
        for (String code : List.of("1-1", "2-1")) {
            TrainingIndicator item = new TrainingIndicator();
            item.setMajorCode("SE"); item.setPlanVersion("plan-2026"); item.setIndicatorCode(code);
            item.setRequirementCategory("工程知识"); item.setIndicatorDescription(code);
            catalog.save(item);
        }
        SyllabusDTO.IndicatorDTO one = SyllabusDTO.IndicatorDTO.builder().indicatorCode("1-1")
                .requirementCategory("工程知识").indicatorDescription("初版").supportWeight("H").build();
        SyllabusDTO.IndicatorDTO two = SyllabusDTO.IndicatorDTO.builder().indicatorCode("2-1")
                .requirementCategory("工程知识").indicatorDescription("新版").supportWeight("M").build();
        var v1 = syllabusService.saveSyllabus(SyllabusDTO.builder().courseId(c.getId()).version("v1")
                .planVersion("plan-2026").indicators(List.of(one)).build());
        var v2 = syllabusService.saveSyllabus(SyllabusDTO.builder().courseId(c.getId()).version("v2")
                .planVersion("plan-2026").indicators(List.of(two)).build());
        assertEquals(1, mappings.findBySyllabusId(v1.getId()).size());
        assertEquals("1-1", mappings.findBySyllabusId(v1.getId()).get(0).getIndicatorCode());
        assertEquals("2-1", mappings.findBySyllabusId(v2.getId()).get(0).getIndicatorCode());
        assertThrows(IllegalArgumentException.class, () -> syllabusService.saveSyllabus(SyllabusDTO.builder()
                .courseId(c.getId()).version("v3").planVersion("plan-2026")
                .indicators(List.of(SyllabusDTO.IndicatorDTO.builder().indicatorCode("99-1").build())).build()));
    }

    @Test void resourceVisibilityTagsAndExpiringPreview() throws Exception {
        Course a = course("EXP3-A"); Course b = course("EXP3-B");
        Course foreign = courses.save(Course.builder().courseCode("EXP3-FOREIGN").courseName("外院课")
                .department("其他教研室").majorCode("CS").credits(3.0).hours(48).build());
        offering(a, "2026秋", "IN_PROGRESS"); offering(b, "2026秋", "IN_PROGRESS").setTeacherCode("T2");
        offering(foreign, "2026秋", "IN_PROGRESS").setTeacherCode("T2");
        CourseResource privateB = resources.save(resource(b, false, List.of()));
        CourseResource sharedB = resources.save(resource(b, true, List.of("理论", "实验")));
        CourseResource foreignShared = resources.save(resource(foreign, true, List.of("理论")));
        AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 11));
        assertFalse(resourceAccess.canRead(privateB));
        assertTrue(resourceAccess.canRead(sharedB));
        assertFalse(resourceAccess.canRead(foreignShared));
        assertEquals(List.of("理论", "实验"), resources.findById(sharedB.getId()).orElseThrow().getTags());
        assertTrue(privateB.getTags().isEmpty());
        assertThrows(com.classroom.ai.common.exception.ForbiddenException.class,
                () -> files.download(sharedB.getId(), user(RoleEnum.TEACHER, 11)));

        String filename = "exp3-" + UUID.randomUUID() + ".pdf";
        Path root = com.classroom.ai.config.UploadPaths.resolveResources("");
        Files.createDirectories(root);
        Path path = root.resolve(filename);
        try (PDDocument pdf = new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(path.toFile()); }
        try {
            sharedB.setFileUrl("/uploads/resources/" + filename);
            resources.saveAndFlush(sharedB);
            String token = files.createTicket(sharedB.getId(), user(RoleEnum.TEACHER, 11));
            byte[] preview = files.preview(token);
            assertTrue(preview.length > 100);
            try (PDDocument rendered = PDDocument.load(preview)) {
                assertTrue(rendered.isEncrypted());
                assertFalse(rendered.getCurrentAccessPermission().canExtractContent());
                assertFalse(rendered.getCurrentAccessPermission().canPrint());
                assertEquals("东北大学软件学院", rendered.getDocumentInformation().getCustomMetadataValue("PreviewOrganization"));
                assertEquals(12, rendered.getDocumentInformation().getCustomMetadataValue("PreviewReference").length());
                assertTrue(rendered.getPage(0).getResources().getExtGStateNames().iterator().hasNext());
                assertTrue(rendered.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            }
            assertEquals(1, accessLogs.count());
            String boundTicket = files.createTicket(sharedB.getId(), user(RoleEnum.TEACHER, 11));
            AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 12));
            assertThrows(com.classroom.ai.common.exception.ForbiddenException.class, () -> files.preview(boundTicket));
            AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 11));
            ReflectionTestUtils.setField(files, "previewMinutes", -1L);
            String expired = files.createTicket(sharedB.getId(), user(RoleEnum.TEACHER, 11));
            assertThrows(IllegalArgumentException.class, () -> files.preview(expired));
            ReflectionTestUtils.setField(files, "previewMinutes", 5L);
            String revokedTicket = files.createTicket(sharedB.getId(), user(RoleEnum.TEACHER, 11));
            sharedB.setIsPublic(false);
            resources.saveAndFlush(sharedB);
            assertThrows(com.classroom.ai.common.exception.ForbiddenException.class, () -> files.preview(revokedTicket));
            assertEquals(1, accessLogs.count());
        } finally { Files.deleteIfExists(path); }
    }

    @Test void recommendedTemplateKeepsRealPlansSeparateAndSupportsMaintenance() {
        Course c = course("EXP3-TEMPLATE");
        offering(c, "2026秋", "IN_PROGRESS");
        AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 11));
        long planItemsBefore = catalog.count();
        var template = com.classroom.ai.modules.course.service.RecommendedIndicatorTemplate.items("SE");
        var items = template.stream().map(item -> SyllabusDTO.IndicatorDTO.builder()
                .indicatorCode(item.getIndicatorCode()).requirementCategory(item.getRequirementCategory())
                .indicatorDescription(item.getIndicatorDescription()).supportWeight("M").build()).toList();
        var created = syllabusService.saveSyllabus(SyllabusDTO.builder().courseId(c.getId()).version("template-v1")
                .planVersion("RECOMMENDED-12").status("DRAFT").indicators(items).build());
        assertEquals(12, mappings.findBySyllabusId(created.getId()).size());
        assertEquals(planItemsBefore, catalog.count());
        var first = mappings.findBySyllabusId(created.getId()).get(0);
        var edit = com.classroom.ai.modules.course.dto.IndicatorDTO.builder()
                .supportWeight("H").targetGoal("目标1").build();
        assertEquals("H", syllabusService.updateIndicator(first.getId(), edit).getSupportWeight());
        assertThrows(IllegalArgumentException.class, () -> syllabusService.updateIndicator(first.getId(),
                com.classroom.ai.modules.course.dto.IndicatorDTO.builder().supportWeight("X").build()));
        syllabusService.deleteIndicator(first.getId());
        assertEquals(11, mappings.findBySyllabusId(created.getId()).size());
        syllabusService.addIndicator(c.getId(), com.classroom.ai.modules.course.dto.IndicatorDTO.builder()
                .indicatorCode("11-2").requirementCategory("项目管理")
                .indicatorDescription("已按实际课程修订").supportWeight("L").targetGoal("目标2").build());
        assertEquals(12, mappings.findBySyllabusId(created.getId()).size());
        assertTrue(mappings.findBySyllabusId(created.getId()).stream().anyMatch(item -> "11-2".equals(item.getIndicatorCode())));
        assertThrows(IllegalArgumentException.class, () -> syllabusService.addIndicator(c.getId(),
                com.classroom.ai.modules.course.dto.IndicatorDTO.builder().indicatorCode("13-1").build()));
        syllabusService.lockSyllabus(created.getId(), "主任");
        assertThrows(IllegalStateException.class, () -> syllabusService.addIndicator(c.getId(),
                com.classroom.ai.modules.course.dto.IndicatorDTO.builder().indicatorCode("1-1").build()));
        assertEquals(planItemsBefore, catalog.count());
    }

    private CourseResource resource(Course course, boolean shared, List<String> tags) {
        CourseResource r = CourseResource.builder().course(course).chapter("第一章").resourceName("课件")
                .fileType("PDF").fileUrl("/uploads/resources/missing.pdf").tag(tags.isEmpty() ? "未标注" : tags.get(0))
                .version("v1").isPublic(shared).build();
        r.setTags(tags);
        return r;
    }

    @Test void reviewDelayAndSemesterCoverage() {
        Course a = course("EXP3-COVER-A"); Course b = course("EXP3-COVER-B");
        CourseOffering oa = offering(a, "2026秋", "IN_PROGRESS");
        offering(a, "2026秋", "FINISHED"); offering(b, "2026秋", "IN_PROGRESS");
        offering(b, "2025秋", "FINISHED"); CourseOffering pending = offering(b, "2026秋", "PENDING");
        AuthContext.setCurrentUser(user(RoleEnum.SUPERVISOR, 22));
        EvaluationSubmitDTO input = EvaluationSubmitDTO.builder().offeringId(oa.getId()).listenTopic("章节讲解")
                .scoreAttitude(20.0).scoreContent(20.0).scoreMethod(20.0).scoreEffect(20.0)
                .highlights("讲解清晰\n组织有序\n互动充分").suggestions("增加练习").build();
        SupervisionEvaluation submitted = evaluations.submitEvaluation(input);
        assertEquals("PENDING_REVIEW", submitted.getStatus());
        assertNull(submitted.getPublishTime());
        AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 11));
        assertTrue(evaluations.getEvaluationsByOffering(oa.getId()).isEmpty());
        AuthContext.setCurrentUser(user(RoleEnum.DIRECTOR, 33));
        SupervisionEvaluation reviewed = evaluations.reviewEvaluation(submitted.getId(), true, "通过");
        assertEquals("APPROVED_PENDING", reviewed.getStatus());
        assertTrue(reviewed.getPublishTime().isAfter(LocalDateTime.now().plusHours(23)));
        evaluationRepo.save(SupervisionEvaluation.builder().offering(pending).supervisorName("旧记录")
                .evaluateDate("2026-09-23").listenTopic("停开班次")
                .scoreAttitude(20.0).scoreContent(20.0).scoreMethod(20.0).scoreEffect(20.0)
                .totalScore(80.0).status("PUBLISHED").build());
        assertEquals(2, auditLogs.count());
        assertEquals(2, analytics.getDashboardMetrics("2026秋").getTotalCourses());
        assertEquals(1, analytics.getDashboardMetrics("2026秋").getSupervisedCourses());
        assertEquals(50.0, analytics.getDashboardMetrics("2026秋").getCoverageRate());
        assertEquals(2, analytics.getCoverageDetails("2026秋").size());
        AuthContext.setCurrentUser(user(RoleEnum.TEACHER, 11));
        assertTrue(evaluations.getEvaluationsByOffering(oa.getId()).isEmpty());
        reviewed.setPublishTime(LocalDateTime.now().minusSeconds(1));
        evaluationRepo.saveAndFlush(reviewed);
        var visible = evaluations.getEvaluationsByOffering(oa.getId());
        assertEquals(1, visible.size());
        assertEquals("匿名督导", visible.get(0).getSupervisorName());
        assertNull(visible.get(0).getSupervisorUserId());
    }
}
