package com.classroom.ai.modules.supervision.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.service.SupervisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/supervisions")
@RequiredArgsConstructor
@CrossOrigin
public class SupervisionController {

    private final SupervisionService supervisionService;
    private final com.classroom.ai.modules.supervision.repository.EvaluationAuditLogRepository auditRepository;

    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.attitude:25}") private double attitudeWeight;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.content:25}") private double contentWeight;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.method:25}") private double methodWeight;
    @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.effect:25}") private double effectWeight;

    @GetMapping("/weights")
    public ApiResponse<java.util.Map<String, Double>> getWeights() {
        return ApiResponse.success(java.util.Map.of("attitude", attitudeWeight, "content", contentWeight,
                "method", methodWeight, "effect", effectWeight));
    }

    @GetMapping
    public ApiResponse<List<SupervisionEvaluation>> getAllEvaluations(@RequestParam(required = false) Long offeringId,
                                                                     @RequestParam(required = false) String teacherName) {
        if (offeringId != null) {
            return ApiResponse.success(supervisionService.getEvaluationsByOffering(offeringId));
        }
        if (teacherName != null) {
            return ApiResponse.success(supervisionService.getEvaluationsByTeacher(teacherName));
        }
        return ApiResponse.success(supervisionService.getAllEvaluations());
    }

    @GetMapping("/{id}")
    public ApiResponse<SupervisionEvaluation> getEvaluationById(@PathVariable Long id) {
        return ApiResponse.success(supervisionService.getEvaluationById(id));
    }

    @PostMapping
    public ApiResponse<SupervisionEvaluation> submitEvaluation(@RequestBody EvaluationSubmitDTO dto) {
        try {
            SupervisionEvaluation result = supervisionService.submitEvaluation(dto);
            String msg = Boolean.TRUE.equals(dto.getIsDraft()) ? "评教草稿暂存成功" : "督导评价已提交，等待教研室主任审核";
            return ApiResponse.success(msg, result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEvaluation(@PathVariable Long id) {
        supervisionService.deleteEvaluation(id);
        return ApiResponse.success("评价记录删除成功", null);
    }

    public record ReviewRequest(boolean approved, String note) {}

    @PostMapping("/{id}/review")
    public ApiResponse<SupervisionEvaluation> review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return ApiResponse.success("评价审核完成", supervisionService.reviewEvaluation(id, request.approved(), request.note()));
    }

    @GetMapping("/{id}/audit")
    public ApiResponse<List<com.classroom.ai.modules.supervision.entity.EvaluationAuditLog>> audit(@PathVariable Long id) {
        com.classroom.ai.modules.auth.context.AuthContext.requireRole(com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR);
        supervisionService.getEvaluationById(id);
        return ApiResponse.success(auditRepository.findByEvaluationIdOrderByOccurredAtAsc(id));
    }
}
