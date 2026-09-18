package com.classroom.ai.modules.resource.service.impl;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.service.CourseResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseResourceServiceImpl implements CourseResourceService {

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024; // US-07: 单文件上限 100MB

    private final CourseResourceRepository resourceRepository;
    private final CourseRepository courseRepository;

    @Override
    public List<CourseResource> getResourcesByCourseId(Long courseId) {
        return resourceRepository.findByCourseId(courseId);
    }

    @Override
    public List<CourseResource> searchResources(Long courseId, String tag, Boolean isPublic, String keyword) {
        return resourceRepository.searchResources(courseId, tag, isPublic, keyword);
    }

    @Override
    public CourseResource getResourceById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的教学资源"));
    }

    @Override
    @Transactional
    public CourseResource saveResource(CourseResourceDTO dto) {
        if (dto.getFileSizeBytes() != null && dto.getFileSizeBytes() < 0) {
            throw new IllegalArgumentException("文件大小不能为负数");
        }
        // 1. 严格检查单文件大小上限 100MB (US-07)
        if (dto.getFileSizeBytes() != null && dto.getFileSizeBytes() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("上传失败：单文件大小不能超过 100MB 限制！当前文件大小: " +
                    (dto.getFileSizeBytes() / 1024 / 1024) + "MB");
        }

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到课程ID为 " + dto.getCourseId() + " 的记录"));

        CourseResource resource;
        if (dto.getId() != null) {
            resource = getResourceById(dto.getId());
        } else {
            resource = new CourseResource();
            resource.setCourse(course);
        }

        resource.setChapter(dto.getChapter());
        resource.setResourceName(dto.getResourceName());
        resource.setFileType(dto.getFileType() != null ? dto.getFileType().toUpperCase() : "PDF");
        resource.setFileUrl(dto.getFileUrl());
        resource.setFileSize(dto.getFileSize());
        resource.setFileSizeBytes(dto.getFileSizeBytes());
        resource.setTag(dto.getTag() != null ? dto.getTag() : "理论");
        resource.setVersion(dto.getVersion() != null ? dto.getVersion() : "v1.0");
        resource.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : true);
        resource.setUploaderTeacher(dto.getUploaderTeacher());

        // US-11: 自动生成防盗链动态水印
        if (dto.getDynamicWatermark() != null && !dto.getDynamicWatermark().isEmpty()) {
            resource.setDynamicWatermark(dto.getDynamicWatermark());
        } else {
            resource.setDynamicWatermark("东北大学软件学院 · 爱教学只读凭证 · " +
                    (dto.getUploaderTeacher() != null ? dto.getUploaderTeacher() : "系统"));
        }

        return resourceRepository.save(resource);
    }

    @Override
    @Transactional
    public void deleteResource(Long id) {
        resourceRepository.deleteById(id);
    }
}
