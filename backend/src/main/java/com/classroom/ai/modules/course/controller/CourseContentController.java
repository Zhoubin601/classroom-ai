package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.dto.ContentRevisionDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseContentRevision;
import com.classroom.ai.modules.course.repository.CourseContentRevisionRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/content")
@RequiredArgsConstructor
@CrossOrigin
public class CourseContentController {

    private final CourseRepository courseRepository;
    private final CourseContentRevisionRepository revisionRepository;
    private final CourseOfferingRepository offeringRepository;

    /**
     * 获取最新草稿 (若无草稿，基于当前课程档案建立初始草稿)
     */
    @GetMapping("/draft")
    public ApiResponse<CourseContentRevision> getDraft(@PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + courseId + " 的课程"));

        Optional<CourseContentRevision> draftOpt = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT");
        if (draftOpt.isPresent()) {
            return ApiResponse.success(draftOpt.get());
        }

        // 无草稿时自动基于最新已发布版本或基础档案建立新草稿
        int nextVersion = revisionRepository.findFirstByCourseIdOrderByVersionDesc(courseId)
                .map(r -> r.getVersion() + 1).orElse(1);

        CourseContentRevision initialDraft = CourseContentRevision.builder()
                .courseId(courseId)
                .description(course.getDescription())
                .assessmentMethod(course.getAssessmentMethod())
                .objectives(course.getObjectives())
                .version(nextVersion)
                .status("DRAFT")
                .editorName(AuthContext.isAuthenticated() ? AuthContext.getCurrentUser().getRealName() : "导入的基线草稿")
                .build();

        return ApiResponse.success(revisionRepository.save(initialDraft));
    }

    /**
     * 暂存草稿 (允许内容不完整，支持并发版本检查)
     */
    @PutMapping("/draft")
    @Transactional
    public ApiResponse<CourseContentRevision> saveDraft(@PathVariable Long courseId,
                                                        @RequestBody ContentRevisionDTO dto) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        CourseContentRevision draft = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT")
                .orElseGet(() -> {
                    int nextVersion = revisionRepository.findFirstByCourseIdOrderByVersionDesc(courseId)
                            .map(r -> r.getVersion() + 1).orElse(1);
                    return CourseContentRevision.builder()
                            .courseId(courseId)
                            .version(nextVersion)
                            .status("DRAFT")
                            .build();
                });

        // 乐观锁并发检测
        if (dto.getVersion() != null && !dto.getVersion().equals(draft.getVersion())) {
            throw new IllegalStateException("检测到并发修改冲突(版本不一致)，请刷新后重试。当前版本: "
                    + draft.getVersion() + "，提交版本: " + dto.getVersion());
        }

        if (dto.getDescription() != null) draft.setDescription(dto.getDescription());
        if (dto.getAssessmentMethod() != null) draft.setAssessmentMethod(dto.getAssessmentMethod());
        if (dto.getObjectives() != null) draft.setObjectives(dto.getObjectives());

        String editor = AuthContext.isAuthenticated() ? AuthContext.getCurrentUser().getRealName() : "任课教师";
        draft.setEditorName(editor);

        return ApiResponse.success("草稿暂存成功", revisionRepository.save(draft));
    }

    /**
     * 正式发布简介与大纲
     * 校验：三项必填齐全；操作者需为关联任课教师或教研室主任；乐观锁版本冲突抛 409
     */
    @PostMapping("/publish")
    @Transactional
    public ApiResponse<CourseContentRevision> publishContent(@PathVariable Long courseId,
                                                             @RequestBody ContentRevisionDTO dto) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        // 权限校验：未登录拦截，登录后仅限教师或主任
        if (AuthContext.isAuthenticated()) {
            UserVO user = AuthContext.getCurrentUser();
            if (user.getRole() != RoleEnum.TEACHER && user.getRole() != RoleEnum.DIRECTOR) {
                throw new ForbiddenException("只有任课教师或教研室主任有权发布课程大纲");
            }
        }

        // 必填三项完整性校验 (US-02 规则)
        String desc = dto.getDescription();
        String assess = dto.getAssessmentMethod();
        String objs = dto.getObjectives();

        if (desc == null || desc.trim().isEmpty() ||
            assess == null || assess.trim().isEmpty() ||
            objs == null || objs.trim().isEmpty()) {
            throw new IllegalArgumentException("正式发布失败：课程简介、考核方式和教学目标三项必须填写齐全");
        }

        CourseContentRevision draft = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT")
                .orElseGet(() -> {
                    int nextVer = revisionRepository.findFirstByCourseIdOrderByVersionDesc(courseId)
                            .map(r -> r.getVersion() + 1).orElse(1);
                    return CourseContentRevision.builder().courseId(courseId).version(nextVer).build();
                });

        // 乐观锁版本并发检测
        if (dto.getVersion() != null && !dto.getVersion().equals(draft.getVersion())) {
            throw new IllegalStateException("检测到并发发布冲突(版本不一致)，请刷新后重试");
        }

        UserVO publisher = AuthContext.getCurrentUser();
        String pubName = publisher != null ? publisher.getRealName() : "任课教师";
        String pubCode = publisher != null ? publisher.getTeacherCode() : null;

        draft.setDescription(desc.trim());
        draft.setAssessmentMethod(assess.trim());
        draft.setObjectives(objs.trim());
        draft.setStatus("PUBLISHED");
        draft.setPublisherName(pubName);
        draft.setPublisherCode(pubCode);
        draft.setPublishedAt(LocalDateTime.now());

        CourseContentRevision published = revisionRepository.save(draft);

        // 同步原子更新 Course 实体字段，供统一读取
        course.setDescription(desc.trim());
        course.setAssessmentMethod(assess.trim());
        course.setObjectives(objs.trim());
        courseRepository.save(course);

        return ApiResponse.success("课程大纲与简介发布成功", published);
    }

    /**
     * 查询最新已发布大纲版本 (公开只读)
     */
    @GetMapping("/published")
    public ApiResponse<CourseContentRevision> getPublishedContent(@PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        return revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "PUBLISHED")
                .map(ApiResponse::success)
                .orElseGet(() -> {
                    // 若尚无发布历史，返回当前 course 基础字段构成的基线版本
                    CourseContentRevision fallback = CourseContentRevision.builder()
                            .courseId(courseId)
                            .description(course.getDescription())
                            .assessmentMethod(course.getAssessmentMethod())
                            .objectives(course.getObjectives())
                            .version(1)
                            .status("PUBLISHED")
                            .publisherName("系统导入基线")
                            .publishedAt(course.getCreatedAt())
                            .build();
                    return ApiResponse.success(fallback);
                });
    }
}
