package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.CourseDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@CrossOrigin
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ApiResponse<List<Course>> getAllCourses(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String courseType) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanCourseType = (courseType != null && !courseType.trim().isEmpty()) ? courseType.trim() : null;
        if (cleanKeyword != null || cleanCourseType != null) {
            return ApiResponse.success(courseService.searchCourses(cleanKeyword, cleanCourseType));
        }
        return ApiResponse.success(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ApiResponse<Course> getCourseById(@PathVariable Long id) {
        return ApiResponse.success(courseService.getCourseById(id));
    }

    @PostMapping
    public ApiResponse<Course> saveCourse(@RequestBody CourseDTO dto) {
        return ApiResponse.success("课程保存成功", courseService.saveCourse(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ApiResponse.success("课程删除成功", null);
    }

    @GetMapping("/offerings")
    public ApiResponse<List<CourseOffering>> getOfferings(@RequestParam(required = false) String term,
                                                          @RequestParam(required = false) String teacher,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResponse.success(courseService.searchOfferings(term, teacher, keyword));
    }

    @GetMapping("/offerings/{id}/students")
    public ApiResponse<java.util.Map<String, Object>> getOfferingStudents(@PathVariable Long id) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("enrolled", courseService.getOfferingStudents(id));
        map.put("available", courseService.getAvailableStudentsForOffering(id));
        return ApiResponse.success(map);
    }

    @PostMapping("/offerings/{id}/students/add")
    public ApiResponse<CourseOffering> addStudentsToOffering(@PathVariable Long id, @RequestBody java.util.List<String> studentIds) {
        CourseOffering updated = courseService.addStudentsToOffering(id, studentIds);
        return ApiResponse.success("成功将学生加入班级，班级现有人数: " + updated.getStudentCount(), updated);
    }

    @PostMapping("/offerings/{id}/students/remove/{studentId}")
    public ApiResponse<CourseOffering> removeStudentFromOffering(@PathVariable Long id, @PathVariable String studentId) {
        CourseOffering updated = courseService.removeStudentFromOffering(id, studentId);
        return ApiResponse.success("成功将学生移出班级，班级现有人数: " + updated.getStudentCount(), updated);
    }
}
