package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.course.dto.ContentRevisionDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseContentRevision;
import com.classroom.ai.modules.course.repository.CourseContentRevisionRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/content")
@RequiredArgsConstructor
@CrossOrigin
public class CourseContentController {
    private final CourseRepository courseRepository;
    private final CourseContentRevisionRepository revisionRepository;
    private final CourseAuthorizationService authService;

    /** 只有关联教师可建立/查看工作草稿；课程行锁覆盖读取、创建和事务提交。 */
    @GetMapping("/draft")
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<CourseContentRevision> getDraft(@PathVariable Long courseId) {
        Course course = lockCourse(courseId);
        authService.validateTeacherCoursePublish(courseId);
        var existing = revisionRepository.findFirstByCourseIdAndStatusOrderByIdDesc(courseId, "DRAFT");
        if (existing.isPresent()) return ApiResponse.success(existing.get());
        var previous = revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED");
        var draft = CourseContentRevision.builder().courseId(courseId).status("DRAFT")
            .lockVersion(0).version(0).publishVersion(previous.map(this::publishedVersion).orElse(0))
            .description(previous.map(CourseContentRevision::getDescription).orElse(course.getDescription()))
            .assessmentMethod(previous.map(CourseContentRevision::getAssessmentMethod).orElse(course.getAssessmentMethod()))
            .objectives(previous.map(CourseContentRevision::getObjectives).orElse(course.getObjectives()))
            .editorName(AuthContext.getCurrentUser().getRealName()).build();
        return ApiResponse.success(revisionRepository.saveAndFlush(draft));
    }

    @PutMapping("/draft")
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<CourseContentRevision> saveDraft(@PathVariable Long courseId, @RequestBody ContentRevisionDTO dto) {
        lockCourse(courseId);
        authService.validateTeacherCoursePublish(courseId);
        CourseContentRevision draft = checkedDraft(courseId, dto);
        if (dto.getDescription() != null) draft.setDescription(dto.getDescription());
        if (dto.getAssessmentMethod() != null) draft.setAssessmentMethod(dto.getAssessmentMethod());
        if (dto.getObjectives() != null) draft.setObjectives(dto.getObjectives());
        draft.setEditorName(AuthContext.getCurrentUser().getRealName());
        // flush 后再返回，确保客户端拿到实际递增后的 JPA 锁版本。
        return ApiResponse.success("草稿暂存成功", revisionRepository.saveAndFlush(draft));
    }

    @PostMapping("/publish")
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<CourseContentRevision> publishContent(@PathVariable Long courseId, @RequestBody ContentRevisionDTO dto) {
        Course course = lockCourse(courseId);
        authService.validateTeacherCoursePublish(courseId);
        String description = dto.getDescription();
        String assessment = dto.getAssessmentMethod();
        String objectives = dto.getObjectives();
        if (description == null || description.isBlank() || assessment == null || assessment.isBlank()
                || objectives == null || objectives.isBlank())
            throw new IllegalArgumentException("正式发布失败：课程简介、考核方式和教学目标三项必须填写齐全");
        CourseContentRevision draft = checkedDraft(courseId, dto);
        var publisher = AuthContext.getCurrentUser();
        draft.setDescription(description.trim());
        draft.setAssessmentMethod(assessment.trim());
        draft.setObjectives(objectives.trim());
        draft.setStatus("PUBLISHED");
        draft.setPublishVersion(latestPublishedVersion(courseId) + 1);
        draft.setVersion(draft.getPublishVersion());
        draft.setEditorName(publisher.getRealName());
        draft.setPublisherName(publisher.getRealName());
        draft.setPublisherCode(publisher.getTeacherCode());
        draft.setPublishedAt(LocalDateTime.now());
        course.setDescription(draft.getDescription());
        course.setAssessmentMethod(draft.getAssessmentMethod());
        course.setObjectives(draft.getObjectives());
        course.setUpdatedBy(publisher.getUsername());
        courseRepository.save(course);
        CourseContentRevision published = revisionRepository.saveAndFlush(draft);
        revisionRepository.retireOtherDrafts(courseId, draft.getId());
        return ApiResponse.success("课程简介发布成功", published);
    }

    @GetMapping("/published")
    public ApiResponse<CourseContentRevision> getPublishedContent(@PathVariable Long courseId) {
        authService.validateCourseRead(courseId);
        return revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED")
            .map(ApiResponse::success).orElseGet(() -> ApiResponse.success("课程简介尚未发布", null));
    }

    private Course lockCourse(Long courseId) {
        return courseRepository.findForUpdate(courseId).orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));
    }

    /** 不能只比较 @Version：每次新建草稿时锁版本都会重置。 */
    private CourseContentRevision checkedDraft(Long courseId, ContentRevisionDTO dto) {
        if (dto.getDraftId() == null || dto.getLockVersion() == null || dto.getPublishVersion() == null)
            throw new IllegalStateException("检测到并发修改冲突：缺少草稿身份或版本，请重新拉取最新草稿");
        var draft = revisionRepository.findFirstByCourseIdAndStatusOrderByIdDesc(courseId, "DRAFT")
            .orElseThrow(() -> new IllegalStateException("草稿已发布或失效，请重新拉取最新草稿"));
        int base = draft.getPublishVersion() == null ? 0 : draft.getPublishVersion();
        if (!Objects.equals(dto.getDraftId(), draft.getId()) || !Objects.equals(dto.getLockVersion(), draft.getLockVersion())
                || dto.getPublishVersion() != base || base != latestPublishedVersion(courseId))
            throw new IllegalStateException("检测到并发修改冲突(版本不一致)，请重新拉取最新草稿");
        return draft;
    }

    private int latestPublishedVersion(Long courseId) {
        return revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED")
            .map(this::publishedVersion).orElse(0);
    }

    private int publishedVersion(CourseContentRevision revision) {
        return revision.getPublishVersion() != null ? revision.getPublishVersion()
            : revision.getVersion() != null ? revision.getVersion() : 0;
    }
}
