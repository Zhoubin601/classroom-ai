package com.classroom.ai;

import com.classroom.ai.controller.VisualDashboardController;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.repository.*;
import com.classroom.ai.service.impl.VisualDashboardServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VisualDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class, VisualDashboardServiceImpl.class})
class VisualHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired VisualDashboardServiceImpl visual;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean OfferingStudentEnrollmentRepository enrollments;
    @MockBean StudentRepository students;
    @MockBean ClassroomRecordRepository records;
    @MockBean StringRedisTemplate redis;
    @MockBean jakarta.persistence.EntityManagerFactory entityManagerFactory;
    CourseOffering own, foreign;
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(visual, "entityManager", null);
        var em = mock(jakarta.persistence.EntityManager.class);
        when(entityManagerFactory.createEntityManager()).thenReturn(em);
        when(entityManagerFactory.createEntityManager(anyMap())).thenReturn(em);
        when(tokens.validateToken("test-token")).thenReturn(true);
        when(tokens.parseUserFromToken("test-token")).thenReturn(UserVO.builder().id(900L).username("teacher").role(RoleEnum.DIRECTOR).build());
        account(RoleEnum.TEACHER, "A", "SE");
        var course = Course.builder().id(1L).courseName("授权课程").department("A").majorCode("SE").build();
        own = CourseOffering.builder().id(100L).course(course).teacherCode("T1").isSnapshotFrozen(false).build();
        foreign = CourseOffering.builder().id(101L).course(Course.builder().id(2L).department("B").majorCode("AI").build()).teacherCode("T2").build();
        for (var offering : List.of(own, foreign)) {
            when(offerings.findById(offering.getId())).thenReturn(Optional.of(offering));
            when(offerings.findForUpdate(offering.getId())).thenReturn(Optional.of(offering));
        }
    }
    private void account(RoleEnum role, String dept, String majors) {
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("teacher")
                .role(role).department(dept).teacherCode("T1").authorizedMajors(majors).build()));
    }
    @Test void anonymousRequestsAreRejectedBeforeReadingOrWritingCaches() throws Exception {
        mvc.perform(get("/api/visual/students/status").param("offeringId", "100")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/visual/report/stream").contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":100}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(redis, records, students);
    }
    @Test void allThreeRolesCannotBypassOfferingScopeThroughAnyRealtimeEndpoint() throws Exception {
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR)) {
            account(role, "A", "SE");
            for (var path : List.of("overview", "trend", "students/status"))
                mvc.perform(get("/api/visual/" + path).param("offeringId", "101").header("Authorization", "Bearer test-token"))
                        .andExpect(status().isForbidden());
            mvc.perform(post("/api/visual/reset").param("offeringId", "101").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/visual/report/stream").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"offeringId\":101,\"className\":\"授权班级\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(redis, records, students, enrollments);
    }
    @Test void missingOfferingNeverFallsBackToGlobalRosterOrActiveClass() throws Exception {
        for (var path : List.of("overview", "trend", "students/status"))
            mvc.perform(get("/api/visual/" + path).header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/visual/reset").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/visual/report/stream").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"className\":\"授权班级\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(redis, records, students, enrollments, offerings);
    }
    @Test void frozenOfferingRejectsStreamAndResetForEveryRoleBeforeAnySideEffect() throws Exception {
        own.setIsSnapshotFrozen(true);
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR)) {
            account(role, "A", "SE");
            mvc.perform(post("/api/visual/reset").param("offeringId", "100").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/visual/report/stream").header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"offeringId\":100}")).andExpect(status().isConflict());
        }
        verifyNoInteractions(redis, records, students, enrollments);
    }
    @Test void emptyAuthorizedRosterDoesNotUseStudentsFromOtherClasses() throws Exception {
        mvc.perform(get("/api/visual/overview").param("offeringId", "100").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalRegistered").value(0));
        mvc.perform(get("/api/visual/students/status").param("offeringId", "100").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(students);
    }
}
