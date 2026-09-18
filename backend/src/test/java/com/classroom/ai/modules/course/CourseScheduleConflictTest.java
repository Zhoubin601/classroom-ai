package com.classroom.ai.modules.course;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
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
class CourseScheduleConflictTest {

    @Mock
    private CourseScheduleRepository scheduleRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository;

    @InjectMocks
    private CourseScheduleServiceImpl scheduleService;

    private CourseOffering mockOffering;

    @BeforeEach
    void setUp() {
        Course course = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        mockOffering = CourseOffering.builder()
                .id(100L)
                .academicTerm("2024-2025-1")
                .course(course)
                .teacherName("郭军")
                .className("软件工程2024级2班")
                .studentCount(95)
                .build();
    }

    @Test
    @DisplayName("排课冲突校验：同一教室同时段已占用时必须拦截并抛出异常")
    void testScheduleConflictDetection_ShouldThrowException() {
        // 模拟文管 A447 周三第 3-4 节已被占用
        CourseSchedule existing = CourseSchedule.builder()
                .id(1L)
                .offering(mockOffering)
                .classroom("文管 A447")
                .weekRange("1-16周")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(3)
                .startPeriod(3)
                .endPeriod(4)
                .build();

        when(offeringRepository.findById(200L)).thenReturn(Optional.of(CourseOffering.builder().id(200L).academicTerm("2024-2025-1").build()));
        when(scheduleRepository.findConflictingClassroomSchedules(
                any(), eq("文管 A447"), eq(3), eq(1), eq(16), eq(3), eq(4), isNull()
        )).thenReturn(List.of(existing));

        CourseScheduleDTO newScheduleDTO = CourseScheduleDTO.builder()
                .offeringId(200L)
                .classroom("文管 A447")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(3)
                .startPeriod(3)
                .endPeriod(4)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            scheduleService.saveSchedule(newScheduleDTO);
        });

        assertTrue(ex.getMessage().contains("排课冲突拦截"));
        assertTrue(ex.getMessage().contains("文管 A447"));
        assertTrue(ex.getMessage().contains("软件项目管理"));
    }

    @Test
    @DisplayName("无冲突排课：教室时段空闲时正常排课成功")
    void testScheduleSuccess_WhenNoConflict() {
        when(scheduleRepository.findConflictingClassroomSchedules(
                any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()
        )).thenReturn(Collections.emptyList());

        when(offeringRepository.findById(100L)).thenReturn(Optional.of(mockOffering));
        when(scheduleRepository.save(any(CourseSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseScheduleDTO dto = CourseScheduleDTO.builder()
                .offeringId(100L)
                .classroom("信息馆 B201")
                .startWeek(1)
                .endWeek(16)
                .dayOfWeek(2)
                .startPeriod(1)
                .endPeriod(2)
                .build();

        CourseSchedule saved = scheduleService.saveSchedule(dto);
        assertNotNull(saved);
        assertEquals("信息馆 B201", saved.getClassroom());
        assertEquals(2, saved.getDayOfWeek());
    }
}
