package com.classroom.ai.modules.course.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowError {
    private int rowNumber;
    private String field;
    private String reason;
}
