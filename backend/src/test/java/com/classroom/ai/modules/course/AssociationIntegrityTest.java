package com.classroom.ai.modules.course;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.attendance.dto.*;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.impl.AttendanceServiceImpl;
import com.classroom.ai.modules.course.controller.SyllabusController;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.repository.StudentRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real authorization/business rules with isolated persistence; no daily database writes. */
class AssociationIntegrityTest {
    @AfterEach void clearIdentity() { AuthContext.clear(); }
    static void user(RoleEnum role, String department, String teacherCode) {
        AuthContext.setCurrentUser(UserVO.builder().id(900L).username("association-test").realName("诊断用户")
                .role(role).department(department).teacherCode(teacherCode).authorizedMajors("SE").build());
    }
    static class Fixture {
        CourseRepository courses = mock(CourseRepository.class);
        CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
        CourseOfferingTeacherRepository relations = mock(CourseOfferingTeacherRepository.class);
        CourseSyllabusRepository syllabi = mock(CourseSyllabusRepository.class);
        GraduationIndicatorRepository mappings = mock(GraduationIndicatorRepository.class);
        TrainingIndicatorRepository catalog = mock(TrainingIndicatorRepository.class);
        MajorRepository majors = mock(MajorRepository.class);
        Course course = Course.builder().id(1L).courseCode("C1").courseName("课程一").department("A")
                .majorCode("SE").majorId(1L).credits(3.0).hours(48).build();
        Major major = Major.builder().id(1L).majorCode("SE").department("A").build();
        CourseSyllabus current = CourseSyllabus.builder().id(20L).course(course).version("v2")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").build();
        CourseAuthorizationService auth = new CourseAuthorizationService(courses, offerings, relations);
        SyllabusServiceImpl service = new SyllabusServiceImpl(syllabi, mappings, courses);
        Fixture() {
            user(RoleEnum.DIRECTOR, "A", "T1");
            when(courses.findById(1L)).thenReturn(Optional.of(course));
            when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
            when(courses.findByCourseCode("C1")).thenReturn(Optional.of(course));
            when(courses.findAllForUpdate()).thenReturn(List.of(course));
            when(courses.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(syllabi.findById(20L)).thenReturn(Optional.of(current));
            when(syllabi.findFirstByCourseIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(current));
            when(syllabi.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mappings.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(majors.findByMajorCode("SE")).thenReturn(Optional.of(major));
            when(majors.findForUpdate("SE")).thenReturn(Optional.of(major));
            ReflectionTestUtils.setField(service, "trainingIndicatorRepository", catalog);
            ReflectionTestUtils.setField(service, "majorRepository", majors);
        }
        SyllabusController controller() { return new SyllabusController(service, auth, mappings, catalog, majors, syllabi, courses); }
        CourseOffering offering() { return CourseOffering.builder().id(100L).course(course).teacherCode("T1")
                .majorCode("SE").majorId(1L).studentCount(30).build(); }
        CourseServiceImpl archives() { return new CourseServiceImpl(courses, offerings, mock(StudentRepository.class), mock(OfferingStudentEnrollmentRepository.class), majors); }
        CourseDTO courseDto() { return CourseDTO.builder().id(1L).courseCode("C1").courseName("课程一").department("A")
                .majorCode("SE").credits(3.0).hours(48).courseType("专业核心课").build(); }
        IndicatorDTO item(String code, String category, String goal) { return IndicatorDTO.builder().indicatorCode(code)
                .requirementCategory(category).targetGoal(goal).supportWeight("M").build(); }
        GraduationIndicator mapping(long id, String code, String goal) { return GraduationIndicator.builder().id(id).course(course)
                .syllabus(current).indicatorCode(code).requirementCategory("工程知识").supportWeight("M").targetGoal(goal).build(); }
    }
    @Test void everyIndicatorWriteRejectsMismatchedCategoryBeforeSaving() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.service.addIndicator(1L, f.item("2-1", "工程知识", "目标1")));
        var row = f.mapping(80L, "1-1", "目标1");
        when(f.mappings.findById(80L)).thenReturn(Optional.of(row));
        assertThrows(IllegalArgumentException.class, () -> f.service.updateIndicator(80L, f.item("2-1", "工程知识", "目标1")));
        assertEquals("1-1", row.getIndicatorCode());
        assertThrows(IllegalArgumentException.class, () -> f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L)
                .indicators(List.of(SyllabusDTO.IndicatorDTO.builder().indicatorCode("2-1").requirementCategory("工程知识").build())).build()));
        verify(f.mappings, never()).save(any()); verify(f.syllabi, never()).save(any());
    }
    @Test void duplicateTargetSetsAreRejectedButDifferentGoalsRemainAllowed() {
        var f = new Fixture();
        when(f.mappings.findBySyllabusId(20L)).thenReturn(List.of(f.mapping(80L,"1-1","目标1,目标2")));
        assertThrows(IllegalArgumentException.class, () -> f.service.addIndicator(1L, f.item("1-1","工程知识","目标2，目标1")));
        assertEquals("目标3", f.service.addIndicator(1L, f.item("1-1","工程知识","目标3")).getTargetGoal());
    }
    @Test void bulkSaveRejectsDuplicateMappingsWithoutDeletingExistingRows() {
        var f = new Fixture();
        var row = SyllabusDTO.IndicatorDTO.builder().indicatorCode("1-1").requirementCategory("工程知识").targetGoal("目标1").build();
        assertThrows(IllegalArgumentException.class, () -> f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L).indicators(List.of(row,row)).build()));
        verify(f.mappings, never()).deleteBySyllabusId(any());
    }
    @Test void editingIntoAnExistingMappingIsRejected() {
        var f = new Fixture(); var first = f.mapping(80L,"1-1","目标1"); var second = f.mapping(81L,"1-1","目标2");
        when(f.mappings.findById(81L)).thenReturn(Optional.of(second));
        when(f.mappings.findBySyllabusId(20L)).thenReturn(List.of(first,second));
        assertThrows(IllegalArgumentException.class, () -> f.service.updateIndicator(81L,f.item("1-1","工程知识","目标1")));
        assertEquals("目标2",second.getTargetGoal());
    }
    @Test void historyCannotBeEditedThroughWholeSyllabusSave() {
        var f = new Fixture(); var old = CourseSyllabus.builder().id(10L).course(f.course).version("v1")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").courseGoals("旧目标").build();
        when(f.syllabi.findById(10L)).thenReturn(Optional.of(old));
        assertThrows(IllegalStateException.class, () -> f.service.saveSyllabus(SyllabusDTO.builder().id(10L).courseId(1L).courseGoals("改写").build()));
        assertEquals("旧目标",old.getCourseGoals());
        old.setStatus("LOCKED");
        var draft = f.service.saveSyllabus(SyllabusDTO.builder().id(10L).courseId(1L).version("v3").status("DRAFT").build());
        assertNotSame(old,draft); assertEquals("LOCKED",old.getStatus()); assertEquals("v3",draft.getVersion());
        assertEquals(RecommendedIndicatorTemplate.VERSION,draft.getPlanVersion());
        assertEquals("旧目标",draft.getCourseGoals());
    }
    @Test void omittedPlanIsPreservedAndRebindingNeedsCompleteMappings() {
        var f = new Fixture();
        f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L).courseGoals("新目标").build());
        assertEquals(RecommendedIndicatorTemplate.VERSION,f.current.getPlanVersion()); assertEquals("DRAFT",f.current.getStatus());
        f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L).status("SUBMITTED").build());
        assertEquals("新目标",f.current.getCourseGoals());
        assertThrows(IllegalArgumentException.class, () -> f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L).planVersion("2027").build()));
        assertEquals(RecommendedIndicatorTemplate.VERSION,f.current.getPlanVersion());
    }
    @Test void ambiguousPrerequisiteNamesRequireStableCodes() {
        var courses = mock(CourseRepository.class);
        var one = Course.builder().id(1L).courseCode("A").courseName("同名课").build();
        var two = Course.builder().id(2L).courseCode("B").courseName("同名课").build();
        when(courses.findAllByCourseName("同名课")).thenReturn(List.of(one,two));
        assertThrows(IllegalArgumentException.class, () -> CourseArchiveRules.validatePrerequisites("同名课",Set.of(),courses));
        when(courses.findByCourseCode("A")).thenReturn(Optional.of(one));
        assertDoesNotThrow(() -> CourseArchiveRules.validatePrerequisites("A",Set.of(),courses));
    }
    @Test void referencedCatalogIsImmutableButAnIdenticalReimportIsIdempotent() {
        var f = new Fixture(); var one = new TrainingIndicator(); one.setIndicatorCode("1-1");one.setRequirementCategory("工程知识");one.setIndicatorDescription("目录原文");
        when(f.catalog.findByMajorCodeAndPlanVersionOrderByIndicatorCode("SE","2026")).thenReturn(List.of(one));
        when(f.syllabi.countPlanReferences("SE","2026")).thenReturn(1L);
        var changed = IndicatorDTO.builder().indicatorCode("1-1").requirementCategory("工程知识").indicatorDescription("更改").build();
        assertThrows(IllegalStateException.class, () -> f.controller().importPlanIndicators("SE","2026",List.of(changed)));
        changed.setIndicatorDescription("目录原文");
        assertEquals(1,f.controller().importPlanIndicators("SE","2026",List.of(changed)).getData().size());
        verify(f.catalog,never()).save(any()); verify(f.catalog,never()).deleteAll(any());
    }
    @Test void actualCrossDepartmentAssignmentGrantsCatalogReadOnly() {
        var f = new Fixture(); user(RoleEnum.TEACHER,"B","T1");
        when(f.courses.findByMajorCode("SE")).thenReturn(List.of(f.course));
        when(f.offerings.findByCourseId(1L)).thenReturn(List.of(f.offering()));
        f.auth.validateCourseWrite(f.course);
        assertEquals(12,f.controller().getPlanIndicators("SE",RecommendedIndicatorTemplate.VERSION).getData().size());
        assertThrows(ForbiddenException.class, () -> f.controller().importPlanIndicators("SE","2026",List.of()));
        when(f.offerings.findByCourseId(1L)).thenReturn(List.of());
        assertThrows(ForbiddenException.class, () -> f.controller().getPlanIndicators("SE",RecommendedIndicatorTemplate.VERSION));
    }
    @Test void existingOfferingCannotChangeCourse() {
        var f = new Fixture();var off = f.offering();
        when(f.offerings.findForUpdate(100L)).thenReturn(Optional.of(off));
        var service = new CourseOfferingManagementService(f.offerings,f.courses,mock(TeacherRepository.class),f.relations,
                mock(OfferingStudentEnrollmentRepository.class),mock(StudentRepository.class),mock(CourseScheduleRepository.class),mock(AcademicTermLockService.class),mock(ScheduleConflictService.class),f.auth);
        var dto = new CourseOfferingDTO();dto.setCourseId(2L);dto.setAcademicTerm("2026秋");dto.setClassName("测试班");dto.setPrimaryTeacherId(1L);dto.setCollaboratingTeacherIds(List.of());dto.setStudentNumbers(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.save(100L,dto));
        assertEquals(1L,off.getCourse().getId());verify(f.offerings,never()).saveAndFlush(any());
    }
    @Test void associatedCourseCannotChangeMajorButUnusedCourseCan() {
        var f = new Fixture(); var dto=f.courseDto();dto.setMajorCode("CS");
        when(f.majors.findByMajorCode("CS")).thenReturn(Optional.of(Major.builder().id(2L).majorCode("CS").department("A").build()));
        when(f.courses.countAssociatedRecords(1L)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> f.archives().saveCourse(dto));assertEquals("SE",f.course.getMajorCode());
        when(f.courses.countAssociatedRecords(1L)).thenReturn(0L);
        assertEquals("CS",f.archives().saveCourse(dto).getMajorCode());
    }
    @Test void selfAndMutualPrerequisitesAreRejected() {
        var f = new Fixture(); var dto=f.courseDto();dto.setPrerequisites("《课程一》");
        when(f.courses.findAllByCourseName("课程一")).thenReturn(List.of(f.course));
        assertThrows(IllegalArgumentException.class, () -> f.archives().saveCourse(dto));
        var other=Course.builder().id(2L).courseCode("C2").courseName("课程二").prerequisites("C1").build();
        when(f.courses.findAllForUpdate()).thenReturn(List.of(f.course,other));
        when(f.courses.findByCourseCode("C2")).thenReturn(Optional.of(other));
        dto.setPrerequisites("C2");assertThrows(IllegalArgumentException.class, () -> f.archives().saveCourse(dto));
        verify(f.courses,never()).save(any());
    }
    @Test void unchangedUnknownPrerequisitesDoNotBlockMetadataEditing() {
        var f = new Fixture();f.course.setPrerequisites("《未入库基础课》");var dto=f.courseDto();dto.setPrerequisites(f.course.getPrerequisites());
        assertEquals("《未入库基础课》",f.archives().saveCourse(dto).getPrerequisites());
        dto.setPrerequisites("另一门未知课程");assertThrows(IllegalArgumentException.class, () -> f.archives().saveCourse(dto));
    }
    @Test void noPrerequisiteMarkerIsEmptyAndNamesAreStoredAsStableCodes() {
        var f = new Fixture();var dto=f.courseDto();dto.setPrerequisites("无");
        assertEquals("",f.archives().saveCourse(dto).getPrerequisites());
        var base=Course.builder().id(2L).courseCode("BASE").courseName("基础课").build();
        when(f.courses.findAllForUpdate()).thenReturn(List.of(f.course,base));when(f.courses.findAllByCourseName("基础课")).thenReturn(List.of(base));
        dto.setPrerequisites("《基础课》");assertEquals("BASE",f.archives().saveCourse(dto).getPrerequisites());
    }
    @Test void renamingAReferencedCourseNeedsStableCodeReferences() {
        var f=new Fixture();var other=Course.builder().id(2L).courseCode("C2").courseName("课程二").prerequisites("《课程一》").build();
        when(f.courses.findAllForUpdate()).thenReturn(List.of(f.course,other));var dto=f.courseDto();dto.setCourseName("新名称");
        assertThrows(IllegalStateException.class, () -> f.archives().saveCourse(dto));assertEquals("课程一",f.course.getCourseName());
        other.setPrerequisites("C1");assertEquals("新名称",f.archives().saveCourse(dto).getCourseName());
    }
    @Test void deletingAPrerequisiteCannotLeaveDanglingReferences() {
        var f=new Fixture();var dependent=Course.builder().id(2L).courseCode("C2").courseName("课程二").prerequisites("C1").build();
        when(f.courses.findAllForUpdate()).thenReturn(List.of(f.course,dependent));
        assertThrows(IllegalStateException.class, () -> f.archives().deleteCourse(1L));
        verify(f.courses,never()).deleteById(any());
    }
    @Test void attendanceRejectsForeignOfferingForAllReadAndWritePaths() {
        var f=new Fixture();user(RoleEnum.TEACHER,"B","OUTSIDE");var off=f.offering();var sessions=mock(AttendanceSessionRepository.class);
        when(f.offerings.findById(100L)).thenReturn(Optional.of(off));
        when(f.offerings.findForUpdate(100L)).thenReturn(Optional.of(off));
        var active=AttendanceSession.builder().id(50L).offering(off).status("ACTIVE").build();when(sessions.findForUpdate(50L)).thenReturn(Optional.of(active));
        when(sessions.findOfferingId(50L)).thenReturn(Optional.of(100L));
        var service=new AttendanceServiceImpl(sessions,f.offerings,null,null,f.auth);
        assertThrows(ForbiddenException.class, () -> service.startSession(StartAttendanceDTO.builder().offeringId(100L).build()));
        assertThrows(ForbiddenException.class, () -> service.finishSession(FinishAttendanceDTO.builder().sessionId(50L).build()));
        assertThrows(ForbiddenException.class, () -> service.updateLiveStatus(50L,1,50.0));
        assertThrows(ForbiddenException.class, () -> service.getSessionsByOffering(100L));
        assertThrows(ForbiddenException.class, () -> service.getCurrentActiveSession(100L));
        verify(sessions,never()).save(any());
    }
    @Test void attendanceUsesServerIdentityAndRetainsAuthorizedSupervisorFlow() {
        var f=new Fixture();var off=f.offering();var sessions=mock(AttendanceSessionRepository.class);
        when(f.offerings.findById(100L)).thenReturn(Optional.of(off));when(f.offerings.findForUpdate(100L)).thenReturn(Optional.of(off));when(sessions.save(any())).thenAnswer(inv->inv.getArgument(0));
        when(sessions.findOfferingId(50L)).thenReturn(Optional.of(100L));
        var service=new AttendanceServiceImpl(sessions,f.offerings,null,null,f.auth);
        for(RoleEnum role:RoleEnum.values()) {
            user(role, "A", "T1");
            var result=service.startSession(StartAttendanceDTO.builder().offeringId(100L).operatorName("冒用人").operatorRole("DIRECTOR").operatorTitle("假身份").build());
            assertEquals("诊断用户",result.getOperatorName());assertEquals(role.name(),result.getOperatorRole());
            when(sessions.findForUpdate(50L)).thenReturn(Optional.of(result));
            var finished=service.finishSession(FinishAttendanceDTO.builder().sessionId(50L).operatorName("冒用人").operatorRole("DIRECTOR").build());
            assertEquals(role.name(),finished.getOperatorRole());assertEquals("诊断用户",finished.getOperatorName());
        }
    }
    @Test void currentAttendanceSkipsForeignSessionsAndSupportsExplicitOffering() {
        var f=new Fixture();user(RoleEnum.TEACHER,"A","T1");var own=f.offering();var foreign=CourseOffering.builder().id(101L).course(f.course).teacherCode("T2").build();
        var sessions=mock(AttendanceSessionRepository.class);var ownSession=AttendanceSession.builder().id(50L).offering(own).status("ACTIVE").build();
        when(sessions.findByStatusOrderByCreatedAtDescIdDesc("ACTIVE")).thenReturn(List.of(AttendanceSession.builder().id(51L).offering(foreign).status("ACTIVE").build(),ownSession));
        var service=new AttendanceServiceImpl(sessions,f.offerings,null,null,f.auth);
        assertSame(ownSession,service.getCurrentActiveSession());
        when(f.offerings.findById(100L)).thenReturn(Optional.of(own));when(sessions.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(100L,"ACTIVE")).thenReturn(Optional.of(ownSession));
        assertSame(ownSession,service.getCurrentActiveSession(100L));
    }
    @Test void frozenOfferingAttendanceCannotBeMutated() {
        var f=new Fixture();var off=f.offering();off.setIsSnapshotFrozen(true);when(f.offerings.findForUpdate(100L)).thenReturn(Optional.of(off));
        var sessions=mock(AttendanceSessionRepository.class);var service=new AttendanceServiceImpl(sessions,f.offerings,null,null,f.auth);
        assertThrows(IllegalStateException.class, () -> service.startSession(StartAttendanceDTO.builder().offeringId(100L).build()));
        verify(sessions,never()).save(any());
    }
}
