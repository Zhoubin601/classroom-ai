package com.classroom.ai.modules.attendance;

import com.classroom.ai.modules.attendance.controller.AttendanceController;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.impl.AttendanceServiceImpl;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AttendanceController.class,com.classroom.ai.modules.auth.controller.AuthController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,CourseAuthorizationService.class,AttendanceServiceImpl.class,com.classroom.ai.modules.attendance.service.AttendanceAccessService.class})
class AttendanceHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired AttendanceServiceImpl attendance;
    @Autowired com.classroom.ai.modules.attendance.service.AttendanceAccessService access;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean CourseScheduleRepository schedules;
    @MockBean OfferingStudentEnrollmentRepository enrollments;
    @MockBean AttendanceSessionRepository sessions;
    @MockBean jakarta.persistence.EntityManagerFactory entityManagerFactory;
    CourseOffering own,foreign;
    @BeforeEach void setup() {
        // MVC slice exercises real HTTP/auth/business rules with mocked persistence.
        // Entity refresh/row locks are exercised separately against isolated MySQL.
        org.springframework.test.util.ReflectionTestUtils.setField(access,"entityManager",null);
        var entityManager=mock(jakarta.persistence.EntityManager.class);
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManagerFactory.createEntityManager(anyMap())).thenReturn(entityManager);
        when(tokens.validateToken("synthetic-token")).thenReturn(true);
        when(tokens.parseUserFromToken("synthetic-token")).thenReturn(UserVO.builder().id(900L).username("teacher").role(RoleEnum.DIRECTOR).build());
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("teacher").realName("真实教师")
                .role(RoleEnum.TEACHER).department("A").teacherCode("T1").build()));
        var course=Course.builder().id(1L).courseCode("C1").courseName("测试课程").department("A").majorCode("SE").build();
        own=CourseOffering.builder().id(100L).course(course).teacherCode("T1").studentCount(30).build();
        foreign=CourseOffering.builder().id(101L).course(course).teacherCode("T2").build();
        when(offerings.findById(100L)).thenReturn(Optional.of(own));when(offerings.findForUpdate(100L)).thenReturn(Optional.of(own));
        when(offerings.findById(101L)).thenReturn(Optional.of(foreign));when(offerings.findForUpdate(101L)).thenReturn(Optional.of(foreign));
        when(sessions.save(any())).thenAnswer(inv->inv.getArgument(0));
    }
    @Test void anonymousAttendanceIs401() throws Exception {
        mvc.perform(get("/api/v1/attendance/current")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/attendance/start").contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":100}"))
                .andExpect(status().isUnauthorized());
        verify(sessions,never()).save(any());
    }
    @Test void foreignOfferingEndpointsAre403BeforeAnyWrite() throws Exception {
        var session=AttendanceSession.builder().id(51L).offering(foreign).status("ACTIVE").build();
        when(sessions.findForUpdate(51L)).thenReturn(Optional.of(session));
        when(sessions.findOfferingId(51L)).thenReturn(Optional.of(101L));
        mvc.perform(get("/api/v1/attendance/offering/101").header("Authorization","Bearer synthetic-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attendance/current").param("offeringId","101").header("Authorization","Bearer synthetic-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance/start").header("Authorization","Bearer synthetic-token").contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":101}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance/finish").header("Authorization","Bearer synthetic-token").contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":51}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance/live-update").header("Authorization","Bearer synthetic-token").param("sessionId","51").param("actualCount","1"))
                .andExpect(status().isForbidden());
        verify(sessions,never()).save(any());
    }
    @Test void spoofedBodyAndOldTokenRolesCannotOverrideCurrentAccount() throws Exception {
        mvc.perform(post("/api/v1/attendance/start").header("Authorization","Bearer synthetic-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"offeringId\":100,\"operatorName\":\"冒用人\",\"operatorRole\":\"DIRECTOR\",\"operatorTitle\":\"冒用头衔\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.operatorName").value("真实教师"))
                .andExpect(jsonPath("$.data.operatorRole").value("TEACHER")).andExpect(jsonPath("$.data.operatorTitle").value("任课教师"));
    }
    @Test void deletedAccountOldTokenReceives401() throws Exception {
        when(accounts.findById(900L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/attendance/current").header("Authorization","Bearer synthetic-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer synthetic-token")).andExpect(status().isUnauthorized());
        verify(sessions,never()).findByStatusOrderByCreatedAtDesc(any());
    }
    @Test void profileUsesCurrentDatabaseIdentityRatherThanTokenClaims() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer synthetic-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.realName").value("真实教师"));
    }
    @Test void explicitCurrentDoesNotReturnAnotherOfferingSession() throws Exception {
        when(sessions.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(100L,"ACTIVE")).thenReturn(Optional.of(AttendanceSession.builder()
                .id(50L).offering(own).status("ACTIVE").build()));
        mvc.perform(get("/api/v1/attendance/current").param("offeringId","100").header("Authorization","Bearer synthetic-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.offering.id").value(100)).andExpect(jsonPath("$.data.id").value(50));
    }
}
