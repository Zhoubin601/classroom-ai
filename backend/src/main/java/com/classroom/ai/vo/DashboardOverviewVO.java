package com.classroom.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVO implements Serializable {

    // 班级应到学生总数
    private Integer totalRegistered;

    // 当前检测/出勤人数
    private Integer currentPresent;

    // 缺勤人数
    private Integer currentAbsent;

    // 出勤率百分比 (e.g. 95.0)
    private Double attendanceRate;

    // 实时平均抬头率百分比 (e.g. 88.5)
    private Double realtimeLookupRate;

    // 异常低头人数
    private Integer lookdownCount;

    // 专注度总体评级: "优秀", "良好", "一般", "需关注"
    private String focusLevel;

    // 最近上报更新时间
    private String lastUpdateTime;
}
