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
    public void exportAnnualQualityReport(HttpServletResponse response) throws IOException {
        com.classroom.ai.modules.auth.context.AuthContext.requireRole(com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR);
        String csvContent = analyticsService.generateAnnualQualityReportCsv();
        String filename = "2026-Northeastern-University-Teaching-Quality-Report.csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(csvContent.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
