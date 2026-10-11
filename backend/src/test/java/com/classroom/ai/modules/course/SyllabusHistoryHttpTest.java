package com.classroom.ai.modules.course;

import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.SyllabusController;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SyllabusController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class})
class SyllabusHistoryHttpTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean SyllabusService service;
    @MockBean GraduationIndicatorRepository indicators;
    @MockBean TrainingIndicatorRepository catalog;
    @MockBean MajorRepository majors;
    @MockBean CourseSyllabusRepository syllabi;

    void login(RoleEnum role) {
        when(tokens.validateToken("history-test")).thenReturn(true);
        when(tokens.parseUserFromToken("history-test")).thenReturn(UserVO.builder().username("history-test").build());
        when(accounts.findByUsername("history-test")).thenReturn(Optional.of(UserAccount.builder()
                .username("history-test").role(role).department("A").authorizedMajors("SE").build()));
    }

    Course course() { return Course.builder().id(1L).department("A").majorCode("SE").build(); }

    @Test void anonymousHistoryReadIsRejected() throws Exception {
        mvc.perform(get("/api/v1/syllabus/course/1/indicators?syllabusId=10"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(indicators, syllabi);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RoleEnum.class, names = {"DIRECTOR", "SUPERVISOR"})
    void authorizedUsersReadTheRequestedSnapshot(RoleEnum role) throws Exception {
        login(role);
        Course c = course();
        when(courses.findById(1L)).thenReturn(Optional.of(c));
        when(syllabi.findById(10L)).thenReturn(Optional.of(CourseSyllabus.builder().id(10L).course(c).version("v1").build()));
        when(indicators.findBySyllabusId(10L)).thenReturn(List.of(GraduationIndicator.builder()
                .id(100L).course(c).indicatorCode("1-1").supportWeight("H").targetGoal("旧版目标").build()));
        mvc.perform(get("/api/v1/syllabus/course/1/indicators?syllabusId=10").header("Authorization", "Bearer history-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].targetGoal").value("旧版目标"));
        verify(service, never()).getIndicatorsByCourseId(anyLong());
    }

    @Test void unauthorizedCourseDoesNotExposeItsHistory() throws Exception {
        login(RoleEnum.SUPERVISOR);
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).majorCode("CS").build()));
        mvc.perform(get("/api/v1/syllabus/course/1/indicators?syllabusId=10").header("Authorization", "Bearer history-test"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(syllabi, indicators);
    }

    @Test void versionOfAnotherCourseCannotBeReadThroughAnAuthorizedCourse() throws Exception {
        login(RoleEnum.DIRECTOR);
        when(courses.findById(1L)).thenReturn(Optional.of(course()));
        when(syllabi.findById(10L)).thenReturn(Optional.of(CourseSyllabus.builder().id(10L)
                .course(Course.builder().id(2L).department("B").build()).build()));
        mvc.perform(get("/api/v1/syllabus/course/1/indicators?syllabusId=10").header("Authorization", "Bearer history-test"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(indicators);
    }

    @Test void missingVersionIsRejected() throws Exception {
        login(RoleEnum.DIRECTOR);
        when(courses.findById(1L)).thenReturn(Optional.of(course()));
        mvc.perform(get("/api/v1/syllabus/course/1/indicators?syllabusId=999").header("Authorization", "Bearer history-test"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(indicators);
    }

    @Test void omittingVersionPreservesLatestQuery() throws Exception {
        login(RoleEnum.DIRECTOR);
        when(courses.findById(1L)).thenReturn(Optional.of(course()));
        when(service.getIndicatorsByCourseId(1L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/syllabus/course/1/indicators").header("Authorization", "Bearer history-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(syllabi, indicators);
    }
}
