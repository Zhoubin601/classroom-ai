package com.classroom.ai.modules.resource;

import com.classroom.ai.modules.resource.controller.MicroTeachingVideoController;
import com.classroom.ai.modules.resource.service.MicroTeachingVideoService;
import com.classroom.ai.modules.resource.repository.MicroTeachingSliceRepository;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MicroTeachingVideoController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,CourseAuthorizationService.class,MicroTeachingVideoService.class,com.classroom.ai.modules.sprint3.VideoAssetService.class})
class MicroVideoHttpTest {
    @Autowired MockMvc mvc;
    @Autowired MicroTeachingVideoService videos;
    @org.springframework.boot.test.mock.mockito.SpyBean com.classroom.ai.modules.sprint3.VideoAssetService assets;
    @MockBean jakarta.persistence.EntityManager em;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    @MockBean CourseRepository courses;
    @MockBean CourseOfferingRepository offerings;
    @MockBean CourseOfferingTeacherRepository teachers;
    @MockBean MicroTeachingSliceRepository slices;
    @TempDir Path directory;
    Course own=Course.builder().id(2L).department("A").majorCode("SE").build();
    Course foreign=Course.builder().id(10L).department("B").majorCode("AI").build();
    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(assets,"uploadDir",directory.resolve("faces").toString());
        doReturn(2000L).when(assets).probe(any(Path.class));
        java.util.concurrent.atomic.AtomicReference<com.classroom.ai.modules.sprint3.VideoAsset> stored=new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(i->{if(i.getArgument(0) instanceof com.classroom.ai.modules.sprint3.VideoAsset a){a.setId(7L);stored.set(a);}return null;}).when(em).persist(any());
        when(em.find(eq(com.classroom.ai.modules.sprint3.VideoAsset.class),eq(7L))).thenAnswer(i->stored.get());
        when(em.find(eq(MicroTeachingSlice.class),eq(8L))).thenReturn(MicroTeachingSlice.builder().id(8L).course(foreign).build());
        ReflectionTestUtils.setField(videos,"uploadDir",directory.resolve("faces").toString());
        when(tokens.validateToken("test-token")).thenReturn(true);
        when(tokens.parseUserFromToken("test-token")).thenReturn(UserVO.builder().id(900L).username("test").role(RoleEnum.DIRECTOR).build());
        account(RoleEnum.DIRECTOR);
        when(courses.findById(2L)).thenReturn(Optional.of(own));when(courses.findById(10L)).thenReturn(Optional.of(foreign));
        when(slices.save(any())).thenAnswer(i->{var slice=(MicroTeachingSlice)i.getArgument(0);slice.setId(7L);return slice;});
        when(slices.findById(8L)).thenReturn(Optional.of(MicroTeachingSlice.builder().id(8L).course(foreign).sliceUrl("/uploads/micro/00000000-0000-0000-0000-000000000000.webm").build()));
    }
    void account(RoleEnum role){when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("test").role(role).department("A").teacherCode("T1").authorizedMajors("SE").build()));}
    MockMultipartFile video(){return new MockMultipartFile("file","sample.webm","video/webm",new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,0,0,0,0});}
    @Test void supervisorCannotUploadAndForeignCourseCannotBeRead() throws Exception {
        account(RoleEnum.SUPERVISOR);
        mvc.perform(multipart("/api/v1/resources/micro-slices/upload").file(video()).param("courseId","2").param("title","合成视频").param("stage","P2").param("durationSeconds","2").param("sourceType","PLATFORM_UPLOAD").param("sourceDescription","Platform-Upload").param("authorizationDeclaration","自主合成授权样例").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        for(var role:List.of(RoleEnum.DIRECTOR,RoleEnum.TEACHER,RoleEnum.SUPERVISOR)){
            account(role);mvc.perform(get("/api/v1/resources/micro-slices/8/video").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        }
        verify(slices,never()).save(any());
    }
    @Test void managedFilePathCannotBypassLoginAndCourseAuthorization() throws Exception {
        mvc.perform(get("/api/v1/resources/micro-slices/8/video")).andExpect(status().isUnauthorized());
        mvc.perform(get("/uploads/micro/00000000-0000-0000-0000-000000000000.webm").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(slices);
    }
    @Test void validUploadUsesPrivateGeneratedPathAndDisguisedFilesAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/resources/micro-slices/upload").file(video()).param("courseId","2").param("title","合成视频").param("stage","P2").param("durationSeconds","2").param("sourceType","PLATFORM_UPLOAD").param("sourceDescription","Platform-Upload").param("authorizationDeclaration","自主合成授权样例").header("Authorization","Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceAgent").value("Platform-Upload"));
        mvc.perform(multipart("/api/v1/resources/micro-slices/upload").file(new MockMultipartFile("file","bad.webm","video/webm","fake".getBytes())).param("courseId","2").param("title","合成视频").param("stage","P2").param("durationSeconds","2").param("sourceType","PLATFORM_UPLOAD").param("sourceDescription","Platform-Upload").param("authorizationDeclaration","自主合成授权样例").header("Authorization","Bearer test-token")).andExpect(status().isBadRequest());
    }
}
