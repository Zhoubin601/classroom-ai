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
        var user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
        if (user == null) throw new com.classroom.ai.common.exception.UnauthorizedException("请先登录");
        var allowed = java.util.Arrays.stream(java.util.Optional.ofNullable(user.getAuthorizedMajors()).orElse("").split(";")).map(String::trim).map(String::toUpperCase).toList();
        return ApiResponse.success(majorRepository.findAll().stream().filter(m -> {
            if (user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.SUPERVISOR) return allowed.contains(m.getMajorCode().toUpperCase());
            if (user.getRole() == com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR) return user.getDepartment() != null && user.getDepartment().equals(m.getDepartment());
            return false;
        }).toList());
    }
}
