package com.classroom.ai;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.dto.*;
import com.classroom.ai.entity.*;
import com.classroom.ai.repository.*;
import com.classroom.ai.service.impl.*;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.modules.supervision.dto.*;
import com.classroom.ai.modules.supervision.entity.*;
import com.classroom.ai.modules.supervision.repository.*;
import com.classroom.ai.modules.supervision.service.impl.*;
import com.classroom.ai.modules.attendance.dto.*;
import com.classroom.ai.modules.attendance.entity.*;
import com.classroom.ai.modules.attendance.repository.*;
import com.classroom.ai.modules.attendance.service.impl.*;
import com.classroom.ai.modules.resource.dto.*;
import com.classroom.ai.modules.resource.repository.*;
import com.classroom.ai.modules.resource.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegressionTest {
    private final StudentRepository students = mock(StudentRepository.class);
    private final FaceFeatureRepository faces = mock(FaceFeatureRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final SupervisionEvaluationRepository evaluations = mock(SupervisionEvaluationRepository.class);

    private FaceServiceImpl faceService() {
        var service = new FaceServiceImpl(students, faces, redis);
        ReflectionTestUtils.setField(service, "defaultDimension", 512);
        return service;
    }

    @Test void partialCacheFallsBackToCompleteDatabaseWithoutDuplicates() {
        var dto = FaceRegisterDTO.builder().studentId("A").featureVector(List.of(1f)).build();
        when(redis.opsForSet().members("face:all_ids")).thenReturn(new LinkedHashSet<>(List.of("A", "B")));
        when(redis.opsForValue().multiGet(anyCollection())).thenReturn(Arrays.asList(JSON.toJSONString(dto), null));
        when(faces.findAll()).thenReturn(List.of(
            FaceFeature.builder().studentId("A").featureVector("[1]").build(),
            FaceFeature.builder().studentId("B").featureVector("[2]").build()));
        assertEquals(List.of("A", "B"), faceService().getAllFaceFeatures().stream().map(FaceRegisterDTO::getStudentId).toList());
        verify(faces).findAll();
    }

    @Test void rejectsWrongDimensionBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> faceService().registerFace(FaceRegisterDTO.builder().studentId("T").featureVector(List.of(1f)).build()));
        verifyNoInteractions(students, faces);
    }

    @Test void rejectsZeroNanAndNullVectors() {
        for (Float bad : Arrays.asList(0f, Float.NaN, null)) {
            assertThrows(IllegalArgumentException.class, () -> faceService().searchFace(FaceSearchDTO.builder().featureVector(Collections.nCopies(512, bad)).build()));
        }
    }

    @Test void rejectsInvalidSimilarityThreshold() {
        assertThrows(IllegalArgumentException.class, () -> faceService().searchFace(FaceSearchDTO.builder().featureVector(Collections.nCopies(512, 1f)).threshold(Double.NaN).build()));
    }

    @Test void emptyPoseFrameClearsOldPoseAndDeduplicatesAttendance() {
        var records = mock(ClassroomRecordRepository.class);
        when(students.count()).thenReturn(2L);
        new VisualDashboardServiceImpl(students, records, redis, offerings).processClassroomStream(ClassroomStreamDTO.builder()
                .presentStudentIds(List.of("A", "A")).studentPoses(Map.of()).lookupRate(0.5).build());
        verify(redis).delete("classroom:realtime:poses");
        verify(records).save(argThat(record -> record.getActualPresent() == 1 && record.getLookupRate() == 0.5));
    }

    @Test void rejectsPercentageWhereStreamRequiresFraction() {
        var service = new VisualDashboardServiceImpl(students, mock(ClassroomRecordRepository.class), redis, offerings);
        assertThrows(IllegalArgumentException.class, () -> service.processClassroomStream(ClassroomStreamDTO.builder().lookupRate(90.0).build()));
    }

    @Test void defaultWeeksHaveValidHumanReadableLabel() {
        var schedules = mock(CourseScheduleRepository.class);
        when(offerings.findById(1L)).thenReturn(Optional.of(CourseOffering.builder().id(1L).build()));
        when(schedules.save(any())).thenAnswer(call -> call.getArgument(0));
        var saved = new CourseScheduleServiceImpl(schedules, offerings).saveSchedule(CourseScheduleDTO.builder()
                .offeringId(1L).classroom("A").dayOfWeek(1).startPeriod(1).endPeriod(2).build());
        assertEquals("1-16周", saved.getWeekRange());
        verify(schedules).findConflictingSchedules("A", 1, 1, 16, 1, 2, null);
    }

    @Test void reversedScheduleRangesAreRejected() {
        var service = new CourseScheduleServiceImpl(mock(CourseScheduleRepository.class), offerings);
        assertThrows(IllegalArgumentException.class, () -> service.checkConflict("A", 1, 16, 1, 1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> service.checkConflict("A", 8, 1, 16, 1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> service.checkConflict("A", 1, 1, 16, 3, 2, null));
    }

    @Test void teacherSeesPublishedAnonymousCopiesOnly() {
        var published = SupervisionEvaluation.builder().status("PUBLISHED").supervisorName("Reviewer").build();
        var pending = SupervisionEvaluation.builder().status("PENDING_DESENSITIZE").publishTime(LocalDateTime.now().plusHours(1)).build();
        when(evaluations.findByTeacherName("Teacher")).thenReturn(List.of(published, pending, SupervisionEvaluation.builder().status("DRAFT").build()));
        var result = new SupervisionServiceImpl(evaluations, offerings).getEvaluationsByTeacher("Teacher");
        assertEquals(1, result.size());
        assertEquals("匿名督导", result.get(0).getSupervisorName());
        assertEquals("Reviewer", published.getSupervisorName());
    }

    @Test void commentsOver500CharactersAndNanScoresAreRejected() {
        when(offerings.findById(1L)).thenReturn(Optional.of(new CourseOffering()));
        var service = new SupervisionServiceImpl(evaluations, offerings);
        assertThrows(IllegalArgumentException.class, () -> service.submitEvaluation(EvaluationSubmitDTO.builder().offeringId(1L).highlights("字".repeat(501)).build()));
        assertThrows(IllegalArgumentException.class, () -> service.submitEvaluation(EvaluationSubmitDTO.builder().offeringId(1L).scoreAttitude(Double.NaN).build()));
        verify(evaluations, never()).save(any());
    }

    @Test void emptyRadarDoesNotInventScoresOrFeedback() {
        var radar = new SupervisionAnalyticsServiceImpl(courses, offerings, evaluations).getTeacherRadar("Teacher");
        assertEquals(0, radar.getEvaluationCount());
        assertEquals(0.0, radar.getOverallScore());
        assertTrue(radar.getWordCloud().isEmpty());
        assertTrue(radar.getHighlightList().isEmpty());
    }

    @Test void unpublishedEvaluationDoesNotLeakIntoRadar() {
        when(evaluations.findByTeacherName("Teacher")).thenReturn(List.of(SupervisionEvaluation.builder()
                .status("PENDING_DESENSITIZE").publishTime(LocalDateTime.now().plusHours(24)).totalScore(90.0).build()));
        assertEquals(0, new SupervisionAnalyticsServiceImpl(courses, offerings, evaluations).getTeacherRadar("Teacher").getEvaluationCount());
    }

    @Test void csvEscapesCommasQuotesAndKeepsDecimalDotInOtherLocales() {
        var course = Course.builder().courseCode("C").courseName("Course, \"A\"").credits(3.5).hours(48).build();
        when(offerings.findAll()).thenReturn(List.of(CourseOffering.builder().id(1L).course(course).teacherName("=1+1").className("A").studentCount(2).build()));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            var csv = new SupervisionAnalyticsServiceImpl(courses, offerings, evaluations).generateAnnualQualityReportCsv();
            assertTrue(csv.contains("\"Course, \"\"A\"\"\""));
            assertTrue(csv.contains("'=1+1,3.5,48"));
        } finally { Locale.setDefault(previous); }
    }

    @Test void finishedAttendanceCannotBeOverwritten() {
        var sessions = mock(AttendanceSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(AttendanceSession.builder().status("FINISHED").build()));
        assertThrows(IllegalStateException.class, () -> new AttendanceServiceImpl(sessions, offerings).finishSession(FinishAttendanceDTO.builder().sessionId(1L).actualCount(1).build()));
        verify(sessions, never()).save(any());
    }

    @Test void zeroExpectedAttendanceDoesNotDivideByOneOrFailOnNull() {
        var sessions = mock(AttendanceSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(AttendanceSession.builder().status("ACTIVE").expectedCount(0).build()));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = new AttendanceServiceImpl(sessions, offerings).finishSession(FinishAttendanceDTO.builder().sessionId(1L).build());
        assertEquals(0.0, result.getAttendanceRate());
        assertEquals(0, result.getActualCount());
    }

    @Test void negativeAttendanceIsRejected() {
        var sessions = mock(AttendanceSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(AttendanceSession.builder().status("ACTIVE").expectedCount(2).build()));
        assertThrows(IllegalArgumentException.class, () -> new AttendanceServiceImpl(sessions, offerings).finishSession(FinishAttendanceDTO.builder().sessionId(1L).actualCount(-1).build()));
    }

    @Test void negativeFileSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CourseResourceServiceImpl(mock(CourseResourceRepository.class), courses)
                .saveResource(CourseResourceDTO.builder().fileSizeBytes(-1L).build()));
    }

    @Test void lockedSyllabusCannotBeEdited() {
        var syllabi = mock(CourseSyllabusRepository.class);
        when(courses.findById(1L)).thenReturn(Optional.of(new Course()));
        when(syllabi.findById(1L)).thenReturn(Optional.of(CourseSyllabus.builder().status("LOCKED").build()));
        assertThrows(IllegalStateException.class, () -> new SyllabusServiceImpl(syllabi, mock(GraduationIndicatorRepository.class), courses)
                .saveSyllabus(SyllabusDTO.builder().id(1L).courseId(1L).build()));
    }
}
