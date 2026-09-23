package com.classroom.ai.modules.resource.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.entity.CourseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ResourceAccessService {
    private final CourseAuthorizationService authorization;

    public boolean canRead(CourseResource resource) {
        var user = authorization.requireCurrentUser();
        try {
            authorization.validateCourseRead(resource.getCourse());
            return true;
        } catch (ForbiddenException ex) {
            return Boolean.TRUE.equals(resource.getIsPublic())
                    && (user.getRole() == RoleEnum.TEACHER || user.getRole() == RoleEnum.DIRECTOR)
                    && user.getDepartment() != null && !user.getDepartment().isBlank()
                    && Objects.equals(user.getDepartment().trim(), resource.getCourse().getDepartment());
        }
    }

    public void requireRead(CourseResource resource) {
        if (!canRead(resource)) throw new ForbiddenException("无权访问该教学资源");
    }

    public void requireWrite(CourseResource resource) {
        authorization.validateCourseWrite(resource.getCourse());
    }

    public void requireCourseWrite(Long courseId) {
        authorization.validateCourseWrite(courseId);
    }
}
