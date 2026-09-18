package com.classroom.ai.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.config.UploadPaths;
import com.classroom.ai.dto.FaceRegisterDTO;
import com.classroom.ai.dto.FaceSearchDTO;
import com.classroom.ai.entity.FaceFeature;
import com.classroom.ai.service.FaceService;
import com.classroom.ai.vo.FaceMatchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/face")
@RequiredArgsConstructor
public class FaceController {

    private final FaceService faceService;

    @Value("${classroom.upload-dir:}")
    private String uploadDir;

    /**
     * 1. 注册学生人脸数据（直接传输 512 维特征向量 JSON）
     */
    @PostMapping("/register")
    public ApiResponse<FaceFeature> registerFace(@RequestBody FaceRegisterDTO dto) {
        FaceFeature feature = faceService.registerFace(dto);
        return ApiResponse.success("Face registered successfully", feature);
    }

    /**
     * 2. 上传人脸照片底库文件
     */
    @PostMapping("/upload")
    public ApiResponse<String> uploadFaceImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("Uploaded file is empty");
        }
        try {
            Path targetDir = UploadPaths.resolve(uploadDir);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            File dest = targetDir.resolve(filename).toFile();
            file.transferTo(dest);

            String relativeUrl = "/uploads/faces/" + filename;
            return ApiResponse.success("Image uploaded", relativeUrl);
        } catch (IOException e) {
            log.error("Failed to upload face photo", e);
            return ApiResponse.error("Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * 3. 全量获取所有人脸特征库（供 Python 边缘视觉端启动/更新时一键同步）
     */
    @GetMapping("/all")
    public ApiResponse<List<FaceRegisterDTO>> getAllFaces() {
        List<FaceRegisterDTO> list = faceService.getAllFaceFeatures();
        return ApiResponse.success(list);
    }

    /**
     * 4. 1:N 人脸特征检索比对（云端基于 Redis 缓存与余弦相似度匹配）
     */
    @PostMapping("/search")
    public ApiResponse<FaceMatchVO> searchFace(@RequestBody FaceSearchDTO dto) {
        FaceMatchVO match = faceService.searchFace(dto);
        return ApiResponse.success(match);
    }

    /**
     * 5. 查询指定学生人脸特征
     */
    @GetMapping("/{studentId}")
    public ApiResponse<FaceRegisterDTO> getFace(@PathVariable("studentId") String studentId) {
        FaceRegisterDTO dto = faceService.getFaceByStudentId(studentId);
        if (dto == null) {
            return ApiResponse.error(404, "Student face not found");
        }
        return ApiResponse.success(dto);
    }

    /**
     * 6. 删除学生人脸特征
     */
    @DeleteMapping("/{studentId}")
    public ApiResponse<Boolean> deleteFace(@PathVariable("studentId") String studentId) {
        boolean deleted = faceService.deleteFace(studentId);
        return ApiResponse.success("Face feature deleted", deleted);
    }

    /**
     * 7. 调起桌面端 Python face_register.py 摄像头录入进程
     */
    @PostMapping("/launch-register")
    public ApiResponse<String> launchCameraRegister(@RequestBody FaceRegisterDTO dto) {
        faceService.launchCameraRegister(dto.getStudentId(), dto.getName(), dto.getClassName());
        return ApiResponse.success("桌面端摄像头录入程序已启动，请正对摄像头按 S 键保存！", "OK");
    }
}
