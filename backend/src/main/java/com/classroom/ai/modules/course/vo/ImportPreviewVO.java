package com.classroom.ai.modules.course.vo;

import com.classroom.ai.modules.course.dto.CourseImportRowDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewVO {
    private String batchId;
    private int totalCount;
    private int successCount;
    private int errorCount;
    private List<ImportRowError> errors;
    private List<CourseImportRowDTO> validRows;
}
