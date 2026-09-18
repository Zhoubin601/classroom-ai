package com.classroom.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceSearchDTO implements Serializable {

    // 待比对的 512 维人脸特征浮点数数组
    private List<Float> featureVector;

    // 相似度阈值（默认 0.45）
    @Builder.Default
    private Double threshold = 0.45;

    // 返回前 K 个最相似匹配结果（默认 1）
    @Builder.Default
    private Integer topK = 1;
}
