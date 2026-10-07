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

import java.util.*;
import com.classroom.ai.modules.course.service.RecommendedIndicatorTemplate;

@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusService {

    private final CourseSyllabusRepository syllabusRepository;
    private final GraduationIndicatorRepository indicatorRepository;
    private final CourseRepository courseRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private TrainingIndicatorRepository trainingIndicatorRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.classroom.ai.modules.course.repository.MajorRepository majorRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseSyllabus saveSyllabus(SyllabusDTO dto) {
        Course course = lockCourse(dto.getCourseId());
        String requestedVersion = dto.getVersion() != null && !dto.getVersion().isBlank() ? dto.getVersion().trim() : null;
        String version = requestedVersion == null ? "2026版" : requestedVersion;

        CourseSyllabus syllabus;
        CourseSyllabus source = null;
        if (dto.getId() != null) {
            syllabus = syllabusRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到大纲ID " + dto.getId()));
            source = syllabus;
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
            } else {
                assertEditable(syllabus);
            }
        } else {
            if (syllabusRepository.findByCourseIdAndVersion(course.getId(), version).isPresent())
                throw new IllegalArgumentException("大纲版本已存在");
            syllabus = new CourseSyllabus();
            syllabus.setCourse(course);
        }

        String previousPlan = source == null ? null : source.getPlanVersion();
        String planVersion = dto.getPlanVersion() == null
                ? (previousPlan == null ? version : previousPlan) : dto.getPlanVersion().trim();
        if (planVersion.isBlank()) throw new IllegalArgumentException("培养方案目录版本不能为空");
        boolean changedPlan = source != null && !Objects.equals(planVersion, previousPlan);
        if (changedPlan && dto.getIndicators() == null)
            throw new IllegalArgumentException("更换目录版本时必须完整提供指标映射，请创建新大纲并重新绑定");
        List<SyllabusDTO.IndicatorDTO> requestedItems = dto.getIndicators();
        if (syllabus.getId() == null && source != null && requestedItems == null) {
            requestedItems = indicatorRepository.findBySyllabusId(source.getId()).stream().map(old ->
                    SyllabusDTO.IndicatorDTO.builder().indicatorCode(old.getIndicatorCode())
                        .requirementCategory(old.getRequirementCategory()).indicatorDescription(old.getIndicatorDescription())
                        .supportWeight(old.getSupportWeight()).targetGoal(old.getTargetGoal()).build()).toList();
        }
        if (syllabus.getId() == null || changedPlan) requireCatalog(course, planVersion);
        if (requestedItems != null) {
            var seen = new HashSet<String>();
            for (var item : requestedItems) {
                if (item == null) throw new IllegalArgumentException("指标映射不能为空");
                item.setIndicatorCode(cleanCode(item.getIndicatorCode()));
                item.setRequirementCategory(validateMapping(course, planVersion, item.getIndicatorCode(), item.getRequirementCategory(), item.getSupportWeight()));
                if (!seen.add(mappingKey(item.getIndicatorCode(), item.getTargetGoal())))
                    throw new IllegalArgumentException("同一指标与课程目标重复映射: " + item.getIndicatorCode());
            }
        }
        syllabus.setVersion(version);
        syllabus.setPlanVersion(planVersion);
        String status = dto.getStatus() != null ? dto.getStatus()
                : (syllabus.getId() == null ? "DRAFT"
                    : ("DRAFT".equals(syllabus.getStatus()) || "SUBMITTED".equals(syllabus.getStatus())) ? syllabus.getStatus() : "SUBMITTED");
        if (!"DRAFT".equals(status) && !"SUBMITTED".equals(status))
            throw new IllegalArgumentException("大纲状态无效，请使用独立审核接口锁定");
        syllabus.setStatus(status);
        syllabus.setAuthorTeacher(dto.getAuthorTeacher());
        if (dto.getCourseGoals() != null) syllabus.setCourseGoals(dto.getCourseGoals());
        else if (syllabus.getId() == null && source != null) syllabus.setCourseGoals(source.getCourseGoals());

        CourseSyllabus savedSyllabus = syllabusRepository.save(syllabus);

        // 处理指标点映射
        if (requestedItems != null) {
            indicatorRepository.deleteBySyllabusId(savedSyllabus.getId());
            for (SyllabusDTO.IndicatorDTO indDto : requestedItems) {
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseSyllabus lockSyllabus(Long syllabusId, String lockedBy) {
        CourseSyllabus syllabus = syllabusRepository.findById(syllabusId)
                .orElseThrow(() -> new IllegalArgumentException("未找到大纲ID " + syllabusId));
        lockCourse(syllabus.getCourse().getId());
        refresh(syllabus);
        if ("LOCKED".equals(syllabus.getStatus())) return syllabus;
        assertEditable(syllabus);
        syllabus.setStatus("LOCKED");
        syllabus.setLockedBy(lockedBy);
        return syllabusRepository.save(syllabus);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public GraduationIndicator addIndicator(Long courseId, com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        Course course = lockCourse(courseId);
        CourseSyllabus latestSyllabus = syllabusRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId).orElse(null);
        if (latestSyllabus == null) throw new IllegalStateException("请先从培养方案创建课程大纲版本");
        if (latestSyllabus != null && "LOCKED".equals(latestSyllabus.getStatus()))
            throw new IllegalStateException("已锁定的大纲不能修改，请创建新版本");
        String code = cleanCode(dto.getIndicatorCode());
        String category = validateMapping(course, latestSyllabus.getPlanVersion(), code, dto.getRequirementCategory(), dto.getSupportWeight());
        assertNoDuplicate(latestSyllabus.getId(), null, code, dto.getTargetGoal());

        GraduationIndicator indicator = GraduationIndicator.builder()
                .course(course)
                .syllabus(latestSyllabus)
                .indicatorCode(code)
                .requirementCategory(category)
                .indicatorDescription(dto.getIndicatorDescription())
                .supportWeight(dto.getSupportWeight() != null ? dto.getSupportWeight() : "M")
                .targetGoal(dto.getTargetGoal())
                .build();
        return indicatorRepository.save(indicator);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public GraduationIndicator updateIndicator(Long id, com.classroom.ai.modules.course.dto.IndicatorDTO dto) {
        GraduationIndicator indicator = indicatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的毕业要求指标点记录"));
        lockCourse(indicator.getCourse().getId());
        refresh(indicator); refresh(indicator.getSyllabus());
        assertEditable(indicator);
        String code = cleanCode(dto.getIndicatorCode() == null ? indicator.getIndicatorCode() : dto.getIndicatorCode());
        String category = validateMapping(indicator.getCourse(), indicator.getSyllabus().getPlanVersion(), code,
                dto.getRequirementCategory() == null ? indicator.getRequirementCategory() : dto.getRequirementCategory(), dto.getSupportWeight());
        String goal = dto.getTargetGoal() == null ? indicator.getTargetGoal() : dto.getTargetGoal();
        assertNoDuplicate(indicator.getSyllabus().getId(), id, code, goal);
        indicator.setIndicatorCode(code);
        indicator.setRequirementCategory(category);
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteIndicator(Long id) {
        GraduationIndicator indicator = indicatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的毕业要求指标点记录"));
        lockCourse(indicator.getCourse().getId());
        refresh(indicator); refresh(indicator.getSyllabus());
        assertEditable(indicator);
        indicatorRepository.deleteById(id);
    }

    private void validateWeight(String weight) {
        if (weight != null && !List.of("H", "M", "L").contains(weight))
            throw new IllegalArgumentException("支撑权重仅支持H、M、L");
    }

    private void assertEditable(GraduationIndicator indicator) {
        if (indicator.getSyllabus() == null) throw new IllegalStateException("历史无大纲指标不可修改，请创建新版本");
        assertEditable(indicator.getSyllabus());
    }

    private void assertEditable(CourseSyllabus syllabus) {
        CourseSyllabus latest = getLatestSyllabus(syllabus.getCourse().getId());
        if (latest == null || !Objects.equals(latest.getId(), syllabus.getId()) || "LOCKED".equals(syllabus.getStatus()))
            throw new IllegalStateException("历史或已锁定的大纲不可修改，请创建新版本");
    }

    private Course lockCourse(Long id) {
        Course course = courseRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        refresh(course);
        if (majorRepository != null)
            majorRepository.findForUpdate(course.getMajorCode()).orElseThrow(() -> new IllegalArgumentException("课程专业不存在"));
        return course;
    }

    private void refresh(Object entity) {
        // Refresh entities already loaded by controller authorization before the write lock.
        if (entityManager != null && entity != null) {
            // Preserve earlier valid changes in an enclosing transaction before reloading.
            entityManager.flush();
            entityManager.refresh(entity);
        }
    }

    private List<TrainingIndicator> requireCatalog(Course course, String plan) {
        List<TrainingIndicator> catalog = RecommendedIndicatorTemplate.VERSION.equals(plan)
                ? RecommendedIndicatorTemplate.items(course.getMajorCode())
                : trainingIndicatorRepository == null ? List.of()
                : trainingIndicatorRepository.findByMajorCodeAndPlanVersionOrderByIndicatorCode(course.getMajorCode(), plan);
        if (catalog.isEmpty()) throw new IllegalArgumentException("请先导入该专业及版本的培养方案指标目录");
        return catalog;
    }

    private String validateMapping(Course course, String plan, String code, String category, String weight) {
        validateWeight(weight);
        String expected = RecommendedIndicatorTemplate.VERSION.equals(plan) ? RecommendedIndicatorTemplate.categoryFor(code)
                : requireCatalog(course, plan).stream().filter(item -> item.getIndicatorCode().equals(code))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("指标点不属于当前培养方案: " + code)).getRequirementCategory();
        if (category == null || !expected.equals(category.trim()))
            throw new IllegalArgumentException("指标编号与毕业要求大项不匹配: " + code + "应属于" + expected);
        return expected;
    }

    private String cleanCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("指标编号不能为空");
        return code.trim();
    }

    private String mappingKey(String code, String goal) {
        String targets = goal == null ? "" : Arrays.stream(goal.split("[,，;；、]"))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        return cleanCode(code) + "\u0000" + targets;
    }

    private void assertNoDuplicate(Long syllabusId, Long excluded, String code, String goal) {
        if (indicatorRepository.findBySyllabusId(syllabusId).stream().anyMatch(item ->
                !Objects.equals(excluded, item.getId()) && mappingKey(code, goal).equals(mappingKey(item.getIndicatorCode(), item.getTargetGoal()))))
            throw new IllegalArgumentException("同一指标与课程目标重复映射: " + code);
    }

}
