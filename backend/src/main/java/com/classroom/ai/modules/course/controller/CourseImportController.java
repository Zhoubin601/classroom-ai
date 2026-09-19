package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.service.CourseImportService;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 课程批量导入控制器 (US-01 规范导入)
 * 职责：
 * 1. 模板下载；
 * 2. 上传文件、全维度校验与数据预览 (第一阶段，不入库)；
 * 3. 确认入库 (第二阶段，整批事务原子提交、操作者防越权、一次性消费)。
 */
@RestController
@RequestMapping("/api/v1/courses/import")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class CourseImportController {

    private final CourseImportService courseImportService;

    /**
     * 下载标准课程导入 CSV 模板 (带 UTF-8 BOM，兼容各类 Excel 及文本编辑器)
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"course_import_template.csv\"");
        courseImportService.downloadTemplate(response.getOutputStream());
    }

    /**
     * 课程 CSV 上传校验与预览 (US-01 两阶段导入之第一阶段：校验与预览，不写入数据库)
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportPreviewVO> previewImport(@RequestParam("file") MultipartFile file) throws Exception {
        ImportPreviewVO vo = courseImportService.previewImport(file);
        return ApiResponse.success("导入解析完成", vo);
    }

    /**
     * 确认导入批次入库 (US-01 两阶段导入之第二阶段：整批事务原子写入)
     */
    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirmImport(@RequestBody ImportConfirmDTO dto) {
        Map<String, Object> res = courseImportService.confirmImport(dto);
        return ApiResponse.success(res);
    }
}
