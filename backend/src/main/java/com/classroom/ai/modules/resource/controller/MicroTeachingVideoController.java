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
    private final com.classroom.ai.modules.sprint3.VideoAssetService assets;
    @PostMapping("/upload")
    public ApiResponse<MicroTeachingSlice> upload(@RequestParam Long courseId,@RequestParam String title,@RequestParam String stage,
      @RequestParam(required=false) Integer durationSeconds,@RequestParam MultipartFile file,
      @RequestParam(required=false) String sourceType,@RequestParam(required=false) String sourceDescription,
      @RequestParam(required=false) String sourceReference,@RequestParam(required=false) String authorizationDeclaration) throws IOException {
        com.classroom.ai.modules.sprint3.Sprint3Policy.stage(stage);
        var a=assets.upload(courseId,file,new com.classroom.ai.modules.sprint3.VideoAssetService.Source(sourceType,sourceDescription,sourceReference,authorizationDeclaration),stage);
        var d=new com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO();d.setCourseId(courseId);d.setVideoTitle(title);d.setBopppsStage(stage);d.setAssetId(a.getId());d.setStartMs(0L);d.setEndMs(a.getDurationMs());
        return ApiResponse.success("视频已验证并挂载",assets.mount(d));
    }
    @GetMapping("/{id}/video")
    public ResponseEntity<FileSystemResource> video(@PathVariable Long id) {return assets.video(id);}
}
