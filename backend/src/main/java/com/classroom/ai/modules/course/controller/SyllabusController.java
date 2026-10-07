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
import com.classroom.ai.modules.course.service.RecommendedIndicatorTemplate;

@RestController
@RequestMapping("/api/v1/syllabus")
@RequiredArgsConstructor
@CrossOrigin
public class SyllabusController {

    private final SyllabusService syllabusService;
    private final CourseAuthorizationService authorizationService;
    private final com.classroom.ai.modules.course.repository.GraduationIndicatorRepository indicatorRepository;
    private final com.classroom.ai.modules.course.repository.TrainingIndicatorRepository trainingRepository;
    private final com.classroom.ai.modules.course.repository.MajorRepository majorRepository;
    private final com.classroom.ai.modules.course.repository.CourseSyllabusRepository syllabusRepository;

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
        dto.setAuthorTeacher(authorizationService.requireCurrentUser().getRealName());
        return ApiResponse.success("教学大纲与指标点矩阵保存成功", syllabusService.saveSyllabus(dto));
    }

    @PreAuthorize("hasRole('DIRECTOR')")
    @PostMapping("/{id}/lock")
    public ApiResponse<CourseSyllabus> lockSyllabus(@PathVariable Long id, @RequestParam String lockedBy) {
        CourseSyllabus target = syllabusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("大纲不存在"));
        authorizationService.validateCourseWrite(target.getCourse());
        return ApiResponse.success("大纲版本已审查锁定", syllabusService.lockSyllabus(id, authorizationService.requireCurrentUser().getRealName()));
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
        authorizationService.validateCourseWrite(indicatorRepository.findById(id).orElseThrow().getCourse());
        return ApiResponse.success("指标点修改成功并已更新MySQL", syllabusService.updateIndicator(id, dto));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @DeleteMapping("/indicators/{id}")
    public ApiResponse<Void> deleteIndicator(@PathVariable Long id) {
        authorizationService.validateCourseWrite(indicatorRepository.findById(id).orElseThrow().getCourse());
        syllabusService.deleteIndicator(id);
        return ApiResponse.success("指标点已从MySQL中移除", null);
    }

    @GetMapping("/plans/{majorCode}/{version}/indicators")
    public ApiResponse<List<com.classroom.ai.modules.course.entity.TrainingIndicator>> getPlanIndicators(
            @PathVariable String majorCode, @PathVariable String version) {
        checkMajorAccess(majorCode, false);
        if (RecommendedIndicatorTemplate.VERSION.equals(version))
            return ApiResponse.success(RecommendedIndicatorTemplate.items(majorCode));
        return ApiResponse.success(trainingRepository.findByMajorCodeAndPlanVersionOrderByIndicatorCode(majorCode, version));
    }

    @GetMapping("/plans/{majorCode}/versions")
    public ApiResponse<List<String>> getPlanVersions(@PathVariable String majorCode) {
        checkMajorAccess(majorCode, false);
        return ApiResponse.success(trainingRepository.findPlanVersions(majorCode));
    }

    @PreAuthorize("hasRole('DIRECTOR')")
    @PutMapping("/plans/{majorCode}/{version}/indicators")
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<List<com.classroom.ai.modules.course.entity.TrainingIndicator>> importPlanIndicators(
            @PathVariable String majorCode, @PathVariable String version,
            @RequestBody List<com.classroom.ai.modules.course.dto.IndicatorDTO> items) {
        checkMajorAccess(majorCode, true);
        if (RecommendedIndicatorTemplate.VERSION.equals(version))
            throw new IllegalArgumentException("推荐模板版本不能用作真实培养方案版本");
        majorRepository.findForUpdate(majorCode).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("培养方案指标目录不能为空");
        var codes = new java.util.HashSet<String>();
        for (var item : items) {
            if (item == null || item.getIndicatorCode() == null || item.getIndicatorCode().isBlank()
                    || item.getRequirementCategory() == null || item.getRequirementCategory().isBlank()
                    || item.getIndicatorDescription() == null || item.getIndicatorDescription().isBlank())
                throw new IllegalArgumentException("指标编号、类别和描述不能为空");
            String code = item.getIndicatorCode().trim();
            if (!codes.add(code)) throw new IllegalArgumentException("指标编号重复：" + code);
        }
        var existing = trainingRepository.findByMajorCodeAndPlanVersionOrderByIndicatorCode(majorCode, version);
        if (syllabusRepository.countPlanReferences(majorCode, version) > 0) {
            boolean unchanged = existing.size() == items.size() && items.stream().allMatch(item ->
                    existing.stream().anyMatch(old -> old.getIndicatorCode().equals(item.getIndicatorCode().trim())
                            && java.util.Objects.equals(old.getRequirementCategory(), item.getRequirementCategory().trim())
                            && java.util.Objects.equals(old.getIndicatorDescription(), item.getIndicatorDescription().trim())));
            if (!unchanged) throw new IllegalStateException("该目录版本已被课程大纲引用，请使用新目录版本，保留历史内容");
            return getPlanIndicators(majorCode, version);
        }
        var byCode = existing.stream().collect(java.util.stream.Collectors.toMap(
                com.classroom.ai.modules.course.entity.TrainingIndicator::getIndicatorCode, item -> item));
        for (var item : items) {
            String code = item.getIndicatorCode().trim();
            var record = byCode.getOrDefault(code, new com.classroom.ai.modules.course.entity.TrainingIndicator());
            record.setMajorCode(majorCode);
            record.setPlanVersion(version);
            record.setIndicatorCode(code);
            record.setRequirementCategory(item.getRequirementCategory().trim());
            record.setIndicatorDescription(item.getIndicatorDescription().trim());
            trainingRepository.save(record);
        }
        trainingRepository.deleteAll(existing.stream().filter(item -> !codes.contains(item.getIndicatorCode())).toList());
        return getPlanIndicators(majorCode, version);
    }

    public record CreateFromPlanRequest(String syllabusVersion, String planVersion) {}

    @PreAuthorize("hasAnyRole('TEACHER', 'DIRECTOR')")
    @PostMapping("/course/{courseId}/from-plan")
    public ApiResponse<CourseSyllabus> createFromPlan(@PathVariable Long courseId, @RequestBody CreateFromPlanRequest request) {
        authorizationService.validateCourseWrite(courseId);
        if (request == null || request.planVersion() == null || request.planVersion().isBlank()
                || request.syllabusVersion() == null || request.syllabusVersion().isBlank())
            throw new IllegalArgumentException("培养方案版本和大纲版本不能为空");
        var course = courseRepository.findById(courseId).orElseThrow();
        var catalog = RecommendedIndicatorTemplate.VERSION.equals(request.planVersion())
                ? RecommendedIndicatorTemplate.items(course.getMajorCode())
                : trainingRepository.findByMajorCodeAndPlanVersionOrderByIndicatorCode(course.getMajorCode(), request.planVersion());
        if (catalog.isEmpty()) throw new IllegalArgumentException("该专业培养方案版本尚未导入指标目录");
        if (syllabusRepository.findByCourseIdAndVersion(courseId, request.syllabusVersion()).isPresent())
            throw new IllegalArgumentException("大纲版本已存在，请输入新版本号");
        var indicators = catalog.stream().map(item -> SyllabusDTO.IndicatorDTO.builder()
                .indicatorCode(item.getIndicatorCode()).requirementCategory(item.getRequirementCategory())
                .indicatorDescription(item.getIndicatorDescription()).supportWeight("M").build()).toList();
        var dto = SyllabusDTO.builder().courseId(courseId).version(request.syllabusVersion()).planVersion(request.planVersion())
                .status("DRAFT").authorTeacher(authorizationService.requireCurrentUser().getRealName()).indicators(indicators).build();
        return ApiResponse.success("已从培养方案创建大纲版本", syllabusService.saveSyllabus(dto));
    }

    private void checkMajorAccess(String majorCode, boolean write) {
        var major = majorRepository.findByMajorCode(majorCode).orElseThrow(() -> new IllegalArgumentException("专业不存在"));
        var user = authorizationService.requireCurrentUser();
        String department = user.getDepartment() == null ? "" : user.getDepartment().trim();
        var role = user.getRole();
        boolean lead = !department.isEmpty() && department.equals(major.getDepartment());
        boolean permitted;
        if (write) {
            permitted = role == com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR && lead;
        } else if (role == com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR
                || role == com.classroom.ai.modules.auth.entity.RoleEnum.TEACHER) {
            permitted = lead || (!department.isEmpty() && majorRepository.findByDepartment(department).stream()
                    .anyMatch(m -> m.getMajorCode().equals(major.getMajorCode())));
            if (!permitted && role == com.classroom.ai.modules.auth.entity.RoleEnum.TEACHER)
                permitted = authorizationService.isTeachingMajor(major.getMajorCode());
        } else if (role == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR) {
            permitted = java.util.Arrays.stream(java.util.Optional.ofNullable(user.getAuthorizedMajors()).orElse("").split(";"))
                    .anyMatch(code -> code.trim().equalsIgnoreCase(major.getMajorCode()));
        } else {
            permitted = false;
        }
        if (!permitted) throw new com.classroom.ai.common.exception.ForbiddenException(write
                ? "专业公共培养方案目录仅由牵头教研室主任维护"
                : "当前教研室未关联该专业，或督导未获专业授权");
    }

    private final com.classroom.ai.modules.course.repository.CourseRepository courseRepository;
}
