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
            String msg = Boolean.TRUE.equals(dto.getIsDraft()) ? "评教草稿暂存成功" : "督导评价已提交，已进入24小时延迟脱敏流转期";
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
}
