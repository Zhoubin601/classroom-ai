package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 毕业要求指标点数据传输对象 (用于单个指标点的新增与修改)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorDTO {
    private Long id;
    private Long courseId;
    private String indicatorCode;
    private String requirementCategory;
    private String indicatorDescription;
    private String supportWeight;
    private String targetGoal;
}
