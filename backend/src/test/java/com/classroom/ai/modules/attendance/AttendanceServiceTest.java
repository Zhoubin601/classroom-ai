package com.classroom.ai.modules.attendance;

import com.classroom.ai.modules.attendance.dto.FinishAttendanceDTO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.impl.AttendanceServiceImpl;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceSessionRepository sessionRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private com.classroom.ai.modules.course.repository.CourseScheduleRepository scheduleRepository;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private CourseOffering mockOffering;

    @BeforeEach
    void setUp() {
        Course course = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        mockOffering = CourseOffering.builder()
                .id(100L)
                .course(course)
                .teacherName("郭军")
                .className("软件工程2024级2班")
                .studentCount(95) // 应到 95 人
                .build();
    }

    @Test
    @DisplayName("考勤启动：自动继承班级应到人数与操作人身份，状态设为 ACTIVE")
    void testStartAttendanceSession() {
        StartAttendanceDTO dto = StartAttendanceDTO.builder()
                .offeringId(100L)
                .weekNumber(2)
                .classroom("文管 A447")
                .operatorName("郭军")
                .operatorRole("TEACHER")
                .operatorTitle("任课教师")
                .build();

        when(offeringRepository.findById(100L)).thenReturn(Optional.of(mockOffering));
        when(sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(100L, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(AttendanceSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceSession session = attendanceService.startSession(dto);

        assertNotNull(session);
        assertEquals(95, session.getExpectedCount());
        assertEquals("文管 A447", session.getClassroom());
        assertEquals(2, session.getWeekNumber());
        assertEquals("ACTIVE", session.getStatus());
        assertEquals("郭军", session.getOperatorName());
        assertEquals("TEACHER", session.getOperatorRole());
        assertEquals("任课教师", session.getOperatorTitle());
    }

    @Test
    @DisplayName("考勤启动：教学督导发起考勤时自动解析督导职称")
    void testStartAttendanceSessionBySupervisor() {
        StartAttendanceDTO dto = StartAttendanceDTO.builder()
                .offeringId(100L)
                .weekNumber(2)
                .classroom("文管 A447")
                .operatorName("张督导")
                .operatorRole("SUPERVISOR")
                .build();

        when(offeringRepository.findById(100L)).thenReturn(Optional.of(mockOffering));
        when(sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(100L, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(AttendanceSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceSession session = attendanceService.startSession(dto);

        assertNotNull(session);
        assertEquals("张督导", session.getOperatorName());
        assertEquals("SUPERVISOR", session.getOperatorRole());
        assertEquals("教学督导", session.getOperatorTitle());
    }

    @Test
    @DisplayName("考勤下课归档：精准计算实到与出勤率，操作人信息持久化保存")
    void testFinishAttendanceSession() {
        AttendanceSession activeSession = AttendanceSession.builder()
                .id(1L)
                .offering(mockOffering)
                .expectedCount(95)
                .status("ACTIVE")
                .operatorName("郭军")
                .operatorRole("TEACHER")
                .operatorTitle("任课教师")
                .build();

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(AttendanceSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinishAttendanceDTO dto = FinishAttendanceDTO.builder()
                .sessionId(1L)
                .actualCount(91) // 实到 91 人
                .avgLookupRate(88.5)
                .absentStudentIds(List.of("20246099", "20246098"))
                .build();

        AttendanceSession finished = attendanceService.finishSession(dto);

        assertNotNull(finished);
        assertEquals("FINISHED", finished.getStatus());
        assertEquals(91, finished.getActualCount());
        // 91 / 95 * 100 = 95.8%
        assertEquals(95.8, finished.getAttendanceRate());
        assertEquals(88.5, finished.getAvgLookupRate());
        assertEquals("20246099,20246098", finished.getAbsentStudentIds());
        assertEquals("郭军", finished.getOperatorName());
        assertEquals("TEACHER", finished.getOperatorRole());
        assertEquals("任课教师", finished.getOperatorTitle());
    }
}
