package com.classroom.ai.modules.resource.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.service.CourseResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@CrossOrigin
public class CourseResourceController {

    private final CourseResourceService resourceService;

    @GetMapping
    public ApiResponse<List<CourseResource>> searchResources(@RequestParam(required = false) Long courseId,
                                                             @RequestParam(required = false) String tag,
                                                             @RequestParam(required = false) Boolean isPublic,
                                                             @RequestParam(required = false) String keyword) {
        String cleanTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : null;
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return ApiResponse.success(resourceService.searchResources(courseId, cleanTag, isPublic, cleanKeyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseResource> getResourceById(@PathVariable Long id) {
        return ApiResponse.success(resourceService.getResourceById(id));
    }

    @PostMapping
    public ApiResponse<CourseResource> saveResource(@RequestBody CourseResourceDTO dto) {
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
