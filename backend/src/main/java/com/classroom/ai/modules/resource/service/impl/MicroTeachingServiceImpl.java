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
    private final com.classroom.ai.modules.sprint3.VideoAssetService assets;

    @Override
    @Transactional(readOnly = true)
    public List<MicroTeachingSlice> getSlicesByCourseId(Long courseId) {
        authorizationService.validateCourseRead(courseId);
        return sliceRepository.findByCourseId(courseId).stream().map(assets::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroTeachingSlice> getSlicesByStage(String stage) {
        authorizationService.requireCurrentUser();
        List<Long> courseIds = authorizationService.filterCourses(courseRepository.findAll())
                .stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) return List.of();
        return sliceRepository.findByBopppsStageAndCourse_IdIn(stage, courseIds).stream().map(assets::view).toList();
    }

    @Override
    @Transactional
    public MicroTeachingSlice mountSlice(MicroTeachingSliceDTO dto) {
        authorizationService.requireCurrentUser();
        if(dto==null||dto.getCourseId()==null)throw new IllegalArgumentException("请指定课程");
        authorizationService.validateCourseWrite(dto.getCourseId());
        return assets.mount(dto);
    }

    @Override
    @Transactional
    public void deleteSlice(Long id) {
        var slice=sliceRepository.findById(id).orElseThrow(()->new IllegalArgumentException("片段不存在"));
        authorizationService.validateCourseWrite(slice.getCourse());
        assets.remove(id);
    }
}
