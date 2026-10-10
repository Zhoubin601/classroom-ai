package com.classroom.ai.modules.course;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.attendance.dto.*;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.AttendanceAccessService;
import com.classroom.ai.modules.attendance.service.impl.AttendanceServiceImpl;
import com.classroom.ai.modules.course.controller.SyllabusController;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=update","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CourseAuthorizationService.class,SyllabusServiceImpl.class,SyllabusController.class,CourseServiceImpl.class,
        CourseOfferingManagementService.class,AcademicTermLockService.class,ScheduleConflictService.class,AttendanceServiceImpl.class,AttendanceAccessService.class,CourseImportServiceImpl.class})
@EnabledIfEnvironmentVariable(named="EXP3_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:13317/exp3_test.*")
class AssociationMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        String url=System.getenv("EXP3_MYSQL_URL");
        if(url==null || !url.startsWith("jdbc:mysql://127.0.0.1:13317/exp3_test?")) throw new IllegalStateException("Disposable test database required");
        props.add("spring.datasource.url",()->url);props.add("spring.datasource.username",()->"root");props.add("spring.datasource.password",()->"");
    }
    @Autowired CourseRepository courses;
    @Autowired MajorRepository majors;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseSyllabusRepository syllabi;
    @Autowired GraduationIndicatorRepository mappings;
    @Autowired TrainingIndicatorRepository catalog;
    @Autowired TeacherRepository teachers;
    @Autowired CourseResourceRepository resources;
    @Autowired AttendanceSessionRepository sessions;
    @Autowired SyllabusController plans;
    @Autowired SyllabusServiceImpl syllabusService;
    @Autowired CourseServiceImpl archives;
    @Autowired CourseOfferingManagementService teaching;
    @Autowired AttendanceServiceImpl attendance;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    void user(RoleEnum role,String code) { AuthContext.setCurrentUser(UserVO.builder().id(900L).username("ar-test").realName("关联测试用户")
            .role(role).department("AR-A").teacherCode(code).authorizedMajors("AR-SE").build()); }
    @AfterEach void clear() { AuthContext.clear(); }
    Course course(String code) {
        var major=majors.findByMajorCode("AR-SE").orElseGet(()->majors.saveAndFlush(Major.builder().majorCode("AR-SE").majorName("合成关联测试专业").department("AR-A").build()));
        user(RoleEnum.DIRECTOR,"AR-T");
        return courses.saveAndFlush(Course.builder().courseCode(code).courseName(code).department("AR-A").majorCode("AR-SE")
                .majorId(major.getId()).credits(3.0).hours(48).courseType("测试课程").build());
    }
    CourseOffering offering(Course course,String code) { return offerings.saveAndFlush(CourseOffering.builder().course(course).academicTerm("AR-2026秋")
            .className("合成测试班").teacherCode(code).teacherName("合成测试教师").studentCount(30).build()); }
    SyllabusDTO draft(Course course,String version) { return SyllabusDTO.builder().courseId(course.getId()).version(version)
            .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").indicators(List.of()).build(); }
    CourseDTO courseDto(Course c) { return CourseDTO.builder().id(c.getId()).courseCode(c.getCourseCode()).courseName(c.getCourseName())
            .department(c.getDepartment()).majorCode(c.getMajorCode()).credits(3.0).hours(48).courseType("测试课程").build(); }
    IndicatorDTO item(String code,String category) { return IndicatorDTO.builder().indicatorCode(code).requirementCategory(category)
            .indicatorDescription("合成目录原文").supportWeight("M").targetGoal("目标1").build(); }

    @Test void referencesProtectCatalogAndHistoryWhileNewVersionsStillWork() {
        var c=course("AR-HISTORY");var directory=List.of(item("1-1","工程知识"),item("2-1","问题分析"));
        plans.importPlanIndicators("AR-SE","2026",directory);
        var first=plans.createFromPlan(c.getId(),new SyllabusController.CreateFromPlanRequest("v1","2026")).getData();
        syllabusService.lockSyllabus(first.getId(),"关联测试主任");
        assertThrows(IllegalStateException.class,()->plans.importPlanIndicators("AR-SE","2026",List.of(directory.get(0))));
        assertEquals(2,catalog.findByMajorCodeAndPlanVersionOrderByIndicatorCode("AR-SE","2026").size());
        var second=plans.createFromPlan(c.getId(),new SyllabusController.CreateFromPlanRequest("v2","2026")).getData();
        assertThrows(IllegalStateException.class,()->syllabusService.saveSyllabus(SyllabusDTO.builder().id(first.getId()).courseId(c.getId()).courseGoals("改写历史").build()));
        syllabusService.saveSyllabus(SyllabusDTO.builder().id(second.getId()).courseId(c.getId()).courseGoals("新目标").build());
        assertEquals("2026",syllabi.findById(second.getId()).orElseThrow().getPlanVersion());
        assertEquals("LOCKED",syllabi.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(2,mappings.findBySyllabusId(first.getId()).size());
    }
    @Test void associationCountsProtectMajorEvenWhenOnlyAResourceExists() {
        var c=course("AR-RESOURCE");var next=majors.saveAndFlush(Major.builder().majorCode("AR-CS").majorName("另一测试专业").department("AR-A").build());
        var dto=courseDto(c);dto.setMajorCode(next.getMajorCode());assertEquals("AR-CS",archives.saveCourse(dto).getMajorCode());
        resources.saveAndFlush(CourseResource.builder().course(c).resourceName("合成课件").chapter("第一章").tag("理论").fileType("PDF").fileUrl("/synthetic.pdf").build());
        assertTrue(courses.countAssociatedRecords(c.getId())>0);
        dto.setMajorCode("AR-SE");assertThrows(IllegalStateException.class,()->archives.saveCourse(dto));assertEquals("AR-CS",c.getMajorCode());
    }
    @Test void offeringCannotReassignExistingAttendanceToAnotherCourse() {
        var first=course("AR-CLASS1");var second=course("AR-CLASS2");var off=offering(first,"AR-T");
        var session=attendance.startSession(StartAttendanceDTO.builder().offeringId(off.getId()).build());
        var teacher=teachers.saveAndFlush(Teacher.builder().teacherCode("AR-T").teacherName("合成测试教师").department("AR-A").build());
        var dto=new CourseOfferingDTO();dto.setCourseId(second.getId());dto.setAcademicTerm(off.getAcademicTerm());dto.setClassName(off.getClassName());
        dto.setPrimaryTeacherId(teacher.getId());dto.setCollaboratingTeacherIds(List.of());dto.setStudentNumbers(List.of());
        assertThrows(IllegalArgumentException.class,()->teaching.save(off.getId(),dto));
        entityManager.flush();assertEquals(first.getId(),sessions.findById(session.getId()).orElseThrow().getOffering().getCourse().getId());
    }
    @Test void attendanceHasScopeServerIdentityAndFrozenHistoryProtection() {
        var c=course("AR-ATTENDANCE");var own=offering(c,"AR-T");var foreign=offering(c,"OTHER");
        user(RoleEnum.TEACHER,"AR-T");assertThrows(ForbiddenException.class,()->attendance.startSession(StartAttendanceDTO.builder().offeringId(foreign.getId()).build()));
        var ownSession=attendance.startSession(StartAttendanceDTO.builder().offeringId(own.getId()).operatorRole("DIRECTOR").operatorName("冒用人").build());
        assertEquals("TEACHER",ownSession.getOperatorRole());assertEquals("关联测试用户",ownSession.getOperatorName());
        user(RoleEnum.SUPERVISOR,null);assertEquals(ownSession.getId(),attendance.getCurrentActiveSession(own.getId()).getId());
        var finished=attendance.finishSession(FinishAttendanceDTO.builder().sessionId(ownSession.getId()).actualCount(20).build());assertEquals("SUPERVISOR",finished.getOperatorRole());
        own.setIsSnapshotFrozen(true);offerings.saveAndFlush(own);
        assertThrows(IllegalStateException.class,()->attendance.startSession(StartAttendanceDTO.builder().offeringId(own.getId()).build()));
    }
    @Test void crossRoomTeacherReadsCatalogUsingActualAssignment() {
        var c=course("AR-CROSS");offering(c,"AR-T");
        AuthContext.setCurrentUser(UserVO.builder().id(901L).username("ar-cross").role(RoleEnum.TEACHER).department("AR-B").teacherCode("AR-T").build());
        assertEquals(12,plans.getPlanIndicators("AR-SE",RecommendedIndicatorTemplate.VERSION).getData().size());
        assertThrows(ForbiddenException.class,()->plans.importPlanIndicators("AR-SE","2026",List.of(item("1-1","工程知识"))));
    }
    @Test void prerequisiteCycleIsRejectedAndUnknownLegacyTextRemainsEditable() {
        var first=course("AR-P1");var second=course("AR-P2");var dto=courseDto(first);dto.setPrerequisites(second.getCourseCode());
        archives.saveCourse(dto);
        var other=courseDto(second);other.setPrerequisites(first.getCourseCode());assertThrows(IllegalArgumentException.class,()->archives.saveCourse(other));
        other.setPrerequisites("无");assertEquals("",archives.saveCourse(other).getPrerequisites());
        second.setPrerequisites("《尚未入库基础课》");courses.saveAndFlush(second);other.setPrerequisites(second.getPrerequisites());
        assertEquals("《尚未入库基础课》",archives.saveCourse(other).getPrerequisites());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentIdenticalMappingOnlySavesOneRow() throws Exception {
        var tx=new TransactionTemplate(transactions);
        Long courseId=tx.execute(status->{var c=course("AR-CONCURRENT");syllabusService.saveSyllabus(draft(c,"v1"));return c.getId();});
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> insert=()->{user(RoleEnum.DIRECTOR,"AR-T");try {start.await();syllabusService.addIndicator(courseId,item("1-1","工程知识"));return true;}
                catch(IllegalArgumentException duplicate){return false;}finally{AuthContext.clear();}};
            var one=pool.submit(insert);var two=pool.submit(insert);start.countDown();
            assertNotEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
            Integer savedCount=tx.execute(status->mappings.findBySyllabusId(syllabusService.getLatestSyllabus(courseId).getId()).size());
            assertEquals(1,savedCount.intValue());
        } finally {
            pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);
            tx.executeWithoutResult(status->{var syllabus=syllabusService.getLatestSyllabus(courseId);mappings.deleteBySyllabusId(syllabus.getId());syllabi.delete(syllabus);courses.deleteById(courseId);
                majors.findByMajorCode("AR-SE").ifPresent(majors::delete);});
        }
    }
}
