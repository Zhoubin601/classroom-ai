package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.CourseDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@CrossOrigin
public class CourseController {

    private final CourseService courseService;
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository;
    private final com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository;
    private final CourseAuthorizationService authorizationService;

    @org.springframework.beans.factory.annotation.Autowired
    public CourseController(CourseService courseService,
                            com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository,
                            com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository offeringTeacherRepository,
                            CourseAuthorizationService authorizationService) {
        this.courseService = courseService;
        this.courseOfferingRepository = courseOfferingRepository;
        this.offeringTeacherRepository = offeringTeacherRepository;
        this.authorizationService = authorizationService;
    }

    public CourseController(CourseService courseService,
                            com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository) {
        this(courseService, courseOfferingRepository, null, null);
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
        if (dto.getId() != null && authorizationService != null) {
            authorizationService.validateCourseWrite(dto.getId());
        }
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

    @GetMapping("/offerings")
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
        // 统一基于服务端当前身份与授权进行安全过滤 (主任管辖教研室、教师本人关联、督导授权专业)
        return ApiResponse.success(authorizationService != null ? authorizationService.filterOfferings(list) : list);
    }

    /**
     * 建立最小班次维护能力：新增开课班次
     */
    @PostMapping("/offerings")
    public ApiResponse<CourseOffering> createOffering(@RequestBody CourseOffering offering) {
        if (offering.getCourse() == null || offering.getCourse().getId() == null) {
            throw new IllegalArgumentException("关联课程档案不能为空");
        }
        // 仅主任与该课程任课教师可开课
        if (authorizationService != null) {
            authorizationService.validateCourseWrite(offering.getCourse().getId());
        }
        
        Course course = courseService.getCourseById(offering.getCourse().getId());
        offering.setCourse(course);
        if (offering.getMajorCode() == null || offering.getMajorCode().isBlank()) {
            offering.setMajorCode(course.getMajorCode());
        }
        if (offering.getStudentCount() == null) {
            offering.setStudentCount(0);
        }
        CourseOffering saved = courseOfferingRepository.save(offering);

        // 自动同步建立主讲教师关联
        if (saved.getTeacherCode() != null || saved.getTeacherName() != null) {
            com.classroom.ai.modules.course.entity.CourseOfferingTeacher cot =
                    com.classroom.ai.modules.course.entity.CourseOfferingTeacher.builder()
                            .offeringId(saved.getId())
                            .teacherCode(saved.getTeacherCode())
                            .teacherName(saved.getTeacherName())
                            .roleInOffering("PRIMARY")
                            .build();
            if (offeringTeacherRepository != null) {
                offeringTeacherRepository.save(cot);
            }
        }
        return ApiResponse.success("开课班次创建成功", saved);
    }

    /**
     * 建立最小班次维护能力：更新开课班次
     */
    @PutMapping("/offerings/{id}")
    public ApiResponse<CourseOffering> updateOffering(@PathVariable Long id, @RequestBody CourseOffering updateDTO) {
        CourseOffering existing = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到待修改的开课班次 (ID: " + id + ")"));
        if (authorizationService != null) {
            authorizationService.validateOfferingWrite(existing);
        }

        if (updateDTO.getClassName() != null) existing.setClassName(updateDTO.getClassName());
        if (updateDTO.getAcademicTerm() != null) existing.setAcademicTerm(updateDTO.getAcademicTerm());
        if (updateDTO.getTeacherName() != null) existing.setTeacherName(updateDTO.getTeacherName());
        if (updateDTO.getTeacherCode() != null) existing.setTeacherCode(updateDTO.getTeacherCode());
        if (updateDTO.getStudentCount() != null) existing.setStudentCount(updateDTO.getStudentCount());
        if (updateDTO.getStatus() != null) existing.setStatus(updateDTO.getStatus());
        if (updateDTO.getMajorCode() != null) existing.setMajorCode(updateDTO.getMajorCode());

        CourseOffering saved = courseOfferingRepository.save(existing);
        return ApiResponse.success("开课班次更新成功", saved);
    }

    /**
     * 建立最小班次维护能力：删除开课班次
     */
    @DeleteMapping("/offerings/{id}")
    public ApiResponse<Void> deleteOffering(@PathVariable Long id) {
        CourseOffering existing = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到待删除的开课班次 (ID: " + id + ")"));
        if (authorizationService != null) {
            authorizationService.validateOfferingWrite(existing);
        }
        if (offeringTeacherRepository != null) {
            offeringTeacherRepository.deleteByOfferingId(id);
        }
        courseOfferingRepository.deleteById(id);
        return ApiResponse.success("开课班次删除成功", null);
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
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次 (ID: " + id + ")"));
        if (authorizationService != null) {
            authorizationService.validateOfferingWrite(offering);
        }

        CourseOffering updated = courseService.addStudentsToOffering(id, studentIds);
        return ApiResponse.success("成功将学生加入班级，班级现有人数: " + updated.getStudentCount(), updated);
    }

    @PostMapping("/offerings/{id}/students/remove/{studentId}")
    public ApiResponse<CourseOffering> removeStudentFromOffering(@PathVariable Long id, @PathVariable String studentId) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次 (ID: " + id + ")"));
        if (authorizationService != null) {
            authorizationService.validateOfferingWrite(offering);
        }

        CourseOffering updated = courseService.removeStudentFromOffering(id, studentId);
        return ApiResponse.success("成功将学生移出班级，班级现有人数: " + updated.getStudentCount(), updated);
    }
}
