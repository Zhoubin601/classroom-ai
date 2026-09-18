package com.classroom.ai.modules.resource.service.impl;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseRepository;
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

    @Override
    public List<MicroTeachingSlice> getSlicesByCourseId(Long courseId) {
        return sliceRepository.findByCourseId(courseId);
    }

    @Override
    public List<MicroTeachingSlice> getSlicesByStage(String stage) {
        return sliceRepository.findByBopppsStage(stage);
    }

    @Override
    @Transactional
    public MicroTeachingSlice mountSlice(MicroTeachingSliceDTO dto) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到课程ID为 " + dto.getCourseId() + " 的记录"));

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
        sliceRepository.deleteById(id);
    }
}
