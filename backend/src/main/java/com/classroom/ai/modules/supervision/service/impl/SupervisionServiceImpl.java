package com.classroom.ai.modules.supervision.service.impl;

import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.modules.supervision.service.SupervisionService;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.supervision.entity.EvaluationAuditLog;
import com.classroom.ai.modules.supervision.repository.EvaluationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupervisionServiceImpl implements SupervisionService {

    private final SupervisionEvaluationRepository evaluationRepository;
    private final CourseOfferingRepository offeringRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private CourseAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    private EvaluationAuditLogRepository auditRepository;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.feedback-delay-hours:24}")
    private long feedbackDelayHours = 24;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.attitude:25}")
    private double attitudeWeight = 25;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.content:25}")
    private double contentWeight = 25;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.method:25}")
    private double methodWeight = 25;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.effect:25}")
    private double effectWeight = 25;

    @PostConstruct
    public void validateWeightConfiguration() {
        if (!Double.isFinite(attitudeWeight) || !Double.isFinite(contentWeight)
                || !Double.isFinite(methodWeight) || !Double.isFinite(effectWeight)
                || attitudeWeight < 0 || contentWeight < 0 || methodWeight < 0 || effectWeight < 0
                || Math.abs(attitudeWeight + contentWeight + methodWeight + effectWeight - 100) > 0.001) {
            throw new IllegalStateException("督导评价四项权重必须非负且合计为100");
        }
    }

    @Override
    public List<SupervisionEvaluation> getAllEvaluations() {
        refreshDesensitizeStatus();
        return visible(evaluationRepository.findAll());
    }

    @Override
    public List<SupervisionEvaluation> getEvaluationsByOffering(Long offeringId) {
        refreshDesensitizeStatus();
        return visible(evaluationRepository.findByOfferingId(offeringId));
    }

    @Override
    public List<SupervisionEvaluation> getEvaluationsByTeacher(String teacherName) {
        refreshDesensitizeStatus();
        UserVO user = AuthContext.getCurrentUser();
        if (user != null && user.getRole() == RoleEnum.TEACHER && !teacherName.equals(user.getRealName()))
            throw new ForbiddenException("只能查看本人评价反馈");
        return visible(evaluationRepository.findByTeacherName(teacherName));
    }

    @Override
    public SupervisionEvaluation getEvaluationById(Long id) {
        SupervisionEvaluation evaluation = evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的督导评价记录"));
        List<SupervisionEvaluation> allowed = visible(List.of(evaluation));
        if (allowed.isEmpty()) throw new ForbiddenException("无权查看该评价");
        return allowed.get(0);
    }

    @Override
    @Transactional
    public SupervisionEvaluation submitEvaluation(EvaluationSubmitDTO dto) {
        UserVO actor = AuthContext.getCurrentUser();
        if (actor != null && actor.getRole() != RoleEnum.SUPERVISOR)
            throw new ForbiddenException("仅督导可提交评价");
        CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getOfferingId() + " 的开课班次"));
        if (authorization != null) authorization.validateOfferingRead(offering);
        validateWeightConfiguration();

        // 校验四维打分 (各 0-25 分)
        double attitude = dto.getScoreAttitude() != null ? dto.getScoreAttitude() : 0.0;
        double content = dto.getScoreContent() != null ? dto.getScoreContent() : 0.0;
        double method = dto.getScoreMethod() != null ? dto.getScoreMethod() : 0.0;
        double effect = dto.getScoreEffect() != null ? dto.getScoreEffect() : 0.0;

        if (!Double.isFinite(attitude) || !Double.isFinite(content) || !Double.isFinite(method) || !Double.isFinite(effect)
                || attitude < 0 || attitude > attitudeWeight || content < 0 || content > contentWeight ||
            method < 0 || method > methodWeight || effect < 0 || effect > effectWeight) {
            throw new IllegalArgumentException("BOPPPS 四维评分必须在各自配置权重范围内");
        }

        if ((dto.getHighlights() != null && dto.getHighlights().length() > 500)
                || (dto.getSuggestions() != null && dto.getSuggestions().length() > 500)) {
            throw new IllegalArgumentException("教学亮点和改进建议每项不能超过500字");
        }
        boolean isDraft = Boolean.TRUE.equals(dto.getIsDraft());
        if (!isDraft && (dto.getListenTopic() == null || dto.getListenTopic().isBlank()
                || dto.getHighlights() == null || dto.getHighlights().isBlank()
                || dto.getSuggestions() == null || dto.getSuggestions().isBlank()))
            throw new IllegalArgumentException("正式提交须填写听课主题、教学亮点和改进建议");

        double totalScore = attitude + content + method + effect;

        SupervisionEvaluation evaluation;
        if (dto.getId() != null) {
            evaluation = getEvaluationById(dto.getId());
            if (!java.util.Objects.equals(evaluation.getOffering().getId(), offering.getId()))
                throw new IllegalArgumentException("不可更改评价所属开课班次");
            if (!"DRAFT".equals(evaluation.getStatus()) && !"REJECTED".equals(evaluation.getStatus()))
                throw new IllegalStateException("已提交的评价不可编辑");
            if (actor != null && !java.util.Objects.equals(evaluation.getSupervisorUserId(), actor.getId()))
                throw new ForbiddenException("只能编辑本人评价草稿");
        } else {
            evaluation = new SupervisionEvaluation();
            evaluation.setOffering(offering);
        }

        String oldStatus = evaluation.getStatus();
        evaluation.setSupervisorName(actor != null ? (actor.getRealName() == null ? actor.getUsername() : actor.getRealName()) :
                (dto.getSupervisorName() != null ? dto.getSupervisorName() : "特邀督导专家"));
        if (actor != null) evaluation.setSupervisorUserId(actor.getId());
        evaluation.setEvaluateDate(dto.getEvaluateDate() != null ? dto.getEvaluateDate() : LocalDateTime.now().toLocalDate().toString());
        evaluation.setListenTopic(dto.getListenTopic() != null ? dto.getListenTopic() : "课程教学听评");
        evaluation.setScoreAttitude(attitude);
        evaluation.setScoreContent(content);
        evaluation.setScoreMethod(method);
        evaluation.setScoreEffect(effect);
        evaluation.setTotalScore(totalScore);
        evaluation.setHighlights(dto.getHighlights());
        evaluation.setSuggestions(dto.getSuggestions());

        // US-13 & US-14 业务规则：
        if (isDraft) {
            evaluation.setStatus("DRAFT");
            evaluation.setSubmitTime(null);
            evaluation.setPublishTime(null);
        } else {
            evaluation.setStatus("PENDING_REVIEW");
            evaluation.setSubmitTime(LocalDateTime.now());
            evaluation.setPublishTime(null);
        }
        SupervisionEvaluation saved = evaluationRepository.save(evaluation);
        audit(saved, actor, oldStatus, isDraft ? "SAVE_DRAFT" : "SUBMIT");
        return saved;
    }

    @Override
    @Transactional
    public void deleteEvaluation(Long id) {
        SupervisionEvaluation e = getEvaluationById(id);
        UserVO user = AuthContext.getCurrentUser();
        if (!"DRAFT".equals(e.getStatus()) && !"REJECTED".equals(e.getStatus()))
            throw new IllegalStateException("已提交评价不可删除");
        if (user != null && !java.util.Objects.equals(user.getId(), e.getSupervisorUserId()))
            throw new ForbiddenException("只能删除本人草稿");
        audit(e, user, e.getStatus(), "DELETE");
        evaluationRepository.deleteById(id);
    }

    @Override
    @Transactional
    public SupervisionEvaluation reviewEvaluation(Long id, boolean approved, String note) {
        UserVO reviewer = AuthContext.getCurrentUser();
        if (reviewer == null || reviewer.getRole() != RoleEnum.DIRECTOR) throw new ForbiddenException("仅教研室主任可审核评价");
        SupervisionEvaluation e = evaluationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("评价不存在"));
        if (authorization != null) authorization.validateCourseWrite(e.getOffering().getCourse());
        if (!"PENDING_REVIEW".equals(e.getStatus())) throw new IllegalStateException("仅待审核评价可审核");
        String old = e.getStatus();
        e.setReviewedBy(reviewer.getId());
        e.setReviewedAt(LocalDateTime.now());
        e.setReviewNote(note);
        e.setStatus(approved ? "APPROVED_PENDING" : "REJECTED");
        e.setPublishTime(approved ? LocalDateTime.now().plusHours(feedbackDelayHours) : null);
        SupervisionEvaluation saved = evaluationRepository.save(e);
        audit(saved, reviewer, old, approved ? "APPROVE" : "REJECT");
        return saved;
    }

    @Override
    @Transactional
    public void refreshDesensitizeStatus() {
        List<SupervisionEvaluation> list = evaluationRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (SupervisionEvaluation eval : list) {
            if ("APPROVED_PENDING".equals(eval.getStatus()) && eval.getPublishTime() != null && !now.isBefore(eval.getPublishTime())) {
                eval.setStatus("PUBLISHED");
                evaluationRepository.save(eval);
            }
        }
    }

    private List<SupervisionEvaluation> visible(List<SupervisionEvaluation> source) {
        UserVO user = AuthContext.getCurrentUser();
        if (user == null || authorization == null) return source.stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus())).map(this::anonymize).toList();
        return source.stream().filter(e -> {
            try {
                if (user.getRole() == RoleEnum.DIRECTOR) {
                    authorization.validateCourseRead(e.getOffering().getCourse());
                    return true;
                }
                if (user.getRole() == RoleEnum.SUPERVISOR) {
                    authorization.validateOfferingRead(e.getOffering());
                    return "PUBLISHED".equals(e.getStatus()) || java.util.Objects.equals(user.getId(), e.getSupervisorUserId());
                }
                if (user.getRole() == RoleEnum.TEACHER) {
                    authorization.validateOfferingRead(e.getOffering());
                    return "PUBLISHED".equals(e.getStatus());
                }
            } catch (ForbiddenException ex) { return false; }
            return false;
        }).map(e -> user.getRole() == RoleEnum.TEACHER ? anonymize(e) : e).toList();
    }

    private SupervisionEvaluation anonymize(SupervisionEvaluation e) {
        SupervisionEvaluation view = new SupervisionEvaluation();
        org.springframework.beans.BeanUtils.copyProperties(e, view);
        view.setSupervisorName("匿名督导");
        view.setSupervisorUserId(null);
        view.setReviewedBy(null);
        view.setReviewNote(null);
        return view;
    }

    private void audit(SupervisionEvaluation e, UserVO actor, String oldStatus, String action) {
        if (auditRepository == null || actor == null) return;
        EvaluationAuditLog log = new EvaluationAuditLog();
        log.setEvaluationId(e.getId());
        log.setActorId(actor.getId());
        log.setActorUsername(actor.getUsername());
        log.setAction(action);
        log.setFromStatus(oldStatus);
        log.setToStatus(e.getStatus());
        log.setOccurredAt(LocalDateTime.now());
        auditRepository.save(log);
    }
}
