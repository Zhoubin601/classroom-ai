package com.classroom.ai.modules.resource.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.service.CourseResourceService;
import com.classroom.ai.config.UploadPaths;
import com.classroom.ai.modules.resource.service.ResourceAccessService;
import com.classroom.ai.modules.resource.service.ResourceFileService;
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
import org.springframework.http.*;
import org.springframework.core.io.*;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@CrossOrigin
public class CourseResourceController {

    @Value("${classroom.upload-dir:}")
    private String uploadDir;

    @Value("${classroom.resource.max-file-bytes:104857600}")
    private long maxFileBytes;
    @Value("${classroom.resource.allowed-extensions:ppt,pptx,doc,docx,pdf}")
    private String allowedExtensions;

    private final CourseResourceService resourceService;
    private final com.classroom.ai.modules.course.service.CourseAuthorizationService authorizationService;
    private final ResourceAccessService accessService;
    private final ResourceFileService fileService;

    @PostMapping("/upload")
    public ApiResponse<CourseResource> uploadResourceFile(@RequestParam("file") MultipartFile file,
            @RequestParam Long courseId, @RequestParam String chapter,
            @RequestParam(required = false) String resourceName,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "false") Boolean isPublic) {
        accessService.requireCourseWrite(courseId);
        if (chapter == null || chapter.isBlank()) return ApiResponse.error(400, "请选择所属章节");
        if (tags != null && !tags.isBlank() && java.util.Arrays.stream(tags.split(","))
                .map(String::trim).anyMatch(t -> !List.of("理论", "实验", "讨论", "研讨").contains(t)))
            return ApiResponse.error(400, "环节标签仅支持理论、实验、讨论、研讨");
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }
        if (file.getSize() > maxFileBytes) {
            return ApiResponse.error(400, "单文件大小不能超过 " + (maxFileBytes / 1024 / 1024) + "MB 限制");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "courseware.pptx";
        }

        String ext = "";
        if (originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!java.util.Arrays.asList(allowedExtensions.toLowerCase().split(",")).contains(ext.replace(".", ""))) {
            return ApiResponse.error(400, "不支持的文件格式，允许: " + allowedExtensions);
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

            try {
                var user = authorizationService.requireCurrentUser();
                CourseResourceDTO dto = CourseResourceDTO.builder()
                        .courseId(courseId).chapter(chapter.trim())
                        .resourceName(resourceName == null || resourceName.isBlank() ? originalFilename : resourceName.trim())
                        .fileType(fileType).fileUrl("/uploads/resources/" + safeName)
                        .fileSize(formattedSize).fileSizeBytes(bytes)
                        .tags(tags == null || tags.isBlank() ? List.of() : java.util.Arrays.stream(tags.split(",")).map(String::trim).toList())
                        .isPublic(isPublic).uploaderTeacher(user.getRealName()).build();
                return ApiResponse.success("课件上传成功", resourceService.saveResource(dto));
            } catch (RuntimeException ex) {
                Files.deleteIfExists(dest.toPath());
                throw ex;
            }
        } catch (IOException e) {
            return ApiResponse.error(500, "保存课件文件失败: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<CourseResource>> searchResources(@RequestParam(required = false) Long courseId,
                                                             @RequestParam(required = false) String tag,
                                                             @RequestParam(required = false) Boolean isPublic,
                                                             @RequestParam(required = false) String keyword) {
        authorizationService.requireCurrentUser();
        String cleanTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : null;
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return ApiResponse.success(resourceService.searchResources(courseId, null, isPublic, cleanKeyword).stream()
                .filter(accessService::canRead)
                .filter(r -> cleanTag == null || ("未标注".equals(cleanTag) ? r.getTags().isEmpty() : r.getTags().contains(cleanTag)))
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseResource> getResourceById(@PathVariable Long id) {
        CourseResource res = resourceService.getResourceById(id);
        accessService.requireRead(res);
        return ApiResponse.success(res);
    }

    @PostMapping
    public ApiResponse<CourseResource> saveResource(@RequestBody CourseResourceDTO dto) {
        if (dto == null || dto.getId() == null) return ApiResponse.error(400, "新资源请通过带文件的上传接口创建");
        CourseResource existing = resourceService.getResourceById(dto.getId());
        accessService.requireWrite(existing);
        if (!existing.getCourse().getId().equals(dto.getCourseId())) return ApiResponse.error(400, "不可更改资源所属课程");
        dto.setFileUrl(existing.getFileUrl());
        dto.setFileSize(existing.getFileSize());
        dto.setFileSizeBytes(existing.getFileSizeBytes());
        dto.setFileType(existing.getFileType());
        dto.setUploaderTeacher(existing.getUploaderTeacher());
        try {
            return ApiResponse.success("资源上传挂载成功", resourceService.saveResource(dto));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        accessService.requireWrite(resourceService.getResourceById(id));
        resourceService.deleteResource(id);
        return ApiResponse.success("资源删除成功", null);
    }

    @PostMapping("/{id}/preview-ticket")
    public ApiResponse<Map<String, String>> createPreviewTicket(@PathVariable Long id) {
        String ticket = fileService.createTicket(id, authorizationService.requireCurrentUser());
        return ApiResponse.success(Map.of("url", "/api/v1/resources/preview/" + ticket));
    }

    @GetMapping(value = "/preview/{ticket}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> preview(@PathVariable String ticket) throws IOException {
        byte[] bytes = fileService.preview(ticket);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        Path path = fileService.download(id, authorizationService.requireCurrentUser());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(new FileSystemResource(path));
    }
}
