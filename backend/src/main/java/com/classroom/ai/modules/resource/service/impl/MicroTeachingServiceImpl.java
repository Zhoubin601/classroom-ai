package com.classroom.ai.modules.resource.service.impl;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.repository.MicroTeachingSliceRepository;
import com.classroom.ai.modules.resource.service.MicroTeachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MicroTeachingServiceImpl implements MicroTeachingService {

    private final MicroTeachingSliceRepository sliceRepository;
    private final CourseRepository courseRepository;
    private final CourseAuthorizationService authorizationService;

    @Override
    @Transactional(readOnly = true)
    public List<MicroTeachingSlice> getSlicesByCourseId(Long courseId) {
        authorizationService.validateCourseRead(courseId);
        return sliceRepository.findByCourseId(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroTeachingSlice> getSlicesByStage(String stage) {
        authorizationService.requireCurrentUser();
        List<Long> courseIds = authorizationService.filterCourses(courseRepository.findAll())
                .stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) return List.of();
        return sliceRepository.findByBopppsStageAndCourse_IdIn(stage, courseIds);
    }

    @Override
    @Transactional
    public MicroTeachingSlice mountSlice(MicroTeachingSliceDTO dto) {
        authorizationService.requireCurrentUser();
        if (dto == null || dto.getCourseId() == null) {
            throw new IllegalArgumentException("挂载微格切片必须指定课程ID");
        }
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到课程ID为 " + dto.getCourseId() + " 的记录"));
        authorizationService.validateCourseWrite(course);
        if (dto.getSliceUrl() != null && dto.getSliceUrl().startsWith("/uploads/micro/"))
            throw new IllegalArgumentException("平台视频请使用上传接口，不能重新挂载受控文件路径");

        MicroTeachingSlice slice = MicroTeachingSlice.builder()
                .course(course)
                .videoTitle(dto.getVideoTitle())
                .bopppsStage(dto.getBopppsStage() != null ? dto.getBopppsStage() : "P (参与式学习)")
                .durationSeconds(dto.getDurationSeconds() != null ? dto.getDurationSeconds() : 300)
                .sliceUrl(dto.getSliceUrl())
                .coverUrl(dto.getCoverUrl())
                .recordedDate(dto.getRecordedDate())
                .classroom(dto.getClassroom())
                .sourceAgent(dto.getSourceAgent() != null ? dto.getSourceAgent() : "Agent-Edge")
                .build();

        return sliceRepository.save(slice);
    }

    @Override
    @Transactional
    public void deleteSlice(Long id) {
        authorizationService.requireCurrentUser();
        MicroTeachingSlice slice = sliceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到微格切片ID为 " + id + " 的记录"));
        authorizationService.validateCourseWrite(slice.getCourse());
        sliceRepository.delete(slice);
    }
}
