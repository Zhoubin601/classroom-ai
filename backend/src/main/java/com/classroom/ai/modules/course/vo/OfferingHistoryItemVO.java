package com.classroom.ai.modules.course.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferingHistoryItemVO {
    private Long offeringId;
    private String courseCode;
    private String courseName;
    private String academicTerm;
    private String primaryTeacher;
    private List<String> teachers;
    private String className;
    private String classroom;
    private Integer studentCount; // 单个班次选课人数
    private String status;
    private Boolean isSnapshotFrozen;
}
