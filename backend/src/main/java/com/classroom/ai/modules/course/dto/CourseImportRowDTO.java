package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImportRowDTO {
    private int rowNumber;
    private String courseCode;
    private String courseName;
    private String department;
    private String majorCode;
    private Double credits;
    private Integer hours;
    private Integer theoryHours;
    private Integer practiceHours;
    private String courseType;
    private String prerequisites;
    private String description;
}
