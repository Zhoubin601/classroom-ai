package com.classroom.ai.modules.course;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.CourseContentController;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 仅允许显式指定的隔离测试库。真实 InnoDB 事务/锁，不启动业务初始化器。 */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CourseContentController.class, CourseAuthorizationService.class, CourseImportServiceImpl.class, CourseServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "US0102_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13316/us0102_test.*")
class CourseWorkflowMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("US0102_MYSQL_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:13316/us0102_test?"))
            throw new IllegalStateException("必须使用专用隔离测试库");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired CourseContentController content;
    @Autowired CourseImportServiceImpl imports;
    @Autowired CourseServiceImpl archives;
    @Autowired CourseRepository courses;
    @Autowired CourseContentRevisionRepository revisions;
    @Autowired CourseOfferingRepository offerings;
    @Autowired MajorRepository majors;
    @Autowired CourseOfferingTeacherRepository relations;
    @SpyBean CourseImportLogRepository logs;
    @Autowired javax.sql.DataSource dataSource;
    Course course;
    Major major;
    static final String HEADER = "courseCode,courseName,department,majorCode,credits,hours,theoryHours,practiceHours,courseType,prerequisites,description\n";

    @BeforeEach void prepare() {
        reset(logs);
        CourseImportServiceImpl.clearBatchCache();
        revisions.deleteAll(); relations.deleteAll(); offerings.deleteAll(); courses.deleteAll(); majors.deleteAll(); logs.deleteAll();
        major = majors.saveAndFlush(Major.builder().majorCode("SE").majorName("Synthetic Major").department("Review Dept").build());
        course = courses.saveAndFlush(Course.builder().courseCode("BASE").courseName("Synthetic Course")
            .department("Review Dept").majorCode("SE").majorId(major.getId()).credits(3.0).hours(48).courseType("core").build());
        var assigned = offerings.saveAndFlush(CourseOffering.builder().course(course).academicTerm("REVIEW")
            .teacherName("Teacher A").teacherCode("TEST-T1").className("Synthetic Class").studentCount(0).build());
        relations.saveAndFlush(CourseOfferingTeacher.builder().offeringId(assigned.getId()).teacherId(1L).teacherCode("TEST-T1").teacherName("Teacher A").roleInOffering("PRIMARY").build());
        teacher();
    }
    @AfterEach void clear() { AuthContext.clear(); CourseImportServiceImpl.clearBatchCache(); }
    static void teacher() { AuthContext.setCurrentUser(UserVO.builder().username("review_teacher").realName("Teacher A")
        .role(RoleEnum.TEACHER).teacherCode("TEST-T1").build()); }
    static void director() { AuthContext.setCurrentUser(UserVO.builder().username("review_director").realName("Director")
        .role(RoleEnum.DIRECTOR).department("Review Dept").build()); }
    ContentRevisionDTO dto(CourseContentRevision draft, String text) {
        return ContentRevisionDTO.builder().draftId(draft.getId()).lockVersion(draft.getLockVersion())
            .publishVersion(draft.getPublishVersion() == null ? 0 : draft.getPublishVersion())
            .description(text).assessmentMethod("Exam").objectives("Learn").build();
    }
    String preview(String rows) throws Exception {
        return imports.previewImport(new MockMultipartFile("file", "synthetic.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8))).getBatchId();
    }

    @Test void completeDraftPublishAndReeditWorkflowRejectsStaleRequests() {
        var draft = content.getDraft(course.getId()).getData();
        assertEquals(draft.getId(), content.getDraft(course.getId()).getData().getId());
        var partial = dto(draft, "Partial"); partial.setObjectives("");
        var saved = content.saveDraft(course.getId(), partial).getData();
        assertTrue(saved.getLockVersion() > draft.getLockVersion());
        assertThrows(IllegalArgumentException.class, () -> content.publishContent(course.getId(), partial));
        assertNull(content.getPublishedContent(course.getId()).getData());
        var request = dto(saved, "Published v1");
        var published = content.publishContent(course.getId(), request).getData();
        assertEquals(1, published.getPublishVersion());
        assertEquals("TEST-T1", published.getPublisherCode());
        assertNotNull(published.getPublishedAt());
        assertThrows(IllegalStateException.class, () -> content.publishContent(course.getId(), request));
        var next = content.getDraft(course.getId()).getData();
        assertNotEquals(draft.getId(), next.getId());
        assertThrows(IllegalStateException.class, () -> content.saveDraft(course.getId(), dto(draft, "Stale")));
        var edited = content.saveDraft(course.getId(), dto(next, "Editing v2")).getData();
        assertEquals("Published v1", content.getPublishedContent(course.getId()).getData().getDescription());
        var v2 = content.publishContent(course.getId(), dto(edited, "Published v2")).getData();
        assertEquals(2, v2.getPublishVersion());
        assertEquals("Published v2", courses.findById(course.getId()).orElseThrow().getDescription());
        assertEquals(2, revisions.findByCourseIdOrderByPublishVersionDesc(course.getId()).stream().filter(r -> "PUBLISHED".equals(r.getStatus())).count());
    }

    /** 同时从两个线程发起，确保竞争进入独立 Spring 事务，而非仅在一个会话模拟版本号。 */
    <T> List<T> race(Supplier<T> action) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            Callable<T> worker = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { return action.get(); } finally { AuthContext.clear(); }
            };
            Future<T> first = pool.submit(worker), second = pool.submit(worker);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    @Test void concurrentFirstReadsCreateOneDraft() throws Exception {
        var ids = race(() -> { teacher(); return content.getDraft(course.getId()).getData().getId(); });
        assertEquals(ids.get(0), ids.get(1)); assertEquals(1, revisions.count());
    }
    @Test void concurrentPublishHasOneWinnerAndOneConflict() throws Exception {
        var draft = content.getDraft(course.getId()).getData();
        var results = race(() -> {
            teacher();
            try { content.publishContent(course.getId(), dto(draft, "Concurrent")); return "published"; }
            catch (IllegalStateException expected) { return "conflict"; }
        });
        assertEquals(1, Collections.frequency(results, "published"));
        assertEquals(1, Collections.frequency(results, "conflict"));
        assertEquals(1, revisions.count());
    }
    @Test void concurrentDraftSavesDoNotLoseUpdates() throws Exception {
        var draft = content.getDraft(course.getId()).getData();
        var results = race(() -> {
            teacher();
            try { content.saveDraft(course.getId(), dto(draft, UUID.randomUUID().toString())); return "saved"; }
            catch (IllegalStateException expected) { return "conflict"; }
        });
        assertEquals(1, Collections.frequency(results, "saved"));
        assertEquals(1, Collections.frequency(results, "conflict"));
    }
    @Test void validBatchImportsAllRowsAndRecordsOperator() throws Exception {
        director();
        String batch = preview("NEW1,New1,Review Dept,SE,3,48,,12,core,,First\nNEW2,New2,Review Dept,SE,3,48,36,12,core,NEW1,Second\n");
        assertEquals(1, courses.count());
        imports.confirmImport(new ImportConfirmDTO(batch));
        assertEquals(3, courses.count()); assertEquals(1, logs.count());
        var imported = courses.findByCourseCode("NEW1").orElseThrow();
        assertEquals(36, imported.getTheoryHours()); assertEquals("review_director", imported.getCreatedBy());
        assertThrows(IllegalArgumentException.class, () -> imports.confirmImport(new ImportConfirmDTO(batch)));
    }
    @Test void auditFailureRollsBackWholeBatchAndAllowsRetry() throws Exception {
        director();
        String batch = preview("NEW1,New1,Review Dept,SE,3,48,36,12,core,,First\nNEW2,New2,Review Dept,SE,3,48,36,12,core,,Second\n");
        doThrow(new DataIntegrityViolationException("injected audit failure")).when(logs).save(any());
        assertThrows(DataIntegrityViolationException.class, () -> imports.confirmImport(new ImportConfirmDTO(batch)));
        assertEquals(1, courses.count()); assertEquals(0, logs.count());
        assertNotNull(CourseImportServiceImpl.getBatchCache(batch));
        reset(logs);
        imports.confirmImport(new ImportConfirmDTO(batch));
        assertEquals(3, courses.count()); assertEquals(1, logs.count());
    }
    @Test void concurrentBatchConfirmationWritesExactlyOnce() throws Exception {
        director(); String batch = preview("NEW1,New1,Review Dept,SE,3,48,36,12,core,,First\n");
        var results = race(() -> {
            director();
            try { imports.confirmImport(new ImportConfirmDTO(batch)); return "imported"; }
            catch (IllegalStateException | IllegalArgumentException expected) { return "rejected"; }
        });
        assertEquals(1, Collections.frequency(results, "imported"));
        assertEquals(2, courses.count()); assertEquals(1, logs.count());
    }
    @Test void deletedPrerequisiteRejectsConfirmation() throws Exception {
        director();
        var reference = courses.saveAndFlush(Course.builder().courseCode("REF").courseName("Reference").credits(1.0).hours(16).build());
        String batch = preview("NEW1,New1,Review Dept,SE,3,48,36,12,core,REF,First\n");
        courses.deleteById(reference.getId());
        assertThrows(IllegalArgumentException.class, () -> imports.confirmImport(new ImportConfirmDTO(batch)));
        assertEquals(1, courses.count()); assertEquals(0, logs.count());
    }
    @Test void deletedMajorRejectsConfirmation() throws Exception {
        director(); String batch = preview("NEW1,New1,Review Dept,SE,3,48,36,12,core,,First\n");
        majors.deleteById(major.getId());
        assertThrows(IllegalArgumentException.class, () -> imports.confirmImport(new ImportConfirmDTO(batch)));
        assertEquals(1, courses.count());
    }
    @Test void archiveEditPreservesPublishedContentAndRejectsCodeOrScopeChange() {
        var draft = content.getDraft(course.getId()).getData();
        content.publishContent(course.getId(), dto(draft, "Official"));
        director();
        var edit = CourseDTO.builder().id(course.getId()).courseCode("BASE").courseName("Renamed")
            .department("Review Dept").majorCode("SE").credits(3.0).hours(48).courseType("core").description("Bypass").build();
        assertEquals("Official", archives.saveCourse(edit).getDescription());
        edit.setCourseCode("CHANGED");
        assertThrows(IllegalArgumentException.class, () -> archives.saveCourse(edit));
        edit.setCourseCode("BASE"); edit.setDepartment("Other Dept");
        assertThrows(ForbiddenException.class, () -> archives.saveCourse(edit));
    }

    @Test void legacyMigrationIsRepeatableAndPreservesAuditHistory() {
        var published = revisions.saveAndFlush(CourseContentRevision.builder().courseId(course.getId())
            .status("PUBLISHED").version(7).lockVersion(0).description("Historical").publisherName("Historical teacher").build());
        var older = revisions.saveAndFlush(CourseContentRevision.builder().courseId(course.getId()).status("DRAFT").version(0).lockVersion(0).build());
        var latest = revisions.saveAndFlush(CourseContentRevision.builder().courseId(course.getId()).status("DRAFT").version(0).lockVersion(0).build());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE t_course DROP COLUMN created_by, DROP COLUMN updated_by");
        jdbc.execute("ALTER TABLE t_course_content_revision DROP COLUMN publish_version, DROP COLUMN lock_version");
        var path = java.nio.file.Path.of("scripts/deploy/mysql/init/03_us01_us02_integrity.sql");
        if (!java.nio.file.Files.exists(path)) path = java.nio.file.Path.of("..").resolve(path);
        var migration = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource(path));
        migration.setSqlScriptEncoding("UTF-8");
        migration.execute(dataSource); migration.execute(dataSource);
        assertEquals(7, revisions.findById(published.getId()).orElseThrow().getPublishVersion());
        assertEquals("Historical teacher", revisions.findById(published.getId()).orElseThrow().getPublisherName());
        assertEquals("SUPERSEDED", revisions.findById(older.getId()).orElseThrow().getStatus());
        assertEquals(7, revisions.findById(latest.getId()).orElseThrow().getPublishVersion());
        assertNull(courses.findById(course.getId()).orElseThrow().getCreatedBy());
    }
}
