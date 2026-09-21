package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.*;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.CourseScheduleServiceImpl;
import com.classroom.ai.entity.Student;
import com.classroom.ai.repository.StudentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CourseOfferingManagementService.class, CourseScheduleServiceImpl.class, ScheduleConflictService.class, AcademicTermLockService.class, CourseAuthorizationService.class, CourseOfferingQueryService.class, CourseOfferingHistoryController.class, com.classroom.ai.modules.course.service.impl.CourseServiceImpl.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named="US0102_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13316/us0102_test.*")
class CourseSchedulingMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { CourseWorkflowMysqlTest.database(registry); }
    @Autowired CourseOfferingManagementService management;
    @Autowired CourseScheduleServiceImpl scheduler;
    @Autowired CourseAuthorizationService authorization;
    @org.springframework.boot.test.mock.mockito.SpyBean CourseOfferingRepository offerings;
    @Autowired CourseOfferingHistoryController history;
    @Autowired CourseOfferingQueryService query;
    @Autowired MajorRepository majors;
    @Autowired com.classroom.ai.modules.course.service.impl.CourseServiceImpl legacy;
    @Autowired CourseScheduleRepository schedules;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingTeacherRepository relations;
    @Autowired OfferingStudentEnrollmentRepository enrollments;
    @Autowired StudentRepository students;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired javax.sql.DataSource dataSource;
    Course course; Teacher t1,t2,t3;

    static void director() { AuthContext.setCurrentUser(UserVO.builder().username("test_director").role(RoleEnum.DIRECTOR).department("Test Dept").build()); }
    @BeforeEach void prepare() {
        org.mockito.Mockito.reset(offerings);
        schedules.deleteAll(); relations.deleteAll(); enrollments.deleteAll(); offerings.deleteAll(); courses.deleteAll(); teachers.deleteAll(); students.deleteAll(); majors.deleteAll();
        course=courses.saveAndFlush(Course.builder().courseCode("US03").courseName("软件项目管理").department("Test Dept").majorCode("SE").credits(3.0).hours(48).courseType("core").build());
        t1=teachers.saveAndFlush(Teacher.builder().teacherCode("T1").teacherName("郭军").department("Test Dept").build());
        t2=teachers.saveAndFlush(Teacher.builder().teacherCode("T2").teacherName("协同教师").department("Test Dept").build());
        t3=teachers.saveAndFlush(Teacher.builder().teacherCode("T3").teacherName("郭军").department("Test Dept").build());
        director();
    }
    @AfterEach void clear() { AuthContext.clear(); }
    CourseOfferingDTO dto(String term, Teacher primary, Teacher... others) {
        var d=new CourseOfferingDTO(); d.setCourseId(course.getId()); d.setAcademicTerm(term); d.setClassName("软件工程教学班");
        d.setPrimaryTeacherId(primary.getId()); d.setCollaboratingTeacherIds(Arrays.stream(others).map(Teacher::getId).toList()); d.setStudentNumbers(List.of()); return d;
    }
    CourseOffering offering(String term,Teacher primary,Teacher... others) { return management.save(null,dto(term,primary,others)); }
    CourseScheduleDTO schedule(CourseOffering o,String room) { return CourseScheduleDTO.builder().offeringId(o.getId()).classroom(room).dayOfWeek(3).startWeek(1).endWeek(16).startPeriod(3).endPeriod(4).build(); }
    @Test void archiveMigrationIsRepeatableAndPreservesUnknownLegacyData() throws Exception {
        var o=offering("legacy-term",t1);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("UPDATE t_course SET major_code=NULL,major_id=NULL WHERE id=?",course.getId());
        jdbc.update("UPDATE t_course_offering SET major_code=NULL,major_id=NULL WHERE id=?",o.getId());
        String select="SELECT id,course_id,academic_term,class_name,major_code,major_id,student_count FROM t_course_offering WHERE id=?";
        var before=jdbc.queryForMap(select,o.getId());
        jdbc.execute("ALTER TABLE t_course_offering DROP COLUMN archived_at,DROP COLUMN archived_by,DROP COLUMN history_snapshot");
        var root=java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if(root.getFileName().toString().equals("backend")) root=root.getParent();
        var script=new org.springframework.core.io.FileSystemResource(root.resolve("scripts/deploy/mysql/init/05_us04_archive.sql"));
        try(var connection=dataSource.getConnection()) {
            for(int i=0;i<2;i++) org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
        }
        assertEquals(before,jdbc.queryForMap(select,o.getId()));
        assertNull(jdbc.queryForObject("SELECT archived_at FROM t_course_offering WHERE id=?",java.sql.Timestamp.class,o.getId()));
        assertNull(jdbc.queryForObject("SELECT history_snapshot FROM t_course_offering WHERE id=?",String.class,o.getId()));
    }
    @Test void maintainsTeamAndNinetyFiveStudentsAndFiltersSample() {
        List<String> numbers=new ArrayList<>();
        for(int n=0;n<95;n++) { String number="S"+n; numbers.add(number); students.save(Student.builder().studentId(number).name("学生"+n).className("行政班").build()); }
        students.flush(); var d=dto("秋季",t1,t2); d.setStudentNumbers(numbers);
        var o=management.save(null,d); assertEquals(95,o.getStudentCount()); assertEquals(2,o.getTeachers().size());
        assertEquals(95,enrollments.countByOfferingId(o.getId()));
        scheduler.saveSchedule(schedule(o,"文管 A447"));
        var found=scheduler.getFilteredSchedules("秋季","T2",16,"文管A447",null);
        assertEquals(1,found.size()); assertEquals(95,found.get(0).getOffering().getStudentCount()); assertEquals(2,found.get(0).getOffering().getTeachers().size());
        assertTrue(scheduler.getFilteredSchedules("秋季","T2",17,null,null).isEmpty());
        d.setPrimaryTeacherId(t2.getId()); d.setCollaboratingTeacherIds(List.of(t3.getId())); d.setStudentNumbers(numbers.subList(0,2));
        var updated=management.save(o.getId(),d); assertEquals(2,updated.getStudentCount()); assertEquals("T2",updated.getTeacherCode()); assertEquals(2,relations.findByOfferingId(o.getId()).size());
        assertEquals(2,((List<?>)management.details(o.getId()).get("studentNumbers")).size());
    }
    @Test void sameOfferingAcrossRoomsBlockedAndEditingExcludesItself() {
        var o=offering("T",t1); var saved=scheduler.saveSchedule(schedule(o,"A"));
        assertThrows(ScheduleConflictException.class,()->scheduler.saveSchedule(schedule(o,"B")));
        var edit=schedule(o,"B"); edit.setId(saved.getId()); assertEquals("B",scheduler.saveSchedule(edit).getClassroom());
    }
    @Test void allConflictsReturnHttp409WithReasonsAndCourseDetails() throws Exception {
        var a=offering("T",t1); var b=offering("T",t2); var c=offering("T",t3,t2);
        scheduler.saveSchedule(schedule(a,"文管 A447")); scheduler.saveSchedule(schedule(b,"B"));
        var mvc=MockMvcBuilders.standaloneSetup(new CourseScheduleController(scheduler,offerings,authorization)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/v1/schedules").contentType("application/json").content(new ObjectMapper().writeValueAsString(schedule(c,"文管 A447"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.data.conflicts.length()").value(2))
            .andExpect(jsonPath("$.data.conflicts[0].offering.course.courseName").value("软件项目管理"))
            .andExpect(jsonPath("$.data.conflicts[0].conflictReasons[0]").value("教室冲突"));
    }
    @Test void differentSemesterAndDisjointWeeksSucceed() {
        var a=offering("T",t1); var b=offering("OTHER",t1);
        scheduler.saveSchedule(schedule(a,"A")); scheduler.saveSchedule(schedule(b,"A"));
        var next=schedule(a,"A"); next.setStartWeek(17); next.setEndWeek(18); scheduler.saveSchedule(next); assertEquals(3,schedules.count());
    }
    @Test void changingTeacherRevalidatesAndRollsBackWholeOffering() {
        var a=offering("T",t1); var b=offering("T",t2);
        scheduler.saveSchedule(schedule(a,"A")); scheduler.saveSchedule(schedule(b,"B"));
        assertThrows(ScheduleConflictException.class,()->management.save(b.getId(),dto("T",t2,t1)));
        assertEquals(1,relations.findByOfferingId(b.getId()).size()); assertEquals("T2",offerings.findById(b.getId()).orElseThrow().getTeacherCode());
    }
    @Test void changingSemesterRevalidatesAllSchedules() {
        var a=offering("T",t1); var b=offering("OTHER",t2);
        scheduler.saveSchedule(schedule(a,"A")); var first=schedule(b,"B"); first.setDayOfWeek(1); scheduler.saveSchedule(first); scheduler.saveSchedule(schedule(b,"A"));
        assertThrows(ScheduleConflictException.class,()->management.save(b.getId(),dto("T",t2)));
        assertEquals("OTHER",offerings.findById(b.getId()).orElseThrow().getAcademicTerm());
    }
    @Test void teacherCannotMaintainOfferingOrScheduleAndForgedEditRejected() {
        var a=offering("T",t1); var b=offering("T",t2); var saved=scheduler.saveSchedule(schedule(a,"A"));
        var forged=schedule(b,"B"); forged.setId(saved.getId()); assertThrows(IllegalArgumentException.class,()->scheduler.saveSchedule(forged));
        AuthContext.setCurrentUser(UserVO.builder().role(RoleEnum.TEACHER).teacherCode("T1").build());
        assertThrows(ForbiddenException.class,()->management.save(a.getId(),dto("T",t1)));
        assertThrows(ForbiddenException.class,()->scheduler.saveSchedule(schedule(a,"B")));
        assertThrows(ForbiddenException.class,()->scheduler.deleteSchedule(saved.getId()));
    }
    List<String> race(Supplier<String> a,Supplier<String> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2);
            java.util.function.Function<Supplier<String>,Callable<String>> worker=action -> () -> { ready.countDown(); start.await(); director(); try {return action.get();} finally {AuthContext.clear();} };
            var fa=pool.submit(worker.apply(a)); var fb=pool.submit(worker.apply(b)); assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            return List.of(fa.get(20,TimeUnit.SECONDS),fb.get(20,TimeUnit.SECONDS));
        }
    }
    String attempt(Runnable action) { try {action.run(); return "ok";} catch(ScheduleConflictException expected) {return "conflict";} }
    @Test void concurrentRoomRequestsHaveExactlyOneWinner() throws Exception {
        var a=offering("RACE",t1); var b=offering("RACE",t2);
        var results=race(()->attempt(()->scheduler.saveSchedule(schedule(a,"A"))),()->attempt(()->scheduler.saveSchedule(schedule(b,"A"))));
        assertEquals(1,Collections.frequency(results,"ok")); assertEquals(1,schedules.count());
    }
    @Test void concurrentTeacherRequestsAcrossRoomsHaveExactlyOneWinner() throws Exception {
        var a=offering("RACE",t1); var b=offering("RACE",t2,t1);
        var results=race(()->attempt(()->scheduler.saveSchedule(schedule(a,"A"))),()->attempt(()->scheduler.saveSchedule(schedule(b,"B"))));
        assertEquals(1,Collections.frequency(results,"ok")); assertEquals(1,schedules.count());
    }
    @Test void concurrentOfferingChangeAndScheduleCannotIntroduceConflict() throws Exception {
        var a=offering("T",t1); var b=offering("T",t2); scheduler.saveSchedule(schedule(a,"A"));
        var results=race(()->attempt(()->management.save(a.getId(),dto("T",t1,t2))),()->attempt(()->scheduler.saveSchedule(schedule(b,"B"))));
        assertEquals(1,Collections.frequency(results,"ok"));
    }
    @Test void semesterLockRemainsHeldUntilOuterTransactionCommits() throws Exception {
        var a=offering("HOLD",t1); var b=offering("HOLD",t2);
        var saved=new CountDownLatch(1); var commit=new CountDownLatch(1); var entered=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{director(); try {var tx=new TransactionTemplate(transactionManager); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED); tx.executeWithoutResult(status->{scheduler.saveSchedule(schedule(a,"A")); saved.countDown(); try {assertTrue(commit.await(10,TimeUnit.SECONDS));} catch(InterruptedException e){throw new RuntimeException(e);} });} finally {AuthContext.clear();}});
            assertTrue(saved.await(10,TimeUnit.SECONDS));
            var second=pool.submit(()->{director(); entered.countDown(); try{return attempt(()->scheduler.saveSchedule(schedule(b,"A")));} finally {AuthContext.clear();}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            try { assertThrows(TimeoutException.class,()->second.get(400,TimeUnit.MILLISECONDS)); } finally {commit.countDown();}
            first.get(10,TimeUnit.SECONDS); assertEquals("conflict",second.get(10,TimeUnit.SECONDS));
        } finally {commit.countDown();}
    }

    @Test void emptyRosterDoesNotFallBackToStaleCountAndEmptyHistoryIsZero() {
        var o=offering("EMPTY",t1); o.setStudentCount(99); offerings.saveAndFlush(o);
        var h=history.getOfferingHistory("EMPTY").getData(); assertEquals(0,h.getCumulativePersonTimes()); assertEquals(0,h.getItems().get(0).getStudentCount());
        assertEquals(99,offerings.findById(o.getId()).orElseThrow().getStudentCount());
        var empty=history.getOfferingHistory("MISSING").getData(); assertEquals(0,empty.getTotalOfferings());assertTrue(empty.getItems().isEmpty());
    }
    @Test void stableIdListsCountSameStudentAcrossOfferingsButNotTeachers() {
        var student=students.saveAndFlush(Student.builder().studentId("S1").name("同一学生").className("行政班").build());
        var d=dto("T",t1);d.setTeacherIds(List.of(t1.getId(),t2.getId()));d.setStudentIds(List.of(student.getId()));
        var a=management.save(null,d); management.save(null,d);
        var h=history.getOfferingHistory("T").getData();assertEquals(2,h.getTotalOfferings());assertEquals(2,h.getCumulativePersonTimes());assertEquals(2,h.getItems().get(0).getTeachers().size());
        assertEquals(List.of(student.getId()),management.details(a.getId()).get("studentIds"));
    }
    @Test void archiveIsIdempotentAndPreservesDisplayAndCountAfterOtherChanges() {
        var student=students.saveAndFlush(Student.builder().studentId("S1").name("学生").build());
        var d=dto("T",t1,t2);d.setStudentIds(List.of(student.getId()));var a=management.save(null,d);var b=management.save(null,d);
        scheduler.saveSchedule(schedule(a,"文管 A447"));
        var frozen=management.archive(a.getId());var time=frozen.getArchivedAt();
        assertEquals(1,frozen.getSnapshotStudentCount());assertEquals(time,management.archive(a.getId()).getArchivedAt());
        legacy.removeStudentFromOffering(b.getId(),"S1"); course.setCourseName("后来修改的课程名"); courses.saveAndFlush(course);
        var h=history.getOfferingHistory("T").getData();var archived=h.getItems().stream().filter(i->i.getOfferingId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(1,archived.getStudentCount());assertEquals("软件项目管理",archived.getCourseName());assertEquals("文管 A447",archived.getClassroom());assertEquals(1,h.getCumulativePersonTimes());
    }
    @Test void everyNormalMutationRejectsArchivedOffering() {
        var a=offering("T",t1);var row=scheduler.saveSchedule(schedule(a,"A"));management.archive(a.getId());
        assertThrows(IllegalStateException.class,()->management.save(a.getId(),dto("OTHER",t2)));
        assertThrows(IllegalStateException.class,()->management.delete(a.getId()));
        assertThrows(IllegalStateException.class,()->legacy.addStudentsToOffering(a.getId(),List.of("S")));
        assertThrows(IllegalStateException.class,()->legacy.removeStudentFromOffering(a.getId(),"S"));
        assertThrows(IllegalStateException.class,()->scheduler.saveSchedule(schedule(a,"B")));
        assertThrows(IllegalStateException.class,()->scheduler.deleteSchedule(row.getId()));
    }
    @Test void failedArchiveRollsBackSnapshotAndStatus() {
        var a=offering("T",t1);
        org.mockito.Mockito.doThrow(new IllegalStateException("injected failure")).when(offerings).saveAndFlush(org.mockito.ArgumentMatchers.argThat(o->Boolean.TRUE.equals(o.getIsSnapshotFrozen())));
        assertThrows(IllegalStateException.class,()->management.archive(a.getId()));
        org.mockito.Mockito.reset(offerings);
        var unchanged=offerings.findById(a.getId()).orElseThrow();assertFalse(unchanged.getIsSnapshotFrozen());assertNull(unchanged.getSnapshotStudentCount());assertNull(unchanged.getArchivedAt());
    }
    @Test void concurrentArchiveAndRosterEditShareOfferingLock() throws Exception {
        var student=students.saveAndFlush(Student.builder().studentId("S1").name("学生").build());var a=offering("T",t1);
        var results=race(()->{management.archive(a.getId());return "archived";},()->{try{legacy.addStudentsToOffering(a.getId(),List.of("S1"));return "added";}catch(IllegalStateException expected){return "frozen";}});
        var frozen=offerings.findById(a.getId()).orElseThrow();assertEquals(enrollments.countByOfferingId(a.getId()),frozen.getSnapshotStudentCount().longValue());assertTrue(frozen.getIsSnapshotFrozen());
        assertTrue(results.contains("archived"));
    }
    @Test void combinedSearchUsesIdsAndAndPermissionsWithCollaboratorDeduplication() {
        var major=majors.saveAndFlush(Major.builder().majorCode("SE").majorName("软件工程").department("Test Dept").build());
        var other=majors.saveAndFlush(Major.builder().majorCode("AI").majorName("人工智能").department("Other Dept").build());
        course.setMajorId(major.getId());courses.saveAndFlush(course);
        var a=offering("T",t1,t2);offering("OTHER",t1,t2);
        AuthContext.setCurrentUser(UserVO.builder().role(RoleEnum.SUPERVISOR).authorizedMajors("SE").build());
        assertEquals(2,query.search(null,null,null,null,null,null).size());
        assertEquals(1,query.search("T","IGNORED","软件","AI",major.getId(),t2.getId()).size());
        assertTrue(query.search("T",null,"不匹配",null,major.getId(),t2.getId()).isEmpty());
        assertEquals(1,query.search("T","协同教师",null,"SE",null,null).size());
        assertThrows(ForbiddenException.class,()->query.search(null,null,null,null,other.getId(),null));
        assertThrows(ForbiddenException.class,()->query.search(null,null,null,"AI",null,null));
        AuthContext.clear();assertThrows(com.classroom.ai.common.exception.UnauthorizedException.class,()->query.search(null,null,null,null,null,null));
        assertThrows(com.classroom.ai.common.exception.UnauthorizedException.class,()->history.getOfferingHistory("MISSING"));
    }
    @Test void legacyTeacherDisplayFieldDoesNotGrantAccess() {
        var a=offering("T",t1);relations.deleteAll();
        AuthContext.setCurrentUser(UserVO.builder().role(RoleEnum.TEACHER).teacherCode("T1").build());
        assertThrows(ForbiddenException.class,()->management.details(a.getId()));assertTrue(history.getOfferingHistory(null).getData().getItems().isEmpty());
    }
}
