package com.classroom.ai.modules.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResourceDTO {
    private Long id;
    private Long courseId;
    private String chapter;
    private String resourceName;
    private String fileType;
    private String fileUrl;
    private String fileSize;
    private Long fileSizeBytes;
    private String tag;
    private java.util.List<String> tags;
    private String version;
    private Boolean isPublic;
    private String dynamicWatermark;
    private String uploaderTeacher;
}
