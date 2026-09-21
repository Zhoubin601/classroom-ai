package com.classroom.ai.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.classroom.ai.modules.course.service.ScheduleConflictException.class)
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> scheduleConflict(com.classroom.ai.modules.course.service.ScheduleConflictException error) {
        return ResponseEntity.status(409).body(new ApiResponse<>(409, error.getMessage(), java.util.Map.of("conflicts", error.getConflicts()), System.currentTimeMillis()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrityConflict(Exception error) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "数据缺少必填字段、重复或仍被其他记录引用"));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception error) {
        String message = error instanceof HttpMessageNotReadableException ? "请求格式不正确" : error.getMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }

    @ExceptionHandler({IllegalStateException.class, org.springframework.dao.OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(Exception error) {
        String message = error.getMessage() != null ? error.getMessage() : "检测到并发修改冲突，请刷新重试";
        return ResponseEntity.status(409).body(ApiResponse.error(409, message));
    }

    @ExceptionHandler(com.classroom.ai.common.exception.UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(com.classroom.ai.common.exception.UnauthorizedException error) {
        return ResponseEntity.status(401).body(ApiResponse.error(401, error.getMessage()));
    }

    @ExceptionHandler(com.classroom.ai.common.exception.ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(com.classroom.ai.common.exception.ForbiddenException error) {
        return ResponseEntity.status(403).body(ApiResponse.error(403, error.getMessage()));
    }
}
