package com.classroom.ai;

import com.classroom.ai.controller.CameraMonitorController;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.AttendanceAccessService;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.entity.FaceFeature;
import com.classroom.ai.repository.FaceFeatureRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CameraMonitorController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class, AttendanceAccessService.class})
class CameraMonitorHttpTest {
    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean OfferingStudentEnrollmentRepository enrollments;
    @MockBean AttendanceSessionRepository sessions;
    @MockBean FaceFeatureRepository faces;
    @MockBean jakarta.persistence.EntityManagerFactory entityManagerFactory;
    CourseOffering own, foreign; AttendanceSession session;
    @BeforeEach void setup() {
        var em = mock(jakarta.persistence.EntityManager.class);
        when(entityManagerFactory.createEntityManager()).thenReturn(em);
        when(entityManagerFactory.createEntityManager(anyMap())).thenReturn(em);
        when(tokens.validateToken("test-token")).thenReturn(true);
        when(tokens.parseUserFromToken("test-token")).thenReturn(UserVO.builder().id(900L).username("test").role(RoleEnum.TEACHER).build());
        account(RoleEnum.TEACHER);
        own=CourseOffering.builder().id(2L).course(Course.builder().id(1L).courseName("合成课程").department("A").majorCode("SE").build()).className("合成班").teacherCode("T1").build();
        foreign=CourseOffering.builder().id(3L).course(Course.builder().id(9L).department("B").majorCode("AI").build()).teacherCode("T2").build();
        when(offerings.findById(2L)).thenReturn(Optional.of(own));when(offerings.findById(3L)).thenReturn(Optional.of(foreign));
        session=AttendanceSession.builder().id(7L).offering(own).status("ACTIVE").build();when(sessions.findById(7L)).thenReturn(Optional.of(session));
        when(enrollments.findByOfferingId(2L)).thenReturn(List.of(OfferingStudentEnrollment.builder().studentNumber("S1").studentName("合成人员").build()));
        when(faces.findAllByStudentIdIn(List.of("S1"))).thenReturn(List.of(FaceFeature.builder().studentId("S1").featureVector(com.alibaba.fastjson2.JSON.toJSONString(Collections.nCopies(512, .1))).build(),
                FaceFeature.builder().studentId("FOREIGN").featureVector("[]").build()));
    }
    private void account(RoleEnum role) {when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("test").role(role).department("A").teacherCode("T1").authorizedMajors("SE").build()));}
    @Test void anonymousAndForeignRequestsNeverReadFaceVectors() throws Exception {
        mvc.perform(get("/api/visual/monitor-context").param("offeringId","2").param("sessionId","7")).andExpect(status().isUnauthorized());
        for(var role:List.of(RoleEnum.DIRECTOR,RoleEnum.TEACHER,RoleEnum.SUPERVISOR)) {
            account(role);mvc.perform(get("/api/visual/monitor-context").param("offeringId","3").param("sessionId","7").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(faces,enrollments,sessions);
    }
    @Test void allRolesGetOnlyCurrentRosterFacesAndRealCourseIdentity() throws Exception {
        for(var role:List.of(RoleEnum.DIRECTOR,RoleEnum.TEACHER,RoleEnum.SUPERVISOR)) {
            account(role);mvc.perform(get("/api/visual/monitor-context").param("offeringId","2").param("sessionId","7").header("Authorization","Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.faces.length()").value(1))
                    .andExpect(jsonPath("$.data.faces[0].studentId").value("S1")).andExpect(jsonPath("$.data.courseName").value("合成课程"));
        }
        verify(faces,never()).findAll();
    }
    @Test void mismatchedFinishedOrFrozenSessionCannotReadFaces() throws Exception {
        session.setOffering(foreign);
        mvc.perform(get("/api/visual/monitor-context").param("offeringId","2").param("sessionId","7").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
        session.setOffering(own);session.setStatus("FINISHED");
        mvc.perform(get("/api/visual/monitor-context").param("offeringId","2").param("sessionId","7").header("Authorization","Bearer test-token")).andExpect(status().isConflict());
        session.setStatus("ACTIVE");own.setIsSnapshotFrozen(true);
        mvc.perform(get("/api/visual/monitor-context").param("offeringId","2").param("sessionId","7").header("Authorization","Bearer test-token")).andExpect(status().isConflict());
        verifyNoInteractions(faces,enrollments);
    }
}
