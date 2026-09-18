package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.modules.course.controller.CourseContentController;
import com.classroom.ai.modules.course.dto.ContentRevisionDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseContentRevision;
import com.classroom.ai.modules.course.repository.CourseContentRevisionRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CourseContentRevisionTest {

    private MockMvc mockMvc;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseContentRevisionRepository revisionRepository;

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

    @Test
    @DisplayName("US-02 暂存草稿：允许内容不完整，成功保存并返回当前草稿")
    void testSaveDraft_IncompleteAllowed() throws Exception {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(sampleCourse));

        CourseContentRevision existingDraft = CourseContentRevision.builder()
                .id(10L)
                .courseId(1L)
                .version(1)
                .status("DRAFT")
                .description("部分更新简介")
                .build();

        when(revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(existingDraft));
        when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("新修改的简介")
                .version(1)
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
                .version(2)
                .status("DRAFT")
                .build();

        when(revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(existingDraft));

        ContentRevisionDTO dto = ContentRevisionDTO.builder()
                .description("冲突修改")
                .version(1) // 故意提供过期的版本 1
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
}
