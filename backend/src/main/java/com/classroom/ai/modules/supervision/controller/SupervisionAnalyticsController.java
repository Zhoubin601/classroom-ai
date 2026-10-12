package com.classroom.ai.modules.supervision.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.supervision.service.SupervisionAnalyticsService;
import com.classroom.ai.modules.supervision.vo.SupervisionAlertVO;
import com.classroom.ai.modules.supervision.vo.SupervisionDashboardVO;
import com.classroom.ai.modules.supervision.vo.TeacherQualityRadarVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/supervisions/analytics")
@RequiredArgsConstructor
@CrossOrigin
public class SupervisionAnalyticsController {

    private final SupervisionAnalyticsService analyticsService;
    private final com.classroom.ai.modules.sprint3.QualityStatisticsService quality;

    @GetMapping("/dashboard")
    public ApiResponse<SupervisionDashboardVO> getDashboard(@RequestParam(required = false) String term) {
        com.classroom.ai.modules.auth.context.AuthContext.requireRole(
                com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR, com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR);
        return ApiResponse.success(analyticsService.getDashboardMetrics(term));
    }

    @GetMapping("/coverage")
    public ApiResponse<List<com.classroom.ai.modules.supervision.vo.CoverageDetailVO>> getCoverage(
            @RequestParam(required = false) String term) {
        com.classroom.ai.modules.auth.context.AuthContext.requireRole(
                com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR, com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR);
        return ApiResponse.success(analyticsService.getCoverageDetails(term));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<SupervisionAlertVO>> getAlerts() {
        com.classroom.ai.modules.auth.context.AuthContext.requireRole(
                com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR, com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR);
        return ApiResponse.success(analyticsService.getAlertList());
    }

    @GetMapping("/radar")
    public ApiResponse<TeacherQualityRadarVO> getTeacherRadar(@RequestParam(defaultValue = "郭军") String teacherName) {
        var user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
        if (user != null && user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.TEACHER
                && !teacherName.equals(user.getRealName()))
            throw new com.classroom.ai.common.exception.ForbiddenException("只能查看本人教学质量反馈");
        return ApiResponse.success(analyticsService.getTeacherRadar(teacherName));
    }

    @GetMapping("/export-report")
    public org.springframework.http.ResponseEntity<byte[]> exportAnnualQualityReport(@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(required=false) String keyword,@RequestParam(required=false) String teacher,@RequestParam(defaultValue="false") boolean uncategorized,@RequestParam(defaultValue="csv") String format) throws IOException {
        com.classroom.ai.modules.sprint3.Sprint3Policy.roles(com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR);
        if(!java.util.Set.of("csv","xlsx").contains(format))throw new IllegalArgumentException("格式须为csv/xlsx");
        return com.classroom.ai.modules.sprint3.Sprint3Controller.exportResponse(quality.export(new com.classroom.ai.modules.sprint3.QualityStatisticsService.Filter(term,courseType,keyword,teacher,uncategorized),format),format);
    }
}
