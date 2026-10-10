package com.classroom.ai;

import com.classroom.ai.dto.ClassroomStreamDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.repository.*;
import com.classroom.ai.service.impl.VisualDashboardServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraStreamSessionTest {
    private final CourseOfferingRepository offerings=mock(CourseOfferingRepository.class);
    private final OfferingStudentEnrollmentRepository enrollments=mock(OfferingStudentEnrollmentRepository.class);
    private final ClassroomRecordRepository records=mock(ClassroomRecordRepository.class);
    private final AttendanceSessionRepository sessions=mock(AttendanceSessionRepository.class);
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class,RETURNS_DEEP_STUBS);
    private final VisualDashboardServiceImpl service=new VisualDashboardServiceImpl(mock(StudentRepository.class),records,redis,offerings,enrollments,mock(CourseAuthorizationService.class));
    private final CourseOffering offering=CourseOffering.builder().id(2L).course(Course.builder().id(1L).courseName("合成课程").build()).className("合成班").build();
    private final AttendanceSession session=AttendanceSession.builder().id(7L).offering(offering).status("ACTIVE").build();
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"attendanceSessions",sessions);
        when(offerings.findForUpdate(2L)).thenReturn(Optional.of(offering));
        when(sessions.findForUpdate(7L)).thenReturn(Optional.of(session));
        when(enrollments.findByOfferingId(2L)).thenReturn(List.of(OfferingStudentEnrollment.builder().studentNumber("S1").build(),OfferingStudentEnrollment.builder().studentNumber("S2").build()));
    }
    ClassroomStreamDTO frame(){return ClassroomStreamDTO.builder().offeringId(2L).attendanceSessionId(7L).sessionId("CAMERA_2_7").lookupRate(.75).presentStudentIds(List.of("S1","S1","UNKNOWN_FACE_1")).build();}
    @Test void nativeFrameSynchronizesAuthoritativeRosterMetricsToSession(){
        service.processClassroomStream(frame());
        assertEquals(2,session.getExpectedCount());assertEquals(1,session.getActualCount());assertEquals(50.0,session.getAttendanceRate());assertEquals(75.0,session.getAvgLookupRate());
        verify(sessions).save(session);verify(records).save(any());
    }
    @Test void finishedOrMismatchedSessionsHaveNoStreamSideEffects(){
        session.setStatus("FINISHED");assertThrows(IllegalStateException.class,()->service.processClassroomStream(frame()));
        session.setStatus("ACTIVE");session.setOffering(CourseOffering.builder().id(3L).build());assertThrows(IllegalArgumentException.class,()->service.processClassroomStream(frame()));
        verifyNoInteractions(redis,records,enrollments);verify(sessions,never()).save(any());
    }
    @Test void cacheAndDatabaseFailuresNeverReturnFalseSuccess(){
        var values = redis.opsForValue();
        doThrow(new RuntimeException("cache unavailable")).when(values).set(anyString(),anyString(),anyLong(),any());
        assertThrows(IllegalStateException.class,()->service.processClassroomStream(frame()));verifyNoInteractions(records);
        // A fresh deep-stub cache exercises the independent database failure branch.
        var cache=mock(StringRedisTemplate.class,RETURNS_DEEP_STUBS);
        var writer=new VisualDashboardServiceImpl(mock(StudentRepository.class),records,cache,offerings,enrollments,mock(CourseAuthorizationService.class));
        ReflectionTestUtils.setField(writer,"attendanceSessions",sessions);
        when(records.save(any())).thenThrow(new RuntimeException("database unavailable"));
        assertThrows(IllegalStateException.class,()->writer.processClassroomStream(frame()));
    }
}
