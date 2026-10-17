package com.classroom.ai.modules.resource;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.resource.dto.CourseResourceDTO;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.service.ResourceUploadPolicy;
import com.classroom.ai.modules.resource.service.impl.CourseResourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResourceUploadPolicyTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ResourceUploadPolicy.class)
            .withPropertyValues("spring.servlet.multipart.max-file-size=200MB", "spring.servlet.multipart.max-request-size=220MB");

    @Test void defaultPolicyIsOneHundredMiBAndOfficeFormats() {
        var policy = new ResourceUploadPolicy();
        assertEquals(104857600L, policy.current().maxFileBytes());
        assertEquals(List.of("ppt", "pptx", "doc", "docx", "pdf"), policy.current().allowedExtensions());
    }

    @Test void configuredIncreaseAcceptsExactBytesAndRejectsTheNextByte() {
        context.withPropertyValues("classroom.resource.max-file-bytes=157286400").run(application -> {
            var policy = application.getBean(ResourceUploadPolicy.class);
            assertEquals(157286400L, policy.current().maxFileBytes());
            assertDoesNotThrow(() -> policy.validateSize(157286400L));
            assertThrows(IllegalArgumentException.class, () -> policy.validateSize(157286401L));
        });
    }

    @Test void configuredDecreasePreservesBytePrecision() {
        context.withPropertyValues("classroom.resource.max-file-bytes=1024").run(application -> {
            var policy = application.getBean(ResourceUploadPolicy.class);
            assertEquals(1024L, policy.current().maxFileBytes());
            assertDoesNotThrow(() -> policy.validateSize(1024));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> policy.validateSize(1025)).getMessage().contains("1024 字节"));
            assertThrows(IllegalArgumentException.class, () -> policy.validateSize(-1));
        });
    }

    @Test void servletFileCeilingLimitsTheAdvertisedAndEnforcedBytes() {
        context.withPropertyValues("classroom.resource.max-file-bytes=314572800", "spring.servlet.multipart.max-file-size=1234B")
                .run(application -> {
                    var policy = application.getBean(ResourceUploadPolicy.class);
                    assertEquals(1234L, policy.current().maxFileBytes());
                    assertDoesNotThrow(() -> policy.validateSize(1234));
                    assertThrows(IllegalArgumentException.class, () -> policy.validateSize(1235));
                });
    }

    @Test void unlimitedServletCeilingStillHonorsResourceConfiguration() {
        context.withPropertyValues("classroom.resource.max-file-bytes=314572800", "spring.servlet.multipart.max-file-size=-1", "spring.servlet.multipart.max-request-size=-1")
                .run(application -> assertEquals(314572800L, application.getBean(ResourceUploadPolicy.class).current().maxFileBytes()));
    }

    @Test void smallerTotalRequestCeilingAlsoBoundsTheFilePolicy() {
        context.withPropertyValues("classroom.resource.max-file-bytes=104857600", "spring.servlet.multipart.max-request-size=2048B")
                .run(application -> {
                    var policy = application.getBean(ResourceUploadPolicy.class);
                    assertEquals(2048L, policy.current().maxFileBytes());
                    assertThrows(IllegalArgumentException.class, () -> policy.validateSize(2049));
                });
    }

    @Test void extensionConfigurationAndFilenamesUseTheSameNormalization() {
        context.withPropertyValues("classroom.resource.allowed-extensions= PDF , .PpTx , pdf, doc , DOC , ")
                .run(application -> {
                    var policy = application.getBean(ResourceUploadPolicy.class);
                    assertEquals(List.of("pdf", "pptx", "doc"), policy.current().allowedExtensions());
                    assertEquals("pdf", policy.validateExtension(policy.extensionOf("SYNTHETIC.PDF")));
                    assertEquals("doc", policy.validateExtension(" DOC "));
                    assertThrows(IllegalArgumentException.class, () -> policy.validateExtension("docx"));
                });
    }

    @Test void serviceEnforcesTheSharedPolicyForOriginalAndVersionUploads() {
        var policy = new ResourceUploadPolicy();
        ReflectionTestUtils.setField(policy, "configuredMaxFileBytes", 1024L);
        ReflectionTestUtils.setField(policy, "configuredExtensions", " PDF ");
        var resources = mock(CourseResourceRepository.class);
        var courses = mock(CourseRepository.class);
        var service = new CourseResourceServiceImpl(resources, courses);
        ReflectionTestUtils.setField(service, "uploadPolicy", policy);
        for (Long seriesId : new Long[]{null, 7L}) {
            assertThrows(IllegalArgumentException.class, () -> service.saveResource(dto(1025L, "sample.pdf", seriesId)));
            assertThrows(IllegalArgumentException.class, () -> service.saveResource(dto(1024L, "sample.docx", seriesId)));
        }
        verifyNoInteractions(resources, courses);
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).build()));
        when(resources.save(any(CourseResource.class))).thenAnswer(call -> call.getArgument(0));
        assertEquals(1024L, service.saveResource(dto(1024L, "SAMPLE.PDF", null)).getFileSizeBytes());
    }

    private CourseResourceDTO dto(long size, String filename, Long seriesId) {
        return CourseResourceDTO.builder().courseId(1L).seriesId(seriesId).chapter("Synthetic")
                .resourceName(filename).fileUrl("/uploads/resources/" + filename).fileSizeBytes(size).build();
    }
}
