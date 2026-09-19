package com.classroom.ai.modules.resource.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.service.CourseResourceService;
import com.classroom.ai.config.UploadPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@CrossOrigin
public class CourseResourceController {

    @Value("${classroom.upload-dir:}")
    private String uploadDir;

    private final CourseResourceService resourceService;
    private final com.classroom.ai.modules.course.service.CourseAuthorizationService authorizationService;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadResourceFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }
        if (file.getSize() > 100L * 1024 * 1024) {
            return ApiResponse.error(400, "单文件大小不能超过 100MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "courseware.pptx";
        }

        String ext = "";
        if (originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!List.of(".pptx", ".ppt", ".docx", ".doc", ".pdf").contains(ext)) {
            return ApiResponse.error(400, "不支持的文件格式，仅支持 PPT/PPTX、Word/DOCX、PDF 格式");
        }

        try {
            Path targetDir = UploadPaths.resolveResources(uploadDir);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String safeName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            File dest = targetDir.resolve(safeName).toFile();
            file.transferTo(dest);

            long bytes = file.getSize();
            String formattedSize;
            if (bytes >= 1024 * 1024) {
                formattedSize = String.format(java.util.Locale.ROOT, "%.1f MB", (double) bytes / (1024 * 1024));
            } else {
                formattedSize = String.format(java.util.Locale.ROOT, "%.0f KB", Math.max(1.0, (double) bytes / 1024));
            }

            String fileType = ext.replace(".", "").toUpperCase();
            if ("DOC".equals(fileType)) fileType = "DOCX";
            if ("PPT".equals(fileType)) fileType = "PPTX";

            Map<String, Object> result = new HashMap<>();
            result.put("fileUrl", "/uploads/resources/" + safeName);
            result.put("fileName", originalFilename);
            result.put("fileType", fileType);
            result.put("fileSize", formattedSize);
            result.put("fileSizeBytes", bytes);

            return ApiResponse.success("课件文件上传成功", result);
        } catch (IOException e) {
            return ApiResponse.error(500, "保存课件文件失败: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<CourseResource>> searchResources(@RequestParam(required = false) Long courseId,
                                                             @RequestParam(required = false) String tag,
                                                             @RequestParam(required = false) Boolean isPublic,
                                                             @RequestParam(required = false) String keyword) {
        if (courseId != null) {
            authorizationService.validateCourseRead(courseId);
        }
        String cleanTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : null;
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return ApiResponse.success(resourceService.searchResources(courseId, cleanTag, isPublic, cleanKeyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseResource> getResourceById(@PathVariable Long id) {
        CourseResource res = resourceService.getResourceById(id);
        if (res != null && res.getCourse() != null) {
            authorizationService.validateCourseRead(res.getCourse());
        }
        return ApiResponse.success(res);
    }

    @PostMapping
    public ApiResponse<CourseResource> saveResource(@RequestBody CourseResourceDTO dto) {
        if (dto != null && dto.getCourseId() != null) {
            authorizationService.validateCourseWrite(dto.getCourseId());
        }
        try {
            return ApiResponse.success("资源上传挂载成功", resourceService.saveResource(dto));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        resourceService.deleteResource(id);
        return ApiResponse.success("资源删除成功", null);
    }
}
