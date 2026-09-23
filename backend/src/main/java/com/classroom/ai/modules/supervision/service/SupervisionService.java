package com.classroom.ai.modules.supervision.service;

import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;

import java.util.List;

public interface SupervisionService {
    List<SupervisionEvaluation> getAllEvaluations();
    List<SupervisionEvaluation> getEvaluationsByOffering(Long offeringId);
    List<SupervisionEvaluation> getEvaluationsByTeacher(String teacherName);
    SupervisionEvaluation getEvaluationById(Long id);
    SupervisionEvaluation submitEvaluation(EvaluationSubmitDTO dto);
    void deleteEvaluation(Long id);
    void refreshDesensitizeStatus();
    SupervisionEvaluation reviewEvaluation(Long id, boolean approved, String note);
}
