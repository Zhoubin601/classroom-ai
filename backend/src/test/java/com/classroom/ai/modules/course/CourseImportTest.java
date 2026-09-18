package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.modules.course.controller.CourseImportController;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CourseImportTest {

    private MockMvc mockMvc;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MajorRepository majorRepository;

    @InjectMocks
    private CourseImportController importController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(importController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("US-01 模板下载：提供带 BOM 的 UTF-8 标准 CSV 模板")
    void testDownloadTemplate() throws Exception {
        mockMvc.perform(get("/api/v1/courses/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"course_import_template.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"));
    }

    @Test
    @DisplayName("US-01 导入预览：合法 CSV 数据解析成功且错误数为 0")
    void testPreviewImport_Success() throws Exception {
        when(majorRepository.findAll()).thenReturn(List.of(
                Major.builder().majorCode("SE").majorName("软件工程").build()
        ));
        when(courseRepository.findByCourseCode(any())).thenReturn(Optional.empty());

        String csvContent = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                "CS9001,高级软件工程,软件工程教研室,SE,3.0,48,36,12,专业选修课,CS3001,深入讲解软件工程生命周期。\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.validRows[0].courseCode").value("CS9001"));
    }

    @Test
    @DisplayName("US-01 导入校验：学时矛盾与未知专业编码时返回详细错误清单")
    void testPreviewImport_ValidationErrors() throws Exception {
        when(majorRepository.findAll()).thenReturn(List.of(
                Major.builder().majorCode("SE").majorName("软件工程").build()
        ));

        // 理论(30)+实验(10) != 总学时(48)；专业 UNKNOWN 不存在
        String csvContent = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                "CS9002,测试课程,软件工程教研室,UNKNOWN,3.0,48,30,10,专业选修课,,简介内容\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").isNotEmpty())
                .andExpect(jsonPath("$.data.errors[0].reason").isNotEmpty());
    }
}
