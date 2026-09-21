package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.service.CourseOfferingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CourseOfferingQueryController {
    private final CourseOfferingQueryService service;
    @GetMapping("/api/v1/courses/offerings")
    public ApiResponse<List<CourseOffering>> search(@RequestParam(required=false) String term,
        @RequestParam(required=false) String teacher,@RequestParam(required=false) String keyword,
        @RequestParam(required=false) String majorCode,@RequestParam(required=false) Long majorId,@RequestParam(required=false) Long teacherId) {
        return ApiResponse.success(service.search(term,teacher,keyword,majorCode,majorId,teacherId));
    }
}
