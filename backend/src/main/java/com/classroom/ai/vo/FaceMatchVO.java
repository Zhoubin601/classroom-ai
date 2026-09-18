package com.classroom.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceMatchVO implements Serializable {

    private String studentId;

    private String name;

    private String className;

    private String avatarUrl;

    // 余弦相似度 (0.0 ~ 1.0)
    private Double similarity;

    // 是否超过阈值成功匹配
    private Boolean matched;
}
