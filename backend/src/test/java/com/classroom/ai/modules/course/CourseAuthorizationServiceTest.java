package com.classroom.ai.modules.course;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CourseAuthorizationServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private CourseOfferingTeacherRepository offeringTeacherRepository;

    private CourseAuthorizationService authService;

    private Course seCourse;
    private Course csCourse;
    private CourseOffering seOffering;

    @BeforeEach
    void setUp() {
        authService = new CourseAuthorizationService(courseRepository, offeringRepository, offeringTeacherRepository);

        seCourse = Course.builder()
                .id(1L)
                .courseCode("SE101")
                .courseName("软件工程导论")
                .department("软件工程教研室")
                .majorCode("SE")
                .teacherName("郭军")
                .build();

        csCourse = Course.builder()
                .id(2L)
                .courseCode("CS101")
                .courseName("计算机体系结构")
                .department("计算机科学教研室")
                .majorCode("CS")
                .teacherName("姜琳颖")
                .build();

        seOffering = CourseOffering.builder()
                .id(101L)
                .course(seCourse)
                .teacherCode("T2024001")
                .teacherName("郭军")
                .majorCode("SE")
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("未登录校验：未携带登录上下文时统一拒绝并抛出 401 Unauthorized")
    void testUnauthenticated_Throws401() {
        assertThrows(UnauthorizedException.class, () -> authService.validateCourseRead(seCourse));
        assertThrows(UnauthorizedException.class, () -> authService.validateOfferingRead(seOffering));
    }

    @Test
    @DisplayName("主任教研室隔离：主任仅能读写本教研室课程，跨教研室抛出 403 Forbidden")
    void testDirector_DepartmentIsolation() {
        AuthContext.setCurrentUser(UserVO.builder()
                .username("director.se")
                .role(RoleEnum.DIRECTOR)
                .department("软件工程教研室")
                .build());

        // 允许访问本教研室课程
        assertDoesNotThrow(() -> authService.validateCourseRead(seCourse));
        assertDoesNotThrow(() -> authService.validateCourseWrite(seCourse));

        // 拦截跨教研室课程
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> authService.validateCourseRead(csCourse));
        assertTrue(ex.getMessage().contains("教研室主任仅可管理本教研室"));
    }

    @Test
    @DisplayName("教师课程绑定：教师仅能读写本人关联课程与班次，未关联抛出 403")
    void testTeacher_CourseBinding() {
        AuthContext.setCurrentUser(UserVO.builder()
                .username("guojun")
                .role(RoleEnum.TEACHER)
                .teacherCode("T2024001")
                .realName("郭军")
                .build());

        org.mockito.Mockito.when(offeringRepository.findByCourseId(1L)).thenReturn(java.util.List.of(seOffering));
        assertDoesNotThrow(() -> authService.validateCourseRead(seCourse));
        assertDoesNotThrow(() -> authService.validateOfferingRead(seOffering));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> authService.validateCourseRead(csCourse));
        assertTrue(ex.getMessage().contains("任课教师仅可访问本人主讲或关联的课程档案"));
    }

    @Test
    @DisplayName("督导专业授权：督导仅能读取已授权专业课程，未授权或专业缺失抛出 403，写操作一律 403")
    void testSupervisor_MajorAuthorizationAndReadOnly() {
        AuthContext.setCurrentUser(UserVO.builder()
                .username("supervisor")
                .role(RoleEnum.SUPERVISOR)
                .authorizedMajors("SE")
                .build());

        // 读取已授权专业
        assertDoesNotThrow(() -> authService.validateCourseRead(seCourse));

        // 读取未授权专业
        ForbiddenException exMajor = assertThrows(ForbiddenException.class, () -> authService.validateCourseRead(csCourse));
        assertTrue(exMajor.getMessage().contains("未授权专业"));

        // 专业缺失默认拒绝 (Deny by Default)
        Course noMajorCourse = Course.builder().id(3L).courseName("未知课程").department("未知").build();
        ForbiddenException exMissing = assertThrows(ForbiddenException.class, () -> authService.validateCourseRead(noMajorCourse));
        assertTrue(exMissing.getMessage().contains("课程专业缺失"));

        // 督导写操作一律拒绝
        ForbiddenException exWrite = assertThrows(ForbiddenException.class, () -> authService.validateCourseWrite(seCourse));
        assertTrue(exWrite.getMessage().contains("严禁执行课程写入/修改操作"));
    }
}
