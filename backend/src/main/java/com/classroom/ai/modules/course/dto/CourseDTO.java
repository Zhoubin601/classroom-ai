package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDTO {
    private Long id;
    private String courseCode;
    private String courseName;
    private String department;
    private String teacherName;
    private Long majorId;
    private String majorCode;
    private Double credits;
    private Integer hours;
    private Integer theoryHours;
    private Integer practiceHours;
    private String courseType;
    private String prerequisites;
    private String description;
    private String objectives;
    private String assessmentMethod;
}
