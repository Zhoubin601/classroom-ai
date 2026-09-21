package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.CourseDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import com.classroom.ai.modules.course.service.CourseImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@CrossOrigin
public class CourseController {

    private final CourseService courseService;
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository;
    private final com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository;
    private final CourseAuthorizationService authorizationService;
    private final CourseImportService courseImportService;

    @org.springframework.beans.factory.annotation.Autowired
    public CourseController(CourseService courseService,
                            com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository,
                            com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository,
                            CourseAuthorizationService authorizationService,
                            CourseImportService courseImportService) {
        this.courseService = courseService;
        this.courseOfferingRepository = courseOfferingRepository;
        this.offeringTeacherRepository = offeringTeacherRepository;
        this.authorizationService = authorizationService;
        this.courseImportService = courseImportService;
    }

    public CourseController(CourseService courseService,
                            com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository,
                            com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository,
                            CourseAuthorizationService authorizationService) {
        this(courseService, courseOfferingRepository, offeringTeacherRepository, authorizationService, null);
    }

    public CourseController(CourseService courseService,
                            com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository) {
        this(courseService, courseOfferingRepository, null, null, null);
    }

    /**
     * 导出标准化课程档案 CSV (支持主任管辖范围过滤与标准闭环重新导入)
     */
    @GetMapping("/export")
    public void exportCourses(HttpServletResponse response) throws IOException {
        List<Course> list = courseService.getAllCourses();
        if (authorizationService != null) {
            list = authorizationService.filterCourses(list);
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"courses_export.csv\"");
        if (courseImportService != null) {
            courseImportService.exportCourses(list, response.getOutputStream());
        }
    }

    @GetMapping
    public ApiResponse<List<Course>> getAllCourses(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String courseType) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanCourseType = (courseType != null && !courseType.trim().isEmpty()) ? courseType.trim() : null;
        List<Course> list;
        if (cleanKeyword != null || cleanCourseType != null) {
            list = courseService.searchCourses(cleanKeyword, cleanCourseType);
        } else {
            list = courseService.getAllCourses();
        }
        // 统一按当前登录角色权限执行安全过滤
        return ApiResponse.success(authorizationService != null ? authorizationService.filterCourses(list) : list);
    }

    @GetMapping("/{id}")
    public ApiResponse<Course> getCourseById(@PathVariable Long id) {
        Course course = courseService.getCourseById(id);
        // 核心防越权：通过统一授权服务校验课程读权限，杜绝直接按 ID 越权探测
        if (authorizationService != null) {
            authorizationService.validateCourseRead(course);
        }
        return ApiResponse.success(course);
    }

    @PostMapping
    public ApiResponse<Course> saveCourse(@RequestBody CourseDTO dto) {
        // 服务层在同一事务中锁定旧档案并校验原归属与新归属，避免在锁定前预加载旧快照。
        return ApiResponse.success("课程保存成功", courseService.saveCourse(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCourse(@PathVariable Long id) {
        if (authorizationService != null) {
            authorizationService.validateCourseWrite(id);
        }
        courseService.deleteCourse(id);
        return ApiResponse.success("课程删除成功", null);
    }

    /** Legacy in-process compatibility; HTTP queries use CourseOfferingQueryController. */
    @Deprecated
    public ApiResponse<List<CourseOffering>> getOfferings(@RequestParam(required = false) String term,
                                                          @RequestParam(required = false) String teacher,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String majorCode) {
        String cleanTerm = (term != null && !term.trim().isEmpty()) ? term.trim() : null;
        String cleanTeacher = (teacher != null && !teacher.trim().isEmpty()) ? teacher.trim() : null;
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanMajor = (majorCode != null && !majorCode.trim().isEmpty()) ? majorCode.trim().toUpperCase() : null;

        // 督导权限显式前置校验：若指定检索专业但未授权则直接 403
        if (com.classroom.ai.modules.auth.context.AuthContext.isAuthenticated()) {
            com.classroom.ai.modules.auth.vo.UserVO user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
            if (user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR && cleanMajor != null) {
                java.util.Set<String> authMajors = user.getAuthorizedMajors() != null
                        ? java.util.Arrays.stream(user.getAuthorizedMajors().split(";"))
                                .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet())
                        : java.util.Collections.emptySet();
                if (!authMajors.contains(cleanMajor)) {
                    throw new com.classroom.ai.common.exception.ForbiddenException("无权检索未授权专业 (" + cleanMajor + ") 的课程");
                }
            }
        }

        List<CourseOffering> list = courseOfferingRepository.searchOfferingsWithMajor(cleanTerm, cleanTeacher, cleanKeyword, cleanMajor);
        if (offeringTeacherRepository != null) list.forEach(o -> o.setTeachers(offeringTeacherRepository.findByOfferingId(o.getId())));
        // 统一基于服务端当前身份与授权进行安全过滤 (主任管辖教研室、教师本人关联、督导授权专业)
        return ApiResponse.success(authorizationService != null ? authorizationService.filterOfferings(list) : list);
    }

    @GetMapping("/offerings/{id}/students")
    public ApiResponse<java.util.Map<String, Object>> getOfferingStudents(@PathVariable Long id) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次 (ID: " + id + ")"));
        if (authorizationService != null) {
            authorizationService.validateOfferingRead(offering);
        }

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
