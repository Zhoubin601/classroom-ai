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
public class FaceRegisterDTO implements Serializable {

    private String studentId;

    private String name;

    private String gender;

    private String className;

    // 512维人脸特征浮点数数组
    private List<Float> featureVector;

    // 人脸切片存储路径或图片URL
    private String imagePath;
}
