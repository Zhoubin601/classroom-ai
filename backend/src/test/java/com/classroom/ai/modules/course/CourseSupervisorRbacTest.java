package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.CourseController;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CourseSupervisorRbacTest {

    @Mock
    private CourseService courseService;

    @Mock
    private CourseOfferingRepository courseOfferingRepository;

    @InjectMocks
    private CourseController courseController;

    private Course courseSE;
    private Course courseCS;
    private CourseOffering offeringSE;
    private CourseOffering offeringCS;

    @BeforeEach
    void setUp() {
        courseSE = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").majorCode("SE").build();
        courseCS = Course.builder().id(2L).courseCode("CS1001").courseName("计算机体系结构").majorCode("CS").build();

        offeringSE = CourseOffering.builder().id(101L).course(courseSE).majorCode("SE").className("软工1班").build();
        offeringCS = CourseOffering.builder().id(102L).course(courseCS).majorCode("CS").className("计科1班").build();

        // 模拟督导登录，仅授权 SE 专业
        AuthContext.setCurrentUser(UserVO.builder()
                .username("supervisor.se")
                .role(RoleEnum.SUPERVISOR)
                .realName("王督导")
                .authorizedMajors("SE")
                .build());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("US-06 督导越权查询拦截：请求未授权专业 CS 必须抛出 ForbiddenException (403)")
    void testSupervisorQueryUnauthorizedMajor_ShouldThrowForbidden() {
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            courseController.getOfferings(null, null, null, "CS");
        });

        assertTrue(ex.getMessage().contains("无权检索未授权专业"));
        assertTrue(ex.getMessage().contains("CS"));
    }

    @Test
    @DisplayName("US-06 督导未指定专业时后端强制过滤：只返回授权专业 SE，排除 CS")
    void testSupervisorQueryOfferings_AutoFilterByAuthorizedMajors() {
        when(courseOfferingRepository.searchOfferingsWithMajor(null, null, null, null))
                .thenReturn(List.of(offeringSE, offeringCS));

        ApiResponse<List<CourseOffering>> resp = courseController.getOfferings(null, null, null, null);
        assertNotNull(resp);
        assertEquals(200, resp.getCode());

        List<CourseOffering> list = resp.getData();
        assertEquals(1, list.size());
        assertEquals("SE", list.get(0).getMajorCode());
        assertEquals("软工1班", list.get(0).getClassName());
    }

    @Test
    @DisplayName("US-06 督导按 ID 访问未授权专业课程详情时必须拦截 (403)")
    void testSupervisorGetCourseById_UnauthorizedMajor_ShouldThrowForbidden() {
        when(courseService.getCourseById(2L)).thenReturn(courseCS);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            courseController.getCourseById(2L);
        });

        assertTrue(ex.getMessage().contains("无权直接访问未授权专业课程档案"));
    }

    @Test
    @DisplayName("US-06 督导访问已授权专业课程详情成功")
    void testSupervisorGetCourseById_AuthorizedMajor_Success() {
        when(courseService.getCourseById(1L)).thenReturn(courseSE);

        ApiResponse<Course> resp = courseController.getCourseById(1L);
        assertNotNull(resp);
        assertEquals(200, resp.getCode());
        assertEquals("软件项目管理", resp.getData().getCourseName());
    }
}
