package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.CourseSyllabus;
import com.classroom.ai.modules.course.entity.GraduationIndicator;
import com.classroom.ai.modules.course.service.SyllabusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/syllabus")
@RequiredArgsConstructor
@CrossOrigin
public class SyllabusController {

    private final SyllabusService syllabusService;

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<CourseSyllabus>> getSyllabusByCourseId(@PathVariable Long courseId) {
        return ApiResponse.success(syllabusService.getSyllabusByCourseId(courseId));
    }

    @GetMapping("/course/{courseId}/latest")
    public ApiResponse<CourseSyllabus> getLatestSyllabus(@PathVariable Long courseId) {
        return ApiResponse.success(syllabusService.getLatestSyllabus(courseId));
    }

    @GetMapping("/course/{courseId}/indicators")
    public ApiResponse<List<GraduationIndicator>> getIndicators(@PathVariable Long courseId) {
        return ApiResponse.success(syllabusService.getIndicatorsByCourseId(courseId));
    }

    @PostMapping
    public ApiResponse<CourseSyllabus> saveSyllabus(@RequestBody SyllabusDTO dto) {
        return ApiResponse.success("教学大纲与指标点矩阵保存成功", syllabusService.saveSyllabus(dto));
    }

    @PostMapping("/{id}/lock")
    public ApiResponse<CourseSyllabus> lockSyllabus(@PathVariable Long id, @RequestParam String lockedBy) {
        return ApiResponse.success("大纲版本已审查锁定", syllabusService.lockSyllabus(id, lockedBy));
    }
}
