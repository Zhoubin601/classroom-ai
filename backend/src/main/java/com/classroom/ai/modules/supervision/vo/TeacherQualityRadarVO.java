package com.classroom.ai.modules.supervision.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 教师教学质量 4 维雷达图与评语综合分析 (US-17)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherQualityRadarVO {
    private String teacherName;
    private String courseName;
    private Integer evaluationCount;
    /** 综合平均分 (0-100) */
    private Double overallScore;
    
    // BOPPPS 4 维雷达指标 (各维度得分 0-25，换算百分制 0-100)
    private Double attitudeScore;
    private Double contentScore;
    private Double methodScore;
    private Double effectScore;

    /** 教学亮点汇总 */
    private List<String> highlightList;
    /** 改进建议汇总 */
    private List<String> suggestionList;
    /** 词云高频词统计 */
    private List<WordCloudItem> wordCloud;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WordCloudItem {
        private String name;
        private Integer value;
    }
}
