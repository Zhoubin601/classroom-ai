package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusDTO {
    private Long id;
    private Long courseId;
    private String version;
    private String status;
    private String authorTeacher;
    private String lockedBy;
    private String courseGoals;
    private List<IndicatorDTO> indicators;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorDTO {
        private Long id;
        private String indicatorCode;
        private String requirementCategory;
        private String indicatorDescription;
        private String supportWeight;
        private String targetGoal;
    }
}
