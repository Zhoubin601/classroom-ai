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
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository;

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
        Course course = courseService.getCourseById(id);
        // 督导越权防范
        if (com.classroom.ai.modules.auth.context.AuthContext.isAuthenticated()) {
            com.classroom.ai.modules.auth.vo.UserVO user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
            if (user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR) {
                if (user.getAuthorizedMajors() != null && course.getMajorCode() != null) {
                    java.util.Set<String> authMajors = java.util.Arrays.stream(user.getAuthorizedMajors().split(";"))
                            .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet());
                    if (!authMajors.contains(course.getMajorCode().toUpperCase())) {
                        throw new com.classroom.ai.common.exception.ForbiddenException("无权直接访问未授权专业课程档案 (ID: " + id + ")");
                    }
                }
            }
        }
        return ApiResponse.success(course);
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
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String majorCode) {
        String cleanTerm = (term != null && !term.trim().isEmpty()) ? term.trim() : null;
        String cleanTeacher = (teacher != null && !teacher.trim().isEmpty()) ? teacher.trim() : null;
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanMajor = (majorCode != null && !majorCode.trim().isEmpty()) ? majorCode.trim().toUpperCase() : null;

        // 督导权限校验：若传入 majorCode 但不在授权列表则抛出 403
        if (com.classroom.ai.modules.auth.context.AuthContext.isAuthenticated()) {
            com.classroom.ai.modules.auth.vo.UserVO user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
            if (user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR) {
                java.util.Set<String> authMajors = user.getAuthorizedMajors() != null
                        ? java.util.Arrays.stream(user.getAuthorizedMajors().split(";"))
                                .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet())
                        : java.util.Collections.emptySet();

                if (cleanMajor != null && !authMajors.contains(cleanMajor)) {
                    throw new com.classroom.ai.common.exception.ForbiddenException("无权检索未授权专业 (" + cleanMajor + ") 的课程");
                }
            }
        }

        List<CourseOffering> list = courseOfferingRepository.searchOfferingsWithMajor(cleanTerm, cleanTeacher, cleanKeyword, cleanMajor);

        // 督导未指定专业时，后端强制叠加仅返回已授权专业列表
        if (com.classroom.ai.modules.auth.context.AuthContext.isAuthenticated()
                && com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser().getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR) {
            com.classroom.ai.modules.auth.vo.UserVO user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
            if (user.getAuthorizedMajors() != null && !user.getAuthorizedMajors().trim().isEmpty()) {
                java.util.Set<String> authMajors = java.util.Arrays.stream(user.getAuthorizedMajors().split(";"))
                        .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet());
                list = list.stream()
                        .filter(o -> {
                            String mCode = o.getMajorCode();
                            if ((mCode == null || mCode.trim().isEmpty()) && o.getCourse() != null) {
                                mCode = o.getCourse().getMajorCode();
                            }
                            if ((mCode == null || mCode.trim().isEmpty()) && o.getClassName() != null) {
                                if (o.getClassName().contains("软件工程")) mCode = "SE";
                                else if (o.getClassName().contains("计算机")) mCode = "CS";
                                else if (o.getClassName().contains("人工智能")) mCode = "AI";
                                else if (o.getClassName().contains("信息安全")) mCode = "SEC";
                                else if (o.getClassName().contains("数据科学")) mCode = "DS";
                            }
                            return mCode == null || authMajors.contains(mCode.toUpperCase());
                        })
                        .collect(java.util.stream.Collectors.toList());
            }
        }

        return ApiResponse.success(list);
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
