package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.CourseSyllabus;
import com.classroom.ai.modules.course.entity.GraduationIndicator;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.SyllabusService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/syllabus")
@RequiredArgsConstructor
@CrossOrigin
public class SyllabusController {

    private final SyllabusService syllabusService;
    private final CourseAuthorizationService authorizationService;

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<CourseSyllabus>> getSyllabusByCourseId(@PathVariable Long courseId) {
        authorizationService.validateCourseRead(courseId);
        return ApiResponse.success(syllabusService.getSyllabusByCourseId(courseId));
    }

    @GetMapping("/course/{courseId}/latest")
    public ApiResponse<CourseSyllabus> getLatestSyllabus(@PathVariable Long courseId) {
        authorizationService.validateCourseRead(courseId);
        return ApiResponse.success(syllabusService.getLatestSyllabus(courseId));
    }

    @GetMapping("/course/{courseId}/indicators")
    public ApiResponse<List<GraduationIndicator>> getIndicators(@PathVariable Long courseId) {
        authorizationService.validateCourseRead(courseId);
        return ApiResponse.success(syllabusService.getIndicatorsByCourseId(courseId));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @PostMapping
    public ApiResponse<CourseSyllabus> saveSyllabus(@RequestBody SyllabusDTO dto) {
        if (dto.getCourseId() != null) {
            authorizationService.validateCourseWrite(dto.getCourseId());
        }
        return ApiResponse.success("教学大纲与指标点矩阵保存成功", syllabusService.saveSyllabus(dto));
    }

    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{id}/lock")
    public ApiResponse<CourseSyllabus> lockSyllabus(@PathVariable Long id, @RequestParam String lockedBy) {
        return ApiResponse.success("大纲版本已审查锁定", syllabusService.lockSyllabus(id, lockedBy));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @PostMapping("/course/{courseId}/indicators")
    public ApiResponse<GraduationIndicator> addIndicator(@PathVariable Long courseId,
                                                         @RequestBody com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        authorizationService.validateCourseWrite(courseId);
        return ApiResponse.success("指标点新增成功并已同步MySQL", syllabusService.addIndicator(courseId, dto));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @PutMapping("/indicators/{id}")
    public ApiResponse<GraduationIndicator> updateIndicator(@PathVariable Long id,
                                                            @RequestBody com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        return ApiResponse.success("指标点修改成功并已更新MySQL", syllabusService.updateIndicator(id, dto));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @DeleteMapping("/indicators/{id}")
    public ApiResponse<Void> deleteIndicator(@PathVariable Long id) {
        syllabusService.deleteIndicator(id);
        return ApiResponse.success("指标点已从MySQL中移除", null);
    }
}
