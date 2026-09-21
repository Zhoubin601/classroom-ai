package com.classroom.ai.modules.course;

import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 包含实际 SecurityFilterChain/JWT filter/课程授权服务，避免只 mock 一个 403。 */
@WebMvcTest({CourseImportController.class, CourseContentController.class, CourseController.class, CourseOfferingController.class, CourseScheduleController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class})
class CourseHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean CourseContentRevisionRepository revisions;
    @MockBean CourseImportService imports;
    @MockBean CourseService archives;
    @MockBean CourseOfferingManagementService management;
    @MockBean CourseScheduleService schedules;

    void login(RoleEnum role) {
        when(tokens.validateToken("synthetic-token")).thenReturn(true);
        when(tokens.parseUserFromToken("synthetic-token")).thenReturn(UserVO.builder().username("synthetic").build());
        when(accounts.findByUsername("synthetic")).thenReturn(Optional.of(UserAccount.builder()
            .username("synthetic").role(role).realName("Same Name").department("A").teacherCode("T1").authorizedMajors("SE").build()));
    }
    @Test void anonymousImportAndContentReadReturn401() throws Exception {
        mvc.perform(post("/api/v1/courses/import/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"batchId\":\"test\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/courses/1/content/published")).andExpect(status().isUnauthorized());
        verifyNoInteractions(imports, courses, revisions);
    }
    @Test void supervisorCannotPreviewConfirmOrCreateCourse() throws Exception {
        login(RoleEnum.SUPERVISOR);
        mvc.perform(multipart("/api/v1/courses/import/preview").file(new MockMultipartFile("file", new byte[]{1}))
            .header("Authorization", "Bearer synthetic-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/courses/import/confirm").header("Authorization", "Bearer synthetic-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"batchId\":\"test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/courses").header("Authorization", "Bearer synthetic-token")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(imports, archives);
    }
    @Test void unauthorizedPublishedContentReturns403() throws Exception {
        login(RoleEnum.SUPERVISOR);
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).majorCode("CS").build()));
        mvc.perform(get("/api/v1/courses/1/content/published").header("Authorization", "Bearer synthetic-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(revisions);
    }
    @Test void authorizedPublishedContentRemainsReadable() throws Exception {
        login(RoleEnum.SUPERVISOR);
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).majorCode("SE").build()));
        when(revisions.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
            .thenReturn(Optional.of(CourseContentRevision.builder().id(5L).courseId(1L).status("PUBLISHED").description("Official").publishVersion(1).build()));
        mvc.perform(get("/api/v1/courses/1/content/published").header("Authorization", "Bearer synthetic-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value("Official"));
    }
    @Test void teacherWithSameNameButNoAssociationCannotPublish() throws Exception {
        login(RoleEnum.TEACHER);
        Course course = Course.builder().id(1L).teacherName("Same Name").build();
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
        mvc.perform(post("/api/v1/courses/1/content/publish").header("Authorization", "Bearer synthetic-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\",\"objectives\":\"x\",\"assessmentMethod\":\"x\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(revisions);
    }
    @Test void supervisorCannotReadOrCreateUnpublishedDraft() throws Exception {
        login(RoleEnum.SUPERVISOR);
        mvc.perform(get("/api/v1/courses/1/content/draft").header("Authorization", "Bearer synthetic-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(courses, revisions);
    }
    @Test void teacherCannotChangeOfferingsRosterOrSchedules() throws Exception {
        login(RoleEnum.TEACHER);
        for (String path : List.of("/api/v1/courses/offerings", "/api/v1/courses/offerings/1/students/add", "/api/v1/schedules")) {
            mvc.perform(post(path).header("Authorization", "Bearer synthetic-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/v1/courses/offerings/1").header("Authorization", "Bearer synthetic-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/schedules/1").header("Authorization", "Bearer synthetic-token")).andExpect(status().isForbidden());
        verifyNoInteractions(management, schedules, archives);
    }
}
