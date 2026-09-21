package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.CourseOfferingDTO;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.service.CourseOfferingManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/courses/offerings")
@RequiredArgsConstructor
public class CourseOfferingController {
    private final CourseOfferingManagementService service;
    @GetMapping("/{id}")
    public ApiResponse<Map<String,Object>> details(@PathVariable Long id) { return ApiResponse.success(service.details(id)); }
    @PostMapping
    public ApiResponse<CourseOffering> create(@RequestBody CourseOfferingDTO dto) { return ApiResponse.success(service.save(null,dto)); }
    @PutMapping("/{id}")
    public ApiResponse<CourseOffering> update(@PathVariable Long id,@RequestBody CourseOfferingDTO dto) { return ApiResponse.success(service.save(id,dto)); }
    @PostMapping("/{id}/archive")
    public ApiResponse<CourseOffering> archive(@PathVariable Long id) { return ApiResponse.success("结课归档成功",service.archive(id)); }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success("班次已删除",null); }
}
