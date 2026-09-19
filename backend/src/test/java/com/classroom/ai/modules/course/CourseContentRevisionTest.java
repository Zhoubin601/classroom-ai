package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.CourseContentController;
import com.classroom.ai.modules.course.dto.ContentRevisionDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseContentRevision;
import com.classroom.ai.modules.course.repository.CourseContentRevisionRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CourseContentRevisionTest {

    private MockMvc mockMvc;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseContentRevisionRepository revisionRepository;

    @Mock
    private CourseAuthorizationService authService;

    @InjectMocks
    private CourseContentController contentController;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Course sampleCourse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contentController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        sampleCourse = Course.builder()
                .id(1L)
                .courseCode("CS3001")
                .courseName("软件项目管理")
                .description("初始基线简介")
                .assessmentMethod("考试 (100%)")
                .objectives("初始目标")
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("US-02 暂存草稿：允许内容不完整，成功保存并返回当前草稿")
    void testSaveDraft_IncompleteAllowed() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        CourseContentRevision existingDraft = CourseContentRevision.builder()
                .id(10L)
                .courseId(1L)
                .lockVersion(1)
                .version(1)
                .status("DRAFT")
                .description("部分更新简介")
                .build();

        when(revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(existingDraft));
        when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("新修改的简介")
                .lockVersion(1)
                .build();

        mockMvc.perform(put("/api/v1/courses/1/content/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.description").value("新修改的简介"));
    }

    @Test
    @DisplayName("US-02 乐观锁冲突：提交版本与草稿版本不匹配时抛出 409 冲突")
    void testSaveDraft_VersionConflict_Returns409() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        CourseContentRevision existingDraft = CourseContentRevision.builder()
                .id(10L)
                .courseId(1L)
                .lockVersion(2)
                .version(2)
                .status("DRAFT")
                .build();

        when(revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(existingDraft));

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("冲突修改")
                .lockVersion(1) // 故意提供过期的版本 1
                .build();

        mockMvc.perform(put("/api/v1/courses/1/content/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("检测到并发修改冲突")));
    }

    @Test
    @DisplayName("US-02 正式发布校验：三项必填不齐全时拦截并返回 400")
    void testPublish_MissingFields_Returns400() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        // 仅提供 description，缺少考核方式和教学目标
        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("只有简介")
                .build();

        mockMvc.perform(post("/api/v1/courses/1/content/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("正式发布失败")));
    }

    @Test
    @DisplayName("US-02 正式发布权限：非关联任课教师操作时返回 403 禁止")
    void testPublish_ForbiddenForNonRelatedTeacher() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));
        doThrow(new ForbiddenException("只有该课程关联任课教师才能发布课程大纲"))
                .when(authService).validateTeacherCoursePublish(1L);

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("完整简介")
                .assessmentMethod("平时 40% + 期末 60%")
                .objectives("完整教学目标")
                .lockVersion(1)
                .build();

        mockMvc.perform(post("/api/v1/courses/1/content/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("只有该课程关联任课教师才能发布")));
    }

    @Test
    @DisplayName("US-02 正式发布成功：自增发布版本号并记录发布人信息与时间")
    void testPublish_Success_IncrementsPublishVersion() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        UserVO teacherUser = UserVO.builder()
                .username("t_guojun")
                .realName("郭军")
                .teacherCode("T001")
                .role(RoleEnum.TEACHER)
                .build();
        AuthContext.setCurrentUser(teacherUser);

        CourseContentRevision draft = CourseContentRevision.builder()
                .id(10L)
                .courseId(1L)
                .lockVersion(1)
                .version(1)
                .status("DRAFT")
                .build();
        when(revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(draft));

        // 之前已有 v1 发布版本
        CourseContentRevision prevPub = CourseContentRevision.builder()
                .id(5L)
                .courseId(1L)
                .publishVersion(1)
                .status("PUBLISHED")
                .build();
        when(revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
                .thenReturn(Optional.of(prevPub));

        when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("新发布的详细课程简介")
                .assessmentMethod("过程化考核 50% + 期末答辩 50%")
                .objectives("掌握软件工程生命周期与大模型应用")
                .lockVersion(1)
                .build();

        mockMvc.perform(post("/api/v1/courses/1/content/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishVersion").value(2))
                .andExpect(jsonPath("$.data.publisherName").value("郭军"))
                .andExpect(jsonPath("$.data.description").value("新发布的详细课程简介"));

        // 验证 Course 实体被同步更新
        verify(courseRepository).save(argThat(c ->
                "新发布的详细课程简介".equals(c.getDescription()) &&
                "过程化考核 50% + 期末答辩 50%".equals(c.getAssessmentMethod()) &&
                "掌握软件工程生命周期与大模型应用".equals(c.getObjectives())
        ));
    }

    @Test
    @DisplayName("US-02 读者只读：未发布时不伪装已发布，返回未发布提示且数据为空")
    void testGetPublished_UnpublishedReturnsNull() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));
        when(revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/courses/1/content/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("US-02 读者隔离：教师正在编辑新草稿时，读者访问 /published 仍看到上一已发布版本")
    void testGetPublished_DraftEditingDoesNotPollutePublished() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        // 数据库中已有 v1 发布记录
        CourseContentRevision publishedV1 = CourseContentRevision.builder()
                .id(5L)
                .courseId(1L)
                .description("V1 正式简介")
                .assessmentMethod("V1 考核方式")
                .objectives("V1 目标")
                .publishVersion(1)
                .status("PUBLISHED")
                .publisherName("张老师")
                .publishedAt(LocalDateTime.now().minusDays(3))
                .build();
        when(revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
                .thenReturn(Optional.of(publishedV1));

        mockMvc.perform(get("/api/v1/courses/1/content/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.publishVersion").value(1))
                .andExpect(jsonPath("$.data.description").value("V1 正式简介"))
                .andExpect(jsonPath("$.data.publisherName").value("张老师"));
    }
}

