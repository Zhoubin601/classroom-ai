package com.classroom.ai.modules.supervision.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 督导预警实体 (US-16)
 * YELLOW_LOW_COVERAGE: 覆盖率<30% 或零覆盖 (黄色预警)
 * RED_LOW_SCORE: 督导均分<75分 (红色预警)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisionAlertVO {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private String department;
    /** YELLOW (黄色低覆盖) / RED (红色低均分) */
    private String alertLevel;
    private String alertType;
    private String alertMessage;
    private Double currentScore;
    private Integer evaluationCount;
    private String suggestAction;
}
