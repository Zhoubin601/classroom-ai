package com.classroom.ai.modules.resource.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.service.MicroTeachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微格视频切片与课堂录像挂载接口 (US-12)
 */
@RestController
@RequestMapping("/api/v1/resources/micro-slices")
@RequiredArgsConstructor
@CrossOrigin
public class MicroTeachingController {

    private final MicroTeachingService microTeachingService;

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<MicroTeachingSlice>> getSlicesByCourseId(@PathVariable Long courseId) {
        return ApiResponse.success(microTeachingService.getSlicesByCourseId(courseId));
    }

    @GetMapping("/stage/{stage}")
    public ApiResponse<List<MicroTeachingSlice>> getSlicesByStage(@PathVariable String stage) {
        return ApiResponse.success(microTeachingService.getSlicesByStage(stage));
    }

    @PostMapping
    public ApiResponse<MicroTeachingSlice> mountSlice(@RequestBody MicroTeachingSliceDTO dto) {
        return ApiResponse.success("微格切片元数据挂载成功", microTeachingService.mountSlice(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSlice(@PathVariable Long id) {
        microTeachingService.deleteSlice(id);
        return ApiResponse.success("微格切片删除成功", null);
    }
}
