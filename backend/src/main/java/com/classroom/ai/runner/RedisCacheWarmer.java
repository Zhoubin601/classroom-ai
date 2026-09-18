package com.classroom.ai.runner;

import com.classroom.ai.dto.FaceRegisterDTO;
import com.classroom.ai.service.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheWarmer implements ApplicationRunner {

    private final FaceService faceService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting Redis cache pre-warming for student face embeddings...");
        try {
            List<FaceRegisterDTO> list = faceService.getAllFaceFeatures();
            log.info("Redis cache warming completed! Loaded {} face feature vectors into Redis.", list.size());
        } catch (Exception e) {
            log.warn("Notice: Database or Redis connection not ready during initial cache warm-up ({}), system will warm up on first request.", e.getMessage());
        }
    }
}
