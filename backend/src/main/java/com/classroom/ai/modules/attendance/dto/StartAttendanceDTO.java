package com.classroom.ai.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAttendanceDTO {
    private Long offeringId;
    private Integer weekNumber;
    private String classroom;
    private String operatorName;
    private String operatorRole;
    private String operatorTitle;
}
