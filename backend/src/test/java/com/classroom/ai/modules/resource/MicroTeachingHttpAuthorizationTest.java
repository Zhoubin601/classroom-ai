package com.classroom.ai.modules.resource;

import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.controller.MicroTeachingController;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.repository.MicroTeachingSliceRepository;
import com.classroom.ai.modules.resource.service.impl.MicroTeachingServiceImpl;
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

@WebMvcTest(MicroTeachingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class, MicroTeachingServiceImpl.class})
class MicroTeachingHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean MicroTeachingSliceRepository slices;
    Course own, foreign, collaborative;
    MicroTeachingSlice ownSlice, foreignSlice;

    @BeforeEach void setup() {
        own = Course.builder().id(1L).courseName("本室课程").department("A").majorCode("SE").build();
        foreign = Course.builder().id(2L).courseName("未授权课程").department("B").majorCode("AI").build();
        collaborative = Course.builder().id(3L).courseName("跨室协同课程").department("B").majorCode("CS").build();
        for (var course : List.of(own, foreign, collaborative)) {
            when(courses.findById(course.getId())).thenReturn(Optional.of(course));
            when(offerings.findByCourseId(course.getId())).thenReturn(List.of(CourseOffering.builder()
                    .id(course.getId() + 10).course(course).teacherCode(course == own ? "T1" : "T2").build()));
        }
        when(courses.findAll()).thenReturn(List.of(own, foreign, collaborative));
        when(teachers.findByOfferingId(13L)).thenReturn(List.of(CourseOfferingTeacher.builder()
                .teacherId(90L).teacherCode("T1").build()));
        ownSlice = MicroTeachingSlice.builder().id(100L).course(own).videoTitle("本室切片").bopppsStage("B").sliceUrl("/synthetic.mp4").build();
        foreignSlice = MicroTeachingSlice.builder().id(200L).course(foreign).videoTitle("外室切片").bopppsStage("B").sliceUrl("/foreign.mp4").build();
        when(slices.findById(100L)).thenReturn(Optional.of(ownSlice));
        when(slices.findById(200L)).thenReturn(Optional.of(foreignSlice));
        when(slices.findByCourseId(1L)).thenReturn(List.of(ownSlice));
        when(slices.save(any(MicroTeachingSlice.class))).thenAnswer(i -> i.getArgument(0));
        when(tokens.validateToken("scope-token")).thenReturn(true);
        when(tokens.parseUserFromToken("scope-token")).thenReturn(UserVO.builder().id(900L).username("actor").role(RoleEnum.DIRECTOR).build());
    }

    void account(RoleEnum role, String majors) {
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("actor")
                .role(role).department("A").teacherCode("T1").authorizedMajors(majors).build()));
    }

    String payload(long courseId) {
        return "{\"courseId\":" + courseId + ",\"videoTitle\":\"合成切片\",\"sliceUrl\":\"/synthetic.mp4\",\"bopppsStage\":\"B\"}";
    }

    @Test void anonymousRequestsCannotReadMountOrDelete() throws Exception {
        mvc.perform(get("/api/v1/resources/micro-slices/course/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/resources/micro-slices/stage/B")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/resources/micro-slices").contentType(MediaType.APPLICATION_JSON).content(payload(1))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/resources/micro-slices/100")).andExpect(status().isUnauthorized());
        verifyNoInteractions(courses, slices);
    }

    @Test void threeRolesReadTheirAuthorizedCourse() throws Exception {
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR)) {
            account(role, "SE");
            mvc.perform(get("/api/v1/resources/micro-slices/course/1").header("Authorization", "Bearer scope-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(100));
        }
    }

    @Test void directForeignCourseAndSliceIdsCannotBypassScope() throws Exception {
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR)) {
            account(role, "SE");
            mvc.perform(get("/api/v1/resources/micro-slices/course/2").header("Authorization", "Bearer scope-token")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                    .contentType(MediaType.APPLICATION_JSON).content(payload(2))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/resources/micro-slices/200").param("courseId", "1")
                    .header("Authorization", "Bearer scope-token")).andExpect(status().isForbidden());
        }
        verify(slices, never()).findByCourseId(2L);
        verify(slices, never()).save(any());
        verify(slices, never()).delete(any(MicroTeachingSlice.class));
        verify(slices, never()).deleteById(anyLong());
    }

    @Test void directorAndTeacherCanMountAndDeleteInScope() throws Exception {
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER)) {
            account(role, "SE");
            mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                    .contentType(MediaType.APPLICATION_JSON).content(payload(1)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.course.id").value(1));
            mvc.perform(delete("/api/v1/resources/micro-slices/100").header("Authorization", "Bearer scope-token")).andExpect(status().isOk());
        }
        verify(slices, times(2)).save(any());
        verify(slices, times(2)).delete(ownSlice);
    }

    @Test void supervisorCannotMountOrDeleteEvenAnAuthorizedCourse() throws Exception {
        account(RoleEnum.SUPERVISOR, "SE");
        mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload(1))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/resources/micro-slices/100").header("Authorization", "Bearer scope-token")).andExpect(status().isForbidden());
        verify(slices, never()).save(any());
        verify(slices, never()).delete(any(MicroTeachingSlice.class));
    }

    @Test void stageQueriesUseOnlyReadableCourseIdsForEveryRole() throws Exception {
        for (var role : List.of(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR)) {
            account(role, "SE");
            var ids = role == RoleEnum.TEACHER ? List.of(1L, 3L) : List.of(1L);
            when(slices.findByBopppsStageAndCourse_IdIn("B", ids)).thenReturn(List.of(ownSlice));
            mvc.perform(get("/api/v1/resources/micro-slices/stage/B").header("Authorization", "Bearer scope-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            verify(slices, atLeastOnce()).findByBopppsStageAndCourse_IdIn("B", ids);
        }
        verify(slices, never()).findByBopppsStage(anyString());
    }

    @Test void crossDepartmentCollaboratorCanMaintainCourseButDirectorCannot() throws Exception {
        account(RoleEnum.TEACHER, "SE");
        mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload(3))).andExpect(status().isOk());
        account(RoleEnum.DIRECTOR, "SE;CS");
        mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload(3))).andExpect(status().isForbidden());
        verify(slices, times(1)).save(any());
    }

    @Test void revokedAuthorizationAndMissingInputsNeverWrite() throws Exception {
        account(RoleEnum.SUPERVISOR, "");
        mvc.perform(get("/api/v1/resources/micro-slices/course/1").header("Authorization", "Bearer scope-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/resources/micro-slices/stage/B").header("Authorization", "Bearer scope-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        account(RoleEnum.TEACHER, "SE");
        mvc.perform(post("/api/v1/resources/micro-slices").header("Authorization", "Bearer scope-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/resources/micro-slices/999").header("Authorization", "Bearer scope-token")).andExpect(status().isBadRequest());
        verify(slices, never()).save(any());
        verify(slices, never()).delete(any(MicroTeachingSlice.class));
    }
}
