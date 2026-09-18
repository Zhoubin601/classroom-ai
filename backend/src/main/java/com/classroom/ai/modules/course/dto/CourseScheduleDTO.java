package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseScheduleDTO {
    private Long id;
    private Long offeringId;
    private String classroom;
    private String weekRange;
    private Integer startWeek;
    private Integer endWeek;
    private Integer dayOfWeek;
    private Integer startPeriod;
    private Integer endPeriod;
}
