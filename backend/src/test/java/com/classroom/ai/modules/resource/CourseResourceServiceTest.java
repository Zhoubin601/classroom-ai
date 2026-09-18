package com.classroom.ai.modules.resource;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.service.impl.CourseResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseResourceServiceTest {

    @Mock
    private CourseResourceRepository resourceRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseResourceServiceImpl resourceService;

    private Course mockCourse;

    @BeforeEach
    void setUp() {
        mockCourse = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
    }

    @Test
    @DisplayName("资源上传大小限制：文件超过 100MB (104,857,600 字节) 必须拦截并抛出异常")
    void testUploadSizeLimit_Over100MB_ShouldThrowException() {
        long overSize = 105L * 1024 * 1024; // 105 MB
        CourseResourceDTO dto = CourseResourceDTO.builder()
                .courseId(1L)
                .resourceName("超大视频课件.mp4")
                .fileSizeBytes(overSize)
                .chapter("第一章")
                .tag("理论")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            resourceService.saveResource(dto);
        });

        assertTrue(ex.getMessage().contains("单文件大小不能超过 100MB 限制"));
        verify(resourceRepository, never()).save(any());
    }

    @Test
    @DisplayName("合规上传并自动生成动态防盗链水印")
    void testUploadValidResource_ShouldGenerateWatermark() {
        long validSize = 15L * 1024 * 1024; // 15 MB
        CourseResourceDTO dto = CourseResourceDTO.builder()
                .courseId(1L)
                .resourceName("软件项目管理第1讲.pptx")
                .fileSizeBytes(validSize)
                .fileUrl("https://static.neu.edu.cn/cs3001/1.pptx")
                .chapter("第一章")
                .tag("理论")
                .uploaderTeacher("郭军")
                .build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(mockCourse));
        when(resourceRepository.save(any(CourseResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseResource saved = resourceService.saveResource(dto);
        assertNotNull(saved);
        assertNotNull(saved.getDynamicWatermark());
        assertTrue(saved.getDynamicWatermark().contains("东北大学软件学院"));
        assertTrue(saved.getDynamicWatermark().contains("郭军"));
    }
}
