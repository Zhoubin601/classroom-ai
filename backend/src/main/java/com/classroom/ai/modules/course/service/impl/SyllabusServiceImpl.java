package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseSyllabus;
import com.classroom.ai.modules.course.entity.GraduationIndicator;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.CourseSyllabusRepository;
import com.classroom.ai.modules.course.repository.GraduationIndicatorRepository;
import com.classroom.ai.modules.course.service.SyllabusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusService {

    private final CourseSyllabusRepository syllabusRepository;
    private final GraduationIndicatorRepository indicatorRepository;
    private final CourseRepository courseRepository;

    @Override
    public List<CourseSyllabus> getSyllabusByCourseId(Long courseId) {
        return syllabusRepository.findByCourseId(courseId);
    }

    @Override
    public CourseSyllabus getLatestSyllabus(Long courseId) {
        return syllabusRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId).orElse(null);
    }

    @Override
    public List<GraduationIndicator> getIndicatorsByCourseId(Long courseId) {
        return indicatorRepository.findByCourseId(courseId);
    }

    @Override
    @Transactional
    public CourseSyllabus saveSyllabus(SyllabusDTO dto) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getCourseId() + " 的课程"));

        CourseSyllabus syllabus;
        if (dto.getId() != null) {
            syllabus = syllabusRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到大纲ID " + dto.getId()));
            if ("LOCKED".equals(syllabus.getStatus())) {
                throw new IllegalStateException("已锁定的大纲不能修改，请创建新版本");
            }
        } else {
            syllabus = new CourseSyllabus();
            syllabus.setCourse(course);
        }

        syllabus.setVersion(dto.getVersion() != null ? dto.getVersion() : "2026版");
        syllabus.setStatus(dto.getStatus() != null ? dto.getStatus() : "SUBMITTED");
        syllabus.setAuthorTeacher(dto.getAuthorTeacher());
        syllabus.setCourseGoals(dto.getCourseGoals());

        CourseSyllabus savedSyllabus = syllabusRepository.save(syllabus);

        // 处理指标点映射
        if (dto.getIndicators() != null) {
            indicatorRepository.deleteByCourseId(course.getId());
            for (SyllabusDTO.IndicatorDTO indDto : dto.getIndicators()) {
                GraduationIndicator indicator = GraduationIndicator.builder()
                        .course(course)
                        .syllabus(savedSyllabus)
                        .indicatorCode(indDto.getIndicatorCode())
                        .requirementCategory(indDto.getRequirementCategory())
                        .indicatorDescription(indDto.getIndicatorDescription())
                        .supportWeight(indDto.getSupportWeight() != null ? indDto.getSupportWeight() : "M")
                        .targetGoal(indDto.getTargetGoal())
                        .build();
                indicatorRepository.save(indicator);
            }
        }

        return savedSyllabus;
    }

    @Override
    @Transactional
    public CourseSyllabus lockSyllabus(Long syllabusId, String lockedBy) {
        CourseSyllabus syllabus = syllabusRepository.findById(syllabusId)
                .orElseThrow(() -> new IllegalArgumentException("未找到大纲ID " + syllabusId));
        syllabus.setStatus("LOCKED");
        syllabus.setLockedBy(lockedBy);
        return syllabusRepository.save(syllabus);
    }
}
