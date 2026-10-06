package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseSyllabus;
import com.classroom.ai.modules.course.entity.GraduationIndicator;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.CourseSyllabusRepository;
import com.classroom.ai.modules.course.repository.GraduationIndicatorRepository;
import com.classroom.ai.modules.course.repository.TrainingIndicatorRepository;
import com.classroom.ai.modules.course.entity.TrainingIndicator;
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TrainingIndicatorRepository trainingIndicatorRepository;

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
        CourseSyllabus latest = getLatestSyllabus(courseId);
        if (latest == null) return indicatorRepository.findByCourseId(courseId).stream()
                .filter(i -> i.getSyllabus() == null).toList();
        return indicatorRepository.findBySyllabusId(latest.getId());
    }

    @Override
    @Transactional
    public CourseSyllabus saveSyllabus(SyllabusDTO dto) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getCourseId() + " 的课程"));
        String requestedVersion = dto.getVersion() != null && !dto.getVersion().isBlank() ? dto.getVersion().trim() : null;
        String version = requestedVersion == null ? "2026版" : requestedVersion;

        CourseSyllabus syllabus;
        if (dto.getId() != null) {
            syllabus = syllabusRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到大纲ID " + dto.getId()));
            if (requestedVersion == null) version = syllabus.getVersion();
            if ("LOCKED".equals(syllabus.getStatus()) && (requestedVersion == null || requestedVersion.equals(syllabus.getVersion())))
                throw new IllegalStateException("已锁定的大纲不能修改，请创建新版本");
            if (syllabus.getCourse() == null || !syllabus.getCourse().getId().equals(course.getId()))
                throw new IllegalArgumentException("大纲与课程不匹配");
            if (requestedVersion != null && !requestedVersion.equals(syllabus.getVersion())) {
                if (syllabusRepository.findByCourseIdAndVersion(course.getId(), version).isPresent())
                    throw new IllegalArgumentException("大纲版本已存在");
                syllabus = new CourseSyllabus();
                syllabus.setCourse(course);
            }
        } else {
            if (syllabusRepository.findByCourseIdAndVersion(course.getId(), version).isPresent())
                throw new IllegalArgumentException("大纲版本已存在");
            syllabus = new CourseSyllabus();
            syllabus.setCourse(course);
        }

        syllabus.setVersion(version);
        String planVersion = dto.getPlanVersion() != null ? dto.getPlanVersion() : syllabus.getVersion();
        syllabus.setPlanVersion(planVersion);
        String status = dto.getStatus() != null ? dto.getStatus() : "SUBMITTED";
        if (!"DRAFT".equals(status) && !"SUBMITTED".equals(status))
            throw new IllegalArgumentException("大纲状态无效，请使用独立审核接口锁定");
        syllabus.setStatus(status);
        syllabus.setAuthorTeacher(dto.getAuthorTeacher());
        syllabus.setCourseGoals(dto.getCourseGoals());

        CourseSyllabus savedSyllabus = syllabusRepository.save(syllabus);

        // 处理指标点映射
        if (dto.getIndicators() != null) {
            if (trainingIndicatorRepository != null) {
                List<TrainingIndicator> catalog = trainingIndicatorRepository.findByMajorCodeAndPlanVersionOrderByIndicatorCode(
                        course.getMajorCode(), planVersion);
                if (catalog.isEmpty() && !dto.getIndicators().isEmpty())
                    throw new IllegalArgumentException("请先导入该专业及版本的培养方案指标目录");
                for (SyllabusDTO.IndicatorDTO item : dto.getIndicators()) {
                    if (catalog.stream().noneMatch(c -> c.getIndicatorCode().equals(item.getIndicatorCode())))
                        throw new IllegalArgumentException("指标点不属于当前培养方案: " + item.getIndicatorCode());
                }
            }
            indicatorRepository.deleteBySyllabusId(savedSyllabus.getId());
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

    @Override
    @Transactional
    public GraduationIndicator addIndicator(Long courseId, com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + courseId + " 的课程档案"));
        CourseSyllabus latestSyllabus = syllabusRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId).orElse(null);
        if (latestSyllabus == null) throw new IllegalStateException("请先从培养方案创建课程大纲版本");
        if (latestSyllabus != null && "LOCKED".equals(latestSyllabus.getStatus()))
            throw new IllegalStateException("已锁定的大纲不能修改，请创建新版本");
        if (trainingIndicatorRepository != null && latestSyllabus != null &&
                trainingIndicatorRepository.findByMajorCodeAndPlanVersionAndIndicatorCode(
                        course.getMajorCode(), latestSyllabus.getPlanVersion(), dto.getIndicatorCode()).isEmpty())
            throw new IllegalArgumentException("指标点不属于当前培养方案");

        GraduationIndicator indicator = GraduationIndicator.builder()
                .course(course)
                .syllabus(latestSyllabus)
                .indicatorCode(dto.getIndicatorCode())
                .requirementCategory(dto.getRequirementCategory())
                .indicatorDescription(dto.getIndicatorDescription())
                .supportWeight(dto.getSupportWeight() != null ? dto.getSupportWeight() : "M")
                .targetGoal(dto.getTargetGoal())
                .build();
        return indicatorRepository.save(indicator);
    }

    @Override
    @Transactional
    public GraduationIndicator updateIndicator(Long id, com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        GraduationIndicator indicator = indicatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的毕业要求指标点记录"));
        assertEditable(indicator);
        if (dto.getIndicatorCode() != null) {
            if (trainingIndicatorRepository != null && trainingIndicatorRepository.findByMajorCodeAndPlanVersionAndIndicatorCode(
                    indicator.getCourse().getMajorCode(), indicator.getSyllabus().getPlanVersion(), dto.getIndicatorCode()).isEmpty())
                throw new IllegalArgumentException("指标点不属于当前培养方案");
            indicator.setIndicatorCode(dto.getIndicatorCode());
        }
        if (dto.getRequirementCategory() != null) {
            indicator.setRequirementCategory(dto.getRequirementCategory());
        }
        if (dto.getIndicatorDescription() != null) {
            indicator.setIndicatorDescription(dto.getIndicatorDescription());
        }
        if (dto.getSupportWeight() != null) {
            indicator.setSupportWeight(dto.getSupportWeight());
        }
        if (dto.getTargetGoal() != null) {
            indicator.setTargetGoal(dto.getTargetGoal());
        }
        return indicatorRepository.save(indicator);
    }

    @Override
    @Transactional
    public void deleteIndicator(Long id) {
        GraduationIndicator indicator = indicatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的毕业要求指标点记录"));
        assertEditable(indicator);
        indicatorRepository.deleteById(id);
    }

    private void assertEditable(GraduationIndicator indicator) {
        CourseSyllabus latest = getLatestSyllabus(indicator.getCourse().getId());
        if (latest == null || indicator.getSyllabus() == null || !latest.getId().equals(indicator.getSyllabus().getId())
                || "LOCKED".equals(latest.getStatus()))
            throw new IllegalStateException("历史或已锁定的大纲指标点不可修改");
    }
}
