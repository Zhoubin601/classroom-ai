package com.classroom.ai.modules.course;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import com.classroom.ai.modules.course.service.impl.CourseScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CourseScheduleMultiTeacherConflictTest {

    @Mock
    private CourseScheduleRepository scheduleRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private CourseOfferingTeacherRepository offeringTeacherRepository;

    @InjectMocks
    private CourseScheduleServiceImpl scheduleService;

    private CourseOffering offeringA;
    private CourseOffering offeringB;

    @BeforeEach
    void setUp() {
        Course courseA = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        Course courseB = Course.builder().id(2L).courseCode("CS3002").courseName("高可用系统架构").build();

        offeringA = CourseOffering.builder()
                .id(101L)
                .academicTerm("2024-2025-1")
                .course(courseA)
                .teacherCode("T2024001")
                .teacherName("郭军")
                .className("软件工程2024级1班")
                .build();

        offeringB = CourseOffering.builder()
                .id(102L)
                .academicTerm("2024-2025-1")
                .course(courseB)
                .teacherCode("T2024002")
                .teacherName("王伟")
                .className("人工智能2024级1班")
                .build();
    }

    @Test
    @DisplayName("US-03 教师跨教室时段冲突：郭军老师作为联合授课教师在同一时段不同教室已被排课，必须拦截并报409")
    void testTeacherTimeConflictAcrossClassrooms_ShouldBeIntercepted() {
        when(offeringRepository.findById(102L)).thenReturn(Optional.of(offeringB));

        // offeringB 中王伟是主讲，郭军是联合授课助教
        CourseOfferingTeacher cot = CourseOfferingTeacher.builder()
                .offeringId(102L)
                .teacherCode("T2024001")
                .teacherName("郭军")
                .roleInOffering("ASSISTANT")
                .build();
        when(offeringTeacherRepository.findByOfferingId(102L)).thenReturn(List.of(cot));

        // 查找同学期开课：offeringA 是郭军主讲
        when(offeringRepository.findByAcademicTerm("2024-2025-1")).thenReturn(List.of(offeringA, offeringB));

        // offeringA 在信息馆B201 周二第1-2节已排课
        CourseSchedule conflictSchedule = CourseSchedule.builder()
                .id(88L)
                .offering(offeringA)
                .classroom("信息馆 B201")
                .dayOfWeek(2)
                .startPeriod(1)
                .endPeriod(2)
                .startWeek(1)
                .endWeek(16)
                .build();

        // 教室检查无冲突（想排在 文管A447）
        when(scheduleRepository.findConflictingClassroomSchedules(
                any(), eq("文管 A447"), eq(2), eq(1), eq(16), eq(1), eq(2), isNull()
        )).thenReturn(Collections.emptyList());

        // 教师检查发现 conflictSchedule
        when(scheduleRepository.findConflictingTeacherSchedules(
                eq("2024-2025-1"), anyCollection(), eq(2), eq(1), eq(16), eq(1), eq(2), isNull()
        )).thenReturn(List.of(conflictSchedule));

        CourseScheduleDTO dto = CourseScheduleDTO.builder()
                .offeringId(102L)
                .classroom("文管 A447")
                .dayOfWeek(2)
                .startPeriod(1)
                .endPeriod(2)
                .startWeek(1)
                .endWeek(16)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            scheduleService.saveSchedule(dto);
        });

        assertTrue(ex.getMessage().contains("排课冲突拦截"));
        assertTrue(ex.getMessage().contains("郭军"));
        assertTrue(ex.getMessage().contains("信息馆 B201"));
        assertTrue(ex.getMessage().contains("软件项目管理"));
    }

    @Test
    @DisplayName("US-03 教室与教师均无冲突时，排课成功并保存")
    void testScheduleWithoutConflict_ShouldSave() {
        when(offeringRepository.findById(101L)).thenReturn(Optional.of(offeringA));
        when(scheduleRepository.findConflictingClassroomSchedules(
                any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()
        )).thenReturn(Collections.emptyList());
        when(offeringRepository.findByAcademicTerm("2024-2025-1")).thenReturn(List.of(offeringA));
        when(scheduleRepository.save(any(CourseSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseScheduleDTO dto = CourseScheduleDTO.builder()
                .offeringId(101L)
                .classroom("综合楼 301")
                .dayOfWeek(4)
                .startPeriod(5)
                .endPeriod(6)
                .startWeek(1)
                .endWeek(16)
                .build();

        CourseSchedule saved = scheduleService.saveSchedule(dto);
        assertNotNull(saved);
        assertEquals("综合楼 301", saved.getClassroom());
        assertEquals("1-16周", saved.getWeekRange());
    }
}
