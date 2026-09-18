package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentRevisionDTO {
    private Long courseId;
    private String description;
    private String assessmentMethod;
    private String objectives;
    private Integer version;
}
