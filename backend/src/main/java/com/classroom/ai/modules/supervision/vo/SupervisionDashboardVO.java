package com.classroom.ai.modules.supervision.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全院督导覆盖率动态监控仪表盘 (US-15)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisionDashboardVO {
    /** 本学期全院开设课程总门数 */
    private Integer totalCourses;
    /** 已完成督导听课课程门数 */
    private Integer supervisedCourses;
    /** 督导覆盖率百分比，如 85.5 */
    private Double coverageRate;
    /** 累计开展督导听课总次数 */
    private Integer totalEvaluations;
    /** 全院平均得分 */
    private Double collegeAvgScore;
    /** 待督导覆盖课程数 */
    private Integer pendingCourses;
}
