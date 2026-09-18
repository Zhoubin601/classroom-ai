package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.MajorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/majors")
@RequiredArgsConstructor
@CrossOrigin
public class MajorController {

    private final MajorRepository majorRepository;

    @GetMapping
    public ApiResponse<List<Major>> getAllMajors() {
        return ApiResponse.success(majorRepository.findAll());
    }
}
