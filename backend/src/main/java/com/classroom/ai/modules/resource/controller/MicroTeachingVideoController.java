package com.classroom.ai.modules.resource.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.service.MicroTeachingVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/resources/micro-slices")
@RequiredArgsConstructor
public class MicroTeachingVideoController {
    private final MicroTeachingVideoService videos;
    @PostMapping("/upload")
    public ApiResponse<MicroTeachingSlice> upload(@RequestParam Long courseId, @RequestParam String title,
            @RequestParam String stage, @RequestParam Integer durationSeconds, @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.success("微格视频上传成功", videos.upload(courseId, title, stage, durationSeconds, file));
    }
    @GetMapping("/{id}/video")
    public ResponseEntity<FileSystemResource> video(@PathVariable Long id) throws IOException { return videos.video(id); }
}
