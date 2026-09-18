package com.classroom.ai.modules.supervision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSubmitDTO {
    private Long id;
    private Long offeringId;
    private String supervisorName;
    private String evaluateDate;
    private String listenTopic;
    private Double scoreAttitude;
    private Double scoreContent;
    private Double scoreMethod;
    private Double scoreEffect;
    private String highlights;
    private String suggestions;
    /** 是否仅作为暂存草稿 (US-13) */
    private Boolean isDraft;
}
