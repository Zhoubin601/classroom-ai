package com.classroom.ai.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinishAttendanceDTO {
    private Long sessionId;
    private Integer actualCount;
    private Double avgLookupRate;
    private List<String> absentStudentIds;
}
