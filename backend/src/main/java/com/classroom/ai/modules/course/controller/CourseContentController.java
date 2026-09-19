package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.vo.UserVO;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/content")
@RequiredArgsConstructor
@CrossOrigin
public class CourseContentController {

    private final CourseRepository courseRepository;
    private final CourseContentRevisionRepository revisionRepository;
    private final CourseAuthorizationService authService;

    /**
     * 获取最新草稿 (若无草稿，基于最新已发布版本或基础档案建立新草稿)
     */
    @GetMapping("/draft")
    public ApiResponse<CourseContentRevision> getDraft(@PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + courseId + " 的课程"));

        if (authService != null) {
            authService.validateCourseRead(courseId);
        }

        Optional<CourseContentRevision> draftOpt = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT");
        if (draftOpt.isPresent()) {
            return ApiResponse.success(draftOpt.get());
        }

        // 无草稿时，优先以最新已发布版本作为模板生成草稿
        Optional<CourseContentRevision> publishedOpt = revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED");
        CourseContentRevision initialDraft;
        if (publishedOpt.isPresent()) {
            CourseContentRevision pub = publishedOpt.get();
            initialDraft = CourseContentRevision.builder()
                    .courseId(courseId)
                    .description(pub.getDescription())
                    .assessmentMethod(pub.getAssessmentMethod())
                    .objectives(pub.getObjectives())
                    .publishVersion(pub.getPublishVersion())
                    .lockVersion(0)
                    .version(0)
                    .status("DRAFT")
                    .editorName(AuthContext.isAuthenticated() ? AuthContext.getCurrentUser().getRealName() : "任课教师")
                    .build();
        } else {
            initialDraft = CourseContentRevision.builder()
                    .courseId(courseId)
                    .description(course.getDescription())
                    .assessmentMethod(course.getAssessmentMethod())
                    .objectives(course.getObjectives())
                    .publishVersion(null)
                    .lockVersion(0)
                    .version(0)
                    .status("DRAFT")
                    .editorName(AuthContext.isAuthenticated() ? AuthContext.getCurrentUser().getRealName() : "任课教师")
                    .build();
        }

        return ApiResponse.success(revisionRepository.save(initialDraft));
    }

    /**
     * 暂存草稿 (允许内容不完整，采用并发乐观锁检测，版本冲突返回 409)
     */
    @PutMapping("/draft")
    @Transactional
    public ApiResponse<CourseContentRevision> saveDraft(@PathVariable Long courseId,
                                                        @RequestBody ContentRevisionDTO dto) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        if (authService != null) {
            authService.validateCourseWrite(courseId);
        }

        CourseContentRevision draft = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT")
                .orElseGet(() -> {
                    Optional<CourseContentRevision> pubOpt = revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED");
                    Integer basePubVer = pubOpt.map(CourseContentRevision::getPublishVersion).orElse(null);
                    return CourseContentRevision.builder()
                            .courseId(courseId)
                            .publishVersion(basePubVer)
                            .lockVersion(0)
                            .version(0)
                            .status("DRAFT")
                            .build();
                });

        // 乐观锁并发检测 (客户端提交的并发锁版本若与草稿版本不匹配，抛 409)
        Integer clientLockVersion = dto.getEffectiveLockVersion();
        Integer draftLockVersion = draft.getLockVersion() != null ? draft.getLockVersion() : draft.getVersion();
        if (clientLockVersion != null && draftLockVersion != null && !clientLockVersion.equals(draftLockVersion)) {
            throw new IllegalStateException("检测到并发修改冲突(版本不一致)，请刷新后重试。当前版本: "
                    + draftLockVersion + "，提交版本: " + clientLockVersion);
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
     * 校验：
     * 1. 权限：严格限定只允许关联任课教师操作 (未登录 401，非关联教师 403)；
     * 2. 完整性：课程简介、考核方式、教学目标三项必填且非空；
     * 3. 并发锁：版本不匹配抛 409；
     * 4. 业务版本：publishVersion 递增；
     * 5. 状态流转：转为 PUBLISHED，并同步更新 Course 实体。
     */
    @PostMapping("/publish")
    @Transactional
    public ApiResponse<CourseContentRevision> publishContent(@PathVariable Long courseId,
                                                             @RequestBody ContentRevisionDTO dto) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        // 权限校验：只允许关联任课教师发布 (未登录 401，非关联任课教师 403)
        if (authService != null) {
            authService.validateTeacherCoursePublish(courseId);
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

        Optional<CourseContentRevision> draftOpt = revisionRepository.findFirstByCourseIdAndStatusOrderByVersionDesc(courseId, "DRAFT");

        // 乐观锁并发检测
        Integer clientLockVersion = dto.getEffectiveLockVersion();
        if (draftOpt.isPresent()) {
            CourseContentRevision draft = draftOpt.get();
            Integer draftLockVersion = draft.getLockVersion() != null ? draft.getLockVersion() : draft.getVersion();
            if (clientLockVersion != null && draftLockVersion != null && !clientLockVersion.equals(draftLockVersion)) {
                throw new IllegalStateException("检测到并发发布冲突(版本不一致)，请刷新后重试");
            }
        }

        // 计算自增发布版本号
        Optional<CourseContentRevision> latestPub = revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED");
        int nextPubVersion = latestPub.map(r -> (r.getPublishVersion() != null ? r.getPublishVersion() : 0) + 1).orElse(1);

        CourseContentRevision pubRevision = draftOpt.orElseGet(() -> CourseContentRevision.builder()
                .courseId(courseId)
                .build());

        UserVO publisher = AuthContext.getCurrentUser();
        String pubName = publisher != null ? publisher.getRealName() : "任课教师";
        String pubCode = publisher != null ? publisher.getTeacherCode() : null;

        pubRevision.setDescription(desc.trim());
        pubRevision.setAssessmentMethod(assess.trim());
        pubRevision.setObjectives(objs.trim());
        pubRevision.setStatus("PUBLISHED");
        pubRevision.setPublishVersion(nextPubVersion);
        pubRevision.setVersion(nextPubVersion);
        pubRevision.setPublisherName(pubName);
        pubRevision.setPublisherCode(pubCode);
        pubRevision.setPublishedAt(LocalDateTime.now());

        CourseContentRevision published = revisionRepository.save(pubRevision);

        // 同步更新 Course 实体字段，供统一读取
        course.setDescription(desc.trim());
        course.setAssessmentMethod(assess.trim());
        course.setObjectives(objs.trim());
        courseRepository.save(course);

        return ApiResponse.success("课程大纲与简介发布成功", published);
    }

    /**
     * 查询最新已发布大纲版本 (读者公开只读)
     * 规则：编辑已发布内容生成草稿，读者继续看到上一发布版本；未发布内容不伪装成已发布
     */
    @GetMapping("/published")
    public ApiResponse<CourseContentRevision> getPublishedContent(@PathVariable Long courseId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程: " + courseId));

        return revisionRepository.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(courseId, "PUBLISHED")
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.success("课程简介尚未发布", null));
    }
}
