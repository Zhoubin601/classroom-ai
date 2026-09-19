package com.classroom.ai.modules.course;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.modules.course.controller.CourseImportController;
import com.classroom.ai.modules.course.dto.CourseImportRowDTO;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.service.CourseImportService;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import com.classroom.ai.modules.course.vo.ImportRowError;
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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CourseImportTest {

    private MockMvc mockMvc;

    @Mock
    private CourseImportService courseImportService;

    @InjectMocks
    private CourseImportController importController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(importController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("US-01 模板下载：提供带 BOM 的 UTF-8 标准 CSV 模板")
    void testDownloadTemplate() throws Exception {
        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write("fake-template-data".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(courseImportService).downloadTemplate(any(OutputStream.class));

        mockMvc.perform(get("/api/v1/courses/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"course_import_template.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"));

        verify(courseImportService, times(1)).downloadTemplate(any(OutputStream.class));
    }

    @Test
    @DisplayName("US-01 导入预览：合法 CSV 数据解析成功且错误数为 0")
    void testPreviewImport_Success() throws Exception {
        ImportPreviewVO previewVO = ImportPreviewVO.builder()
                .batchId("batch-123")
                .totalCount(1)
                .successCount(1)
                .errorCount(0)
                .errors(List.of())
                .validRows(List.of(CourseImportRowDTO.builder()
                        .courseCode("CS9001")
                        .courseName("高级软件工程")
                        .build()))
                .build();

        when(courseImportService.previewImport(any())).thenReturn(previewVO);

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.csv", "text/csv", "dummy-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.batchId").value("batch-123"))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.validRows[0].courseCode").value("CS9001"));
    }

    @Test
    @DisplayName("US-01 导入校验：有校验错误时返回错误行明细")
    void testPreviewImport_ValidationErrors() throws Exception {
        ImportPreviewVO previewVO = ImportPreviewVO.builder()
                .batchId("batch-err")
                .totalCount(1)
                .successCount(0)
                .errorCount(1)
                .errors(List.of(new ImportRowError(2, "专业编码", "未知的专业编码: UNKNOWN")))
                .validRows(List.of())
                .build();

        when(courseImportService.previewImport(any())).thenReturn(previewVO);

        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.csv", "text/csv", "dummy-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].field").value("专业编码"))
                .andExpect(jsonPath("$.data.errors[0].reason").value("未知的专业编码: UNKNOWN"));
    }

    @Test
    @DisplayName("US-01 确认导入：调用成功并返回入库统计信息")
    void testConfirmImport_Success() throws Exception {
        when(courseImportService.confirmImport(any(ImportConfirmDTO.class)))
                .thenReturn(Map.of("importedCount", 2, "batchId", "batch-123", "message", "成功批量导入 2 门课程档案"));

        ImportConfirmDTO dto = new ImportConfirmDTO("batch-123");

        mockMvc.perform(post("/api/v1/courses/import/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.batchId").value("batch-123"));
    }
}
