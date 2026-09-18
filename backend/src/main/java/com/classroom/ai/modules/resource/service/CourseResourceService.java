package com.classroom.ai.modules.resource.service;

import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;

import java.util.List;

public interface CourseResourceService {
    List<CourseResource> getResourcesByCourseId(Long courseId);
    List<CourseResource> searchResources(Long courseId, String tag, Boolean isPublic, String keyword);
    CourseResource getResourceById(Long id);
    CourseResource saveResource(CourseResourceDTO dto);
    void deleteResource(Long id);
}
