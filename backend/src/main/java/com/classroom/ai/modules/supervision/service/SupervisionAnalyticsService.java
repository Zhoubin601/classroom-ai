package com.classroom.ai.modules.supervision.service;

import com.classroom.ai.modules.supervision.vo.SupervisionAlertVO;
import com.classroom.ai.modules.supervision.vo.SupervisionDashboardVO;
import com.classroom.ai.modules.supervision.vo.TeacherQualityRadarVO;

import java.util.List;

public interface SupervisionAnalyticsService {
    /** 全院督导覆盖率动态监控指标 (US-15) */
    SupervisionDashboardVO getDashboardMetrics();

    /** 预警引擎：低覆盖率(<30%)黄色预警与低均分(<75分)红色预警清单 (US-16) */
    List<SupervisionAlertVO> getAlertList();

    /** 任课教师教学质量 4 维雷达图与评语分析 (US-17) */
    TeacherQualityRadarVO getTeacherRadar(String teacherName);

    /** 导出全员教学质量综合分析报表 CSV 内容 (US-18) */
    String generateAnnualQualityReportCsv();
}
