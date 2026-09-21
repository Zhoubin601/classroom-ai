package com.classroom.ai.modules.course.dto;

import lombok.Data;
import java.util.List;

@Data
public class CourseOfferingDTO {
    private Long courseId;
    private String academicTerm;
    private String className;
    private Long primaryTeacherId;
    private List<Long> teacherIds;
    private List<Long> studentIds;
    private List<Long> collaboratingTeacherIds;
    private List<String> studentNumbers;
}
