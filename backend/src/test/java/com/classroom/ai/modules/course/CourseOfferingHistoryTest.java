package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.CourseOfferingHistoryController;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository;
import com.classroom.ai.modules.course.vo.OfferingHistoryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CourseOfferingHistoryTest {

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private CourseOfferingTeacherRepository offeringTeacherRepository;

    @Mock
    private CourseScheduleRepository scheduleRepository;

    @Mock
    private OfferingStudentEnrollmentRepository enrollmentRepository;

    @Mock
    private com.classroom.ai.modules.course.repository.CourseRepository courseRepository;

    private com.classroom.ai.modules.course.service.CourseAuthorizationService authorizationService;

    private CourseOfferingHistoryController historyController;

    private CourseOffering activeOffering;
    private CourseOffering frozenOffering;

    @BeforeEach
    void setUp() {
        authorizationService = new com.classroom.ai.modules.course.service.CourseAuthorizationService(courseRepository, offeringRepository, offeringTeacherRepository);
        historyController = new CourseOfferingHistoryController(offeringRepository, offeringTeacherRepository, scheduleRepository, enrollmentRepository, authorizationService);

        Course course1 = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").department("测试教研室").build();
        Course course2 = Course.builder().id(2L).courseCode("CS3002").courseName("微服务工程").department("测试教研室").build();

        // 在读班次：学生人数通过 enrollmentRepository 统计（95人）
        activeOffering = CourseOffering.builder()
                .id(101L)
                .course(course1)
                .academicTerm("2024-2025-1")
                .teacherCode("T2024001")
                .teacherName("郭军")
                .className("软件工程2024级1班")
                .majorCode("SE")
                .status("ACTIVE")
                .isSnapshotFrozen(false)
                .studentCount(0)
                .build();

        // 已结课班次：学生人数冻结快照为 88 人
        frozenOffering = CourseOffering.builder()
                .id(102L)
                .course(course2)
                .academicTerm("2023-2024-2")
                .teacherCode("T2024002")
                .teacherName("王伟")
                .className("人工智能2023级1班")
                .majorCode("AI")
                .status("COMPLETED")
                .isSnapshotFrozen(true)
                .snapshotStudentCount(88)
                .studentCount(88)
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("US-04 统计口径测试：在读查名单人数(95)，结课取快照(88)，累计人次=183且多教师不重复计算")
    void testHistoryStatistics_EnrollmentAndFrozenSnapshot() {
        // 模拟教学主任登录（无专业限制、无教师限制）
        AuthContext.setCurrentUser(UserVO.builder()
                .username("director")
                .role(RoleEnum.DIRECTOR).department("测试教研室")
                .realName("张教学")
                .build());

        when(offeringRepository.findAll()).thenReturn(List.of(activeOffering, frozenOffering));
        when(enrollmentRepository.countByOfferingId(101L)).thenReturn(95L);

        // activeOffering 有两位授课老师（郭军、李助教），验证不导致班次人次重复计算
        CourseOfferingTeacher cot1 = CourseOfferingTeacher.builder().offeringId(101L).teacherCode("T2024001").teacherName("郭军").roleInOffering("PRIMARY").build();
        CourseOfferingTeacher cot2 = CourseOfferingTeacher.builder().offeringId(101L).teacherCode("T2024099").teacherName("李助教").roleInOffering("ASSISTANT").build();
        when(offeringTeacherRepository.findByOfferingId(101L)).thenReturn(List.of(cot1, cot2));
        when(offeringTeacherRepository.findByOfferingId(102L)).thenReturn(Collections.emptyList());

        when(scheduleRepository.findByOfferingId(101L)).thenReturn(List.of(CourseSchedule.builder().classroom("文管 A447").build()));
        when(scheduleRepository.findByOfferingId(102L)).thenReturn(List.of(CourseSchedule.builder().classroom("信息馆 B201").build()));

        ApiResponse<OfferingHistoryVO> resp = historyController.getOfferingHistory(null);
        assertNotNull(resp);
        assertEquals(200, resp.getCode());

        OfferingHistoryVO vo = resp.getData();
        assertEquals(2, vo.getTotalOfferings());
        // 95 + 88 = 183 累计人次
        assertEquals(183, vo.getCumulativePersonTimes());

        // 班级1学生数 95，班级2学生数 88
        assertEquals(95, vo.getItems().get(0).getStudentCount());
        assertFalse(vo.getItems().get(0).getIsSnapshotFrozen());
        assertEquals(List.of("郭军", "李助教"), vo.getItems().get(0).getTeachers());

        assertEquals(88, vo.getItems().get(1).getStudentCount());
        assertTrue(vo.getItems().get(1).getIsSnapshotFrozen());
    }

    @Test
    @DisplayName("US-04 教师角色数据权限拦截：郭军老师仅能查看自己主讲或协同的开课历史")
    void testTeacherRoleDataIsolation() {
        AuthContext.setCurrentUser(UserVO.builder()
                .username("guo.jun")
                .role(RoleEnum.TEACHER)
                .teacherCode("T2024001")
                .realName("郭军")
                .build());

        when(offeringRepository.findAll()).thenReturn(List.of(activeOffering, frozenOffering));
        when(enrollmentRepository.countByOfferingId(101L)).thenReturn(95L);
        when(scheduleRepository.findByOfferingId(101L)).thenReturn(Collections.emptyList());

        ApiResponse<OfferingHistoryVO> resp = historyController.getOfferingHistory(null);
        OfferingHistoryVO vo = resp.getData();

        assertEquals(1, vo.getTotalOfferings(), "教师仅能查到与本人关联的 1 个班次");
        assertEquals(95, vo.getCumulativePersonTimes());
        assertEquals("软件项目管理", vo.getItems().get(0).getCourseName());
    }
}
