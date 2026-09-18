package com.classroom.ai.modules.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroTeachingSliceDTO {
    private Long id;
    private Long courseId;
    private String videoTitle;
    private String bopppsStage;
    private Integer durationSeconds;
    private String sliceUrl;
    private String coverUrl;
    private String recordedDate;
    private String classroom;
    private String sourceAgent;
}
