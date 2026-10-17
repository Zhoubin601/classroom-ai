package com.classroom.ai.modules.resource;

import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.controller.CourseResourceController;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.service.*;
import com.classroom.ai.modules.sprint3.ResourceVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseResourceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CourseAuthorizationService.class, ResourceUploadPolicy.class})
class ResourceUploadPolicyHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CourseResourceController controller;
    @Autowired ResourceUploadPolicy policy;
    @MockBean CourseResourceService resources;
    @MockBean ResourceAccessService access;
    @MockBean ResourceFileService files;
    @MockBean ResourceVersionService versions;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @TempDir Path directory;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(controller, "uploadDir", directory.resolve("faces").toString());
        ReflectionTestUtils.setField(policy, "configuredMaxFileBytes", 104857600L);
        ReflectionTestUtils.setField(policy, "configuredExtensions", "ppt,pptx,doc,docx,pdf");
        when(tokens.validateToken("synthetic-token")).thenReturn(true);
        when(tokens.parseUserFromToken("synthetic-token")).thenReturn(UserVO.builder().id(900L).username("synthetic").role(RoleEnum.TEACHER).build());
        when(resources.saveResource(any())).thenAnswer(call -> CourseResource.builder().id(1L).fileSizeBytes(call.getArgument(0, com.classroom.ai.modules.resource.dto.CourseResourceDTO.class).getFileSizeBytes()).build());
        account(RoleEnum.TEACHER);
    }

    private void account(RoleEnum role) {
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("synthetic").realName("Synthetic Teacher").role(role).build()));
    }

    @Test void policyRequiresLoginAndKeepsExistingRoleRestrictions() throws Exception {
        mvc.perform(get("/api/v1/resources/upload-policy")).andExpect(status().isUnauthorized());
        for (var role : new RoleEnum[]{RoleEnum.TEACHER, RoleEnum.DIRECTOR, RoleEnum.SUPERVISOR}) {
            account(role);
            mvc.perform(get("/api/v1/resources/upload-policy").header("Authorization", "Bearer synthetic-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.maxFileBytes").value(104857600L))
                    .andExpect(jsonPath("$.data.allowedExtensions[0]").value("ppt"));
        }
        account(RoleEnum.ADMIN);
        mvc.perform(get("/api/v1/resources/upload-policy").header("Authorization", "Bearer synthetic-token")).andExpect(status().isForbidden());
        verifyNoInteractions(access, courses, offerings, teachers, resources, files, versions);
    }

    @Test void advertisedPolicyAndBothUploadModesShareByteAndTypeValidation() throws Exception {
        ReflectionTestUtils.setField(policy, "configuredMaxFileBytes", 1024L);
        ReflectionTestUtils.setField(policy, "configuredExtensions", " PDF , pdf ");
        mvc.perform(get("/api/v1/resources/upload-policy").header("Authorization", "Bearer synthetic-token"))
                .andExpect(jsonPath("$.data.maxFileBytes").value(1024))
                .andExpect(jsonPath("$.data.allowedExtensions.length()").value(1))
                .andExpect(jsonPath("$.data.allowedExtensions[0]").value("pdf"));
        for (String seriesId : new String[]{"", "7"}) {
            var request = multipart("/api/v1/resources/upload").file(new MockMultipartFile("file", "SYNTHETIC.PDF", "application/pdf", new byte[1024]))
                    .param("courseId", "1").param("chapter", "Synthetic").header("Authorization", "Bearer synthetic-token");
            if (!seriesId.isEmpty()) request.param("seriesId", seriesId).param("expectedVersion", "0");
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.fileSizeBytes").value(1024));
            var tooLarge = multipart("/api/v1/resources/upload").file(new MockMultipartFile("file", "sample.pdf", "application/pdf", new byte[1025]))
                    .param("courseId", "1").param("chapter", "Synthetic").header("Authorization", "Bearer synthetic-token");
            if (!seriesId.isEmpty()) tooLarge.param("seriesId", seriesId);
            mvc.perform(tooLarge).andExpect(jsonPath("$.code").value(400));
            var wrongType = multipart("/api/v1/resources/upload").file(new MockMultipartFile("file", "sample.docx", "application/octet-stream", new byte[1]))
                    .param("courseId", "1").param("chapter", "Synthetic").header("Authorization", "Bearer synthetic-token");
            if (!seriesId.isEmpty()) wrongType.param("seriesId", seriesId);
            mvc.perform(wrongType).andExpect(jsonPath("$.code").value(400));
        }
        verify(resources, times(2)).saveResource(any());
    }
}
