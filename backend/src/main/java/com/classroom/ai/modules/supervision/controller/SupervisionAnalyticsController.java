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
    public ApiResponse<SupervisionDashboardVO> getDashboard() {
        return ApiResponse.success(analyticsService.getDashboardMetrics());
    }

    @GetMapping("/alerts")
    public ApiResponse<List<SupervisionAlertVO>> getAlerts() {
        return ApiResponse.success(analyticsService.getAlertList());
    }

    @GetMapping("/radar")
    public ApiResponse<TeacherQualityRadarVO> getTeacherRadar(@RequestParam(defaultValue = "郭军") String teacherName) {
        return ApiResponse.success(analyticsService.getTeacherRadar(teacherName));
    }

    @GetMapping("/export-report")
    public void exportAnnualQualityReport(HttpServletResponse response) throws IOException {
        String csvContent = analyticsService.generateAnnualQualityReportCsv();
        String filename = "2026-Northeastern-University-Teaching-Quality-Report.csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(csvContent.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
