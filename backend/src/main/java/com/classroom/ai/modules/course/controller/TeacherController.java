package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.entity.Teacher;
import com.classroom.ai.modules.course.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@CrossOrigin
public class TeacherController {

    private final TeacherRepository teacherRepository;

    @GetMapping
    public ApiResponse<List<Teacher>> getAllTeachers() {
        return ApiResponse.success(teacherRepository.findAll());
    }
}
