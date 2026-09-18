package com.classroom.ai.modules.supervision.service.impl;

import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.modules.supervision.service.SupervisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupervisionServiceImpl implements SupervisionService {

    private final SupervisionEvaluationRepository evaluationRepository;
    private final CourseOfferingRepository offeringRepository;

    @Override
    public List<SupervisionEvaluation> getAllEvaluations() {
        refreshDesensitizeStatus();
        return evaluationRepository.findAll();
    }

    @Override
    public List<SupervisionEvaluation> getEvaluationsByOffering(Long offeringId) {
        refreshDesensitizeStatus();
        return evaluationRepository.findByOfferingId(offeringId);
    }

    @Override
    public List<SupervisionEvaluation> getEvaluationsByTeacher(String teacherName) {
        refreshDesensitizeStatus();
        return evaluationRepository.findByTeacherName(teacherName).stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus()))
                .map(e -> {
                    SupervisionEvaluation view = new SupervisionEvaluation();
                    org.springframework.beans.BeanUtils.copyProperties(e, view);
                    view.setSupervisorName("匿名督导");
                    return view;
                }).toList();
    }

    @Override
    public SupervisionEvaluation getEvaluationById(Long id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的督导评价记录"));
    }

    @Override
    @Transactional
    public SupervisionEvaluation submitEvaluation(EvaluationSubmitDTO dto) {
        CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getOfferingId() + " 的开课班次"));

        // 校验四维打分 (各 0-25 分)
        double attitude = dto.getScoreAttitude() != null ? dto.getScoreAttitude() : 0.0;
        double content = dto.getScoreContent() != null ? dto.getScoreContent() : 0.0;
        double method = dto.getScoreMethod() != null ? dto.getScoreMethod() : 0.0;
        double effect = dto.getScoreEffect() != null ? dto.getScoreEffect() : 0.0;

        if (!Double.isFinite(attitude) || !Double.isFinite(content) || !Double.isFinite(method) || !Double.isFinite(effect)
                || attitude < 0 || attitude > 25 || content < 0 || content > 25 ||
            method < 0 || method > 25 || effect < 0 || effect > 25) {
            throw new IllegalArgumentException("BOPPPS 四维评分每项必须在 0 ~ 25 分之间！");
        }

        if ((dto.getHighlights() != null && dto.getHighlights().length() > 500)
                || (dto.getSuggestions() != null && dto.getSuggestions().length() > 500)) {
            throw new IllegalArgumentException("教学亮点和改进建议每项不能超过500字");
        }

        double totalScore = attitude + content + method + effect;

        SupervisionEvaluation evaluation;
        if (dto.getId() != null) {
            evaluation = getEvaluationById(dto.getId());
        } else {
            evaluation = new SupervisionEvaluation();
            evaluation.setOffering(offering);
        }

        evaluation.setSupervisorName(dto.getSupervisorName() != null ? dto.getSupervisorName() : "特邀督导专家");
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
        boolean isDraft = Boolean.TRUE.equals(dto.getIsDraft());
        if (isDraft) {
            evaluation.setStatus("DRAFT");
            evaluation.setSubmitTime(null);
            evaluation.setPublishTime(null);
        } else {
            // 提交时进入 24 小时延迟脱敏期
            evaluation.setStatus("PENDING_DESENSITIZE");
            evaluation.setSubmitTime(LocalDateTime.now());
            // 业务规定：24 小时后脱敏并向任课教师公开
            evaluation.setPublishTime(LocalDateTime.now().plusHours(24));
        }

        return evaluationRepository.save(evaluation);
    }

    @Override
    @Transactional
    public void deleteEvaluation(Long id) {
        evaluationRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void refreshDesensitizeStatus() {
        List<SupervisionEvaluation> list = evaluationRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (SupervisionEvaluation eval : list) {
            if ("PENDING_DESENSITIZE".equals(eval.getStatus()) && eval.getPublishTime() != null && now.isAfter(eval.getPublishTime())) {
                eval.setStatus("PUBLISHED");
                evaluationRepository.save(eval);
            }
        }
    }
}
