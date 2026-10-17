package com.classroom.ai.modules.resource.service.impl;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.service.CourseResourceService;
import com.classroom.ai.modules.resource.service.ResourceUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseResourceServiceImpl implements CourseResourceService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ResourceUploadPolicy uploadPolicy = new ResourceUploadPolicy();

    private final CourseResourceRepository resourceRepository;
    private final CourseRepository courseRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.classroom.ai.modules.sprint3.ResourceVersionService versions;
    @org.springframework.beans.factory.annotation.Autowired private com.classroom.ai.modules.resource.service.ResourceAccessService access;

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
        if (dto.getFileSizeBytes() != null) uploadPolicy.validateSize(dto.getFileSizeBytes());
        if (dto.getId() == null) {
            String extension = uploadPolicy.extensionOf(dto.getFileUrl());
            uploadPolicy.validateExtension(extension.isEmpty() ?
                    (dto.getFileType() == null ? "pdf" : dto.getFileType()) : extension);
        }

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到课程ID为 " + dto.getCourseId() + " 的记录"));
        if(access!=null)access.requireCourseWrite(course.getId());

        CourseResource resource;
        if (dto.getId() != null) {
            resource = getResourceById(dto.getId());
            if(!resource.getCourse().getId().equals(course.getId()))throw new IllegalArgumentException("不可改变资源课程");
            if(!java.util.Objects.equals(resource.getFileUrl(),dto.getFileUrl())||!java.util.Objects.equals(resource.getFileType(),dto.getFileType()))throw new IllegalArgumentException("历史版本文件不可修改，请上传新版本");
        } else {
            resource = new CourseResource();
            resource.setCourse(course);
        }

        resource.setChapter(dto.getChapter());
        resource.setResourceName(dto.getResourceName());
        resource.setFileType(dto.getFileType() != null ? dto.getFileType().toUpperCase(java.util.Locale.ROOT) : "PDF");
        resource.setFileUrl(dto.getFileUrl());
        resource.setFileSize(dto.getFileSize());
        resource.setFileSizeBytes(dto.getFileSizeBytes());
        resource.setTags(dto.getTags() != null ? dto.getTags() :
                (dto.getTag() == null || dto.getTag().isBlank() ? List.of() : List.of(dto.getTag())));
        resource.setTag(resource.getTags().isEmpty() ? "未标注" : resource.getTags().get(0));
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

        if (versions != null) {
            if (dto.getId() == null) return versions.attach(resource, dto.getSeriesId(), dto.getExpectedVersion());
            if (!versions.current(resource)) throw new IllegalStateException("历史版本只读，请先切换当前版本");
            versions.sharing(resource, resource.getIsPublic());
        }
        return resourceRepository.save(resource);
    }

    @org.springframework.beans.factory.annotation.Value("${classroom.upload-dir:}")
    private String uploadDir;

    @Override
    @Transactional
    public void deleteResource(Long id) {
        if (versions == null) throw new IllegalStateException("版本服务不可用");
        versions.remove(id);
    }
}
