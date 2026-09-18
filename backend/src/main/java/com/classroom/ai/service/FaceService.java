package com.classroom.ai.service;

import com.classroom.ai.dto.FaceRegisterDTO;
import com.classroom.ai.dto.FaceSearchDTO;
import com.classroom.ai.entity.FaceFeature;
import com.classroom.ai.vo.FaceMatchVO;

import java.util.List;

public interface FaceService {

    /**
     * 注册或更新学生人脸特征
     */
    FaceFeature registerFace(FaceRegisterDTO dto);

    /**
     * 获取全量学生人脸特征数据（供边缘端/Python端启动全量同步）
     */
    List<FaceRegisterDTO> getAllFaceFeatures();

    /**
     * 1:N 人脸特征检索比对（基于 Redis 特征缓存计算余弦相似度）
     */
    FaceMatchVO searchFace(FaceSearchDTO dto);

    /**
     * 获取指定学生人脸特征
     */
    FaceRegisterDTO getFaceByStudentId(String studentId);

    /**
     * 删除指定学生人脸特征
     */
    boolean deleteFace(String studentId);

    /**
     * 调起本地 Python face_register.py 摄像头人脸录入进程
     */
    boolean launchCameraRegister(String studentId, String name, String className);
}
