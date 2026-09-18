package com.classroom.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.dto.FaceRegisterDTO;
import com.classroom.ai.dto.FaceSearchDTO;
import com.classroom.ai.entity.FaceFeature;
import com.classroom.ai.entity.Student;
import com.classroom.ai.repository.FaceFeatureRepository;
import com.classroom.ai.repository.StudentRepository;
import com.classroom.ai.service.FaceService;
import com.classroom.ai.vo.FaceMatchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceServiceImpl implements FaceService {

    private final StudentRepository studentRepository;
    private final FaceFeatureRepository faceFeatureRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_FACE_KEY_PREFIX = "face:feature:";
    private static final String REDIS_FACE_IDS_SET = "face:all_ids";

    @Value("${classroom.face.similarity-threshold:0.45}")
    private double defaultSimilarityThreshold;

    @Value("${classroom.face.default-dimension:512}")
    private int defaultDimension;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaceFeature registerFace(FaceRegisterDTO dto) {
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be empty");
        }
        validateVector(dto.getFeatureVector());

        // 1. 同步或创建学生基础信息
        Student student = studentRepository.findByStudentId(dto.getStudentId())
                .orElse(Student.builder()
                        .studentId(dto.getStudentId())
                        .name(dto.getName() != null ? dto.getName() : "Student_" + dto.getStudentId())
                        .className(dto.getClassName() != null ? dto.getClassName() : "Default Class")
                        .gender(dto.getGender() != null ? dto.getGender() : "UNKNOWN")
                        .avatarUrl(dto.getImagePath())
                        .build());

        if (dto.getName() != null) {
            student.setName(dto.getName());
        }
        if (dto.getGender() != null) student.setGender(dto.getGender());
        if (dto.getClassName() != null) {
            student.setClassName(dto.getClassName());
        }
        if (dto.getImagePath() != null) {
            student.setAvatarUrl(dto.getImagePath());
        }
        studentRepository.save(student);

        // 2. 保存或更新人脸特征到 MySQL
        String vectorJson = JSON.toJSONString(dto.getFeatureVector());
        FaceFeature feature = faceFeatureRepository.findByStudentId(dto.getStudentId())
                .orElse(FaceFeature.builder()
                        .studentId(dto.getStudentId())
                        .featureDim(dto.getFeatureVector().size())
                        .build());

        feature.setFeatureVector(vectorJson);
        feature.setFeatureDim(dto.getFeatureVector().size());
        feature.setImagePath(dto.getImagePath());
        FaceFeature savedFeature = faceFeatureRepository.save(feature);

        // 3. 更新 Redis 缓存
        try {
            String redisKey = REDIS_FACE_KEY_PREFIX + dto.getStudentId();
            FaceRegisterDTO cacheDto = FaceRegisterDTO.builder()
                    .studentId(dto.getStudentId())
                    .name(student.getName())
                    .className(student.getClassName())
                    .gender(student.getGender())
                    .featureVector(dto.getFeatureVector())
                    .imagePath(dto.getImagePath())
                    .build();

            stringRedisTemplate.opsForValue().set(redisKey, JSON.toJSONString(cacheDto));
            stringRedisTemplate.opsForSet().add(REDIS_FACE_IDS_SET, dto.getStudentId());
            log.info("Face feature successfully stored in MySQL & cached in Redis for student: {}", dto.getStudentId());
        } catch (Exception e) {
            log.error("Failed to write face feature to Redis for student: {}", dto.getStudentId(), e);
        }

        return savedFeature;
    }

    @Override
    public List<FaceRegisterDTO> getAllFaceFeatures() {
        List<FaceRegisterDTO> resultList = new ArrayList<>();
        int expectedCacheSize = 0;

        // 1. 尝试从 Redis 批量获取
        try {
            Set<String> studentIds = stringRedisTemplate.opsForSet().members(REDIS_FACE_IDS_SET);
            if (studentIds != null && !studentIds.isEmpty()) {
                expectedCacheSize = studentIds.size();
                List<String> keys = studentIds.stream()
                        .map(id -> REDIS_FACE_KEY_PREFIX + id)
                        .collect(Collectors.toList());

                List<String> cachedJsonList = stringRedisTemplate.opsForValue().multiGet(keys);
                if (cachedJsonList != null) {
                    for (String json : cachedJsonList) {
                        if (json != null && !json.isBlank()) {
                            resultList.add(JSON.parseObject(json, FaceRegisterDTO.class));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query all face features from Redis, falling back to MySQL: {}", e.getMessage());
        }

        // 2. 如果 Redis 命中且结果完整，直接返回
        if (expectedCacheSize > 0 && resultList.size() == expectedCacheSize) {
            return resultList;
        }
        resultList.clear();

        // 3. 回源 MySQL
        log.info("Cache miss or empty, loading face features from MySQL...");
        List<FaceFeature> allFeatures = faceFeatureRepository.findAll();
        Map<String, Student> studentMap = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getStudentId, s -> s, (s1, s2) -> s1));

        for (FaceFeature ff : allFeatures) {
            Student s = studentMap.get(ff.getStudentId());
            List<Float> vector = JSON.parseArray(ff.getFeatureVector(), Float.class);
            FaceRegisterDTO dto = FaceRegisterDTO.builder()
                    .studentId(ff.getStudentId())
                    .name(s != null ? s.getName() : "Unknown")
                    .className(s != null ? s.getClassName() : "")
                    .gender(s != null ? s.getGender() : "UNKNOWN")
                    .featureVector(vector)
                    .imagePath(ff.getImagePath())
                    .build();
            resultList.add(dto);

            // 回写 Redis 预热
            try {
                stringRedisTemplate.opsForValue().set(REDIS_FACE_KEY_PREFIX + ff.getStudentId(), JSON.toJSONString(dto));
                stringRedisTemplate.opsForSet().add(REDIS_FACE_IDS_SET, ff.getStudentId());
            } catch (Exception ignore) {
            }
        }

        return resultList;
    }

    @Override
    public FaceMatchVO searchFace(FaceSearchDTO dto) {
        validateVector(dto.getFeatureVector());
        if (dto.getThreshold() != null && (!Double.isFinite(dto.getThreshold()) || dto.getThreshold() < 0 || dto.getThreshold() > 1)) {
            throw new IllegalArgumentException("Similarity threshold must be between 0 and 1");
        }

        List<FaceRegisterDTO> allCandidates = getAllFaceFeatures();
        if (allCandidates.isEmpty()) {
            return FaceMatchVO.builder()
                    .similarity(0.0)
                    .matched(false)
                    .build();
        }

        double threshold = dto.getThreshold() != null ? dto.getThreshold() : defaultSimilarityThreshold;

        FaceRegisterDTO bestCandidate = null;
        double bestSimilarity = -1.0;

        List<Float> queryVector = dto.getFeatureVector();

        for (FaceRegisterDTO candidate : allCandidates) {
            try { validateVector(candidate.getFeatureVector()); }
            catch (IllegalArgumentException invalid) { continue; }
            double sim = calculateCosineSimilarity(queryVector, candidate.getFeatureVector());
            if (sim > bestSimilarity) {
                bestSimilarity = sim;
                bestCandidate = candidate;
            }
        }

        boolean isMatched = bestSimilarity >= threshold && bestCandidate != null;

        if (bestCandidate == null) {
            return FaceMatchVO.builder()
                    .similarity(0.0)
                    .matched(false)
                    .build();
        }

        return FaceMatchVO.builder()
                .studentId(bestCandidate.getStudentId())
                .name(bestCandidate.getName())
                .className(bestCandidate.getClassName())
                .avatarUrl(bestCandidate.getImagePath())
                .similarity(Math.round(bestSimilarity * 1000.0) / 1000.0)
                .matched(isMatched)
                .build();
    }

    @Override
    public FaceRegisterDTO getFaceByStudentId(String studentId) {
        // 先查 Redis
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_FACE_KEY_PREFIX + studentId);
            if (json != null && !json.isBlank()) {
                return JSON.parseObject(json, FaceRegisterDTO.class);
            }
        } catch (Exception e) {
            log.warn("Redis read error for studentId {}: {}", studentId, e.getMessage());
        }

        // 查 MySQL
        Optional<FaceFeature> featureOpt = faceFeatureRepository.findByStudentId(studentId);
        if (featureOpt.isEmpty()) {
            return null;
        }

        FaceFeature ff = featureOpt.get();
        Optional<Student> studentOpt = studentRepository.findByStudentId(studentId);

        List<Float> vector = JSON.parseArray(ff.getFeatureVector(), Float.class);
        return FaceRegisterDTO.builder()
                .studentId(studentId)
                .name(studentOpt.map(Student::getName).orElse("Unknown"))
                .className(studentOpt.map(Student::getClassName).orElse(""))
                .gender(studentOpt.map(Student::getGender).orElse("UNKNOWN"))
                .featureVector(vector)
                .imagePath(ff.getImagePath())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFace(String studentId) {
        faceFeatureRepository.deleteByStudentId(studentId);

        try {
            stringRedisTemplate.delete(REDIS_FACE_KEY_PREFIX + studentId);
            stringRedisTemplate.opsForSet().remove(REDIS_FACE_IDS_SET, studentId);
        } catch (Exception e) {
            log.error("Failed to remove student {} from Redis: {}", studentId, e.getMessage());
        }
        return true;
    }

    /**
     * 计算两个 512 维特征向量的余弦相似度
     */
    private double calculateCosineSimilarity(List<Float> vecA, List<Float> vecB) {
        if (vecA == null || vecB == null || vecA.size() != vecB.size() || vecA.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        int size = vecA.size();
        for (int i = 0; i < size; i++) {
            double a = vecA.get(i);
            double b = vecB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void validateVector(List<Float> vector) {
        if (vector == null || vector.size() != defaultDimension
                || vector.stream().anyMatch(v -> v == null || !Float.isFinite(v))
                || vector.stream().allMatch(v -> v == 0.0f)) {
            throw new IllegalArgumentException("Feature vector must contain " + defaultDimension + " finite values and have nonzero norm");
        }
    }

    @Override
    public boolean launchCameraRegister(String studentId, String name, String className) {
        try {
            java.nio.file.Path rootDir = java.nio.file.Paths.get("").toAbsolutePath().normalize();
            if (!java.nio.file.Files.isDirectory(rootDir.resolve("vision"))) {
                rootDir = rootDir.getParent();
            }
            if (rootDir == null || !java.nio.file.Files.isDirectory(rootDir.resolve("vision"))) {
                throw new IllegalStateException("Project vision directory not found; start the backend from the project or backend directory");
            }
            java.nio.file.Path pythonExe = rootDir.resolve(".venv1").resolve("Scripts").resolve("python.exe");
            java.nio.file.Path scriptFile = rootDir.resolve("vision").resolve("face_register.py");

            List<String> cmd = new ArrayList<>();
            cmd.add(pythonExe.toString());
            cmd.add(scriptFile.toString());
            cmd.add("--id");
            cmd.add(studentId != null && !studentId.isBlank() ? studentId : "STU2026001");
            cmd.add("--name");
            cmd.add(name != null && !name.isBlank() ? name : "张三");
            cmd.add("--class-name");
            cmd.add(className != null && !className.isBlank() ? className : "高一(1)班");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            if (scriptFile.getParent() != null) {
                pb.directory(scriptFile.getParent().toFile());
            }
            pb.start();
            log.info("Desktop camera register process started for {} ({})", name, studentId);
            return true;
        } catch (Exception e) {
            log.error("Failed to start camera register process", e);
            throw new RuntimeException("Failed to launch face_register.py: " + e.getMessage());
        }
    }
}
