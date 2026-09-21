package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.CourseDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import com.classroom.ai.modules.course.service.CourseArchiveRules;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final com.classroom.ai.repository.StudentRepository studentRepository;
    private final com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository;
    private final com.classroom.ai.modules.course.repository.MajorRepository majorRepository;

    @Override
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @Override
    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + id + " 的课程档案"));
    }

    @Override
    public Course getCourseByCode(String code) {
        return courseRepository.findByCourseCode(code)
                .orElseThrow(() -> new IllegalArgumentException("未找到课程代码为 " + code + " 的课程档案"));
    }

    @Override
    @Transactional
    public Course saveCourse(CourseDTO dto) {
        var operator = CourseArchiveRules.requireDirector();
        if (dto.getCourseCode() == null || dto.getCourseCode().trim().isEmpty()) {
            throw new IllegalArgumentException("课程编码不能为空");
        }
        if (dto.getCourseName() == null || dto.getCourseName().trim().isEmpty()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        if (dto.getDepartment() == null || dto.getDepartment().trim().isEmpty()) {
            throw new IllegalArgumentException("开课教研室不能为空");
        }
        if (dto.getCourseType() == null || dto.getCourseType().trim().isEmpty()) {
            throw new IllegalArgumentException("课程性质不能为空");
        }
        CourseArchiveRules.validateDepartment(dto.getDepartment());
        CourseArchiveRules.validateCredits(dto.getCredits());
        int[] normalizedHours = CourseArchiveRules.normalizeHours(dto.getHours(), dto.getTheoryHours(), dto.getPracticeHours());
        int theory = normalizedHours[0];
        int practice = normalizedHours[1];
        var major = CourseArchiveRules.requireMajor(dto.getMajorCode(), majorRepository);
        CourseArchiveRules.validatePrerequisites(dto.getPrerequisites(), Set.of(), courseRepository);

        String cleanCode = dto.getCourseCode().trim();
        Course course;
        if (dto.getId() != null) {
            course = courseRepository.findForUpdate(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到课程"));
            CourseArchiveRules.validateDepartment(course.getDepartment());
            // 编码是外部引用键，已有档案不允许原地改码，避免字符串引用悬空。
            if (!cleanCode.equals(course.getCourseCode())) throw new IllegalArgumentException("已有课程编码不可修改，请保留原编码");
            courseRepository.findByCourseCode(cleanCode).ifPresent(other -> {
                if (!other.getId().equals(dto.getId())) {
                    throw new IllegalArgumentException("课程编码 " + cleanCode + " 已被其他课程占用，请勿重复使用");
                }
            });
        } else {
            if (courseRepository.existsByCourseCode(cleanCode)) {
                throw new IllegalArgumentException("课程编码 " + cleanCode + " 已存在，请勿重复创建");
            }
            course = new Course();
        }

        course.setCourseCode(cleanCode);
        course.setCourseName(dto.getCourseName().trim());
        course.setDepartment(dto.getDepartment().trim());
        course.setTeacherName(dto.getTeacherName() != null ? dto.getTeacherName().trim() : null);
        course.setCredits(dto.getCredits());
        course.setHours(dto.getHours());
        course.setTheoryHours(theory);
        course.setPracticeHours(practice);
        course.setCourseType(dto.getCourseType().trim());
        course.setPrerequisites(dto.getPrerequisites());
        if (dto.getId() == null) {
            course.setDescription(dto.getDescription());
            course.setObjectives(dto.getObjectives());
            course.setAssessmentMethod(dto.getAssessmentMethod());
        }

        course.setMajorCode(major.getMajorCode());
        course.setMajorId(major.getId());
        // 旧档案创建者未知时保持为空，不能把本次编辑人伪记为历史创建人。
        if (dto.getId() == null) course.setCreatedBy(operator.getUsername());
        course.setUpdatedBy(operator.getUsername());

        return courseRepository.save(course);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
        CourseArchiveRules.validateDepartment(getCourseById(id).getDepartment());
        courseRepository.deleteById(id);
    }

    @Override
    public List<Course> searchCourses(String keyword, String courseType) {
        return courseRepository.searchCourses(keyword, courseType);
    }

    @Override
    public List<CourseOffering> getOfferingsByTerm(String term) {
        return courseOfferingRepository.findByAcademicTerm(term);
    }

    @Override
    public List<CourseOffering> getOfferingsByTeacher(String teacher) {
        return courseOfferingRepository.findByTeacherName(teacher);
    }

    @Override
    public List<CourseOffering> searchOfferings(String term, String teacher, String keyword) {
        return courseOfferingRepository.searchOfferings(term, teacher, keyword);
    }


    @Override
    @Transactional
    public CourseOffering saveOffering(Long courseId, String term, String teacher, String className, Integer studentCount) {
        throw new IllegalArgumentException("请使用包含教师和选课名单的班次维护接口");
    }

    @Override
    public List<com.classroom.ai.entity.Student> getOfferingStudents(Long offeringId) {
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        List<com.classroom.ai.modules.course.entity.OfferingStudentEnrollment> enrollments = enrollmentRepository.findByOfferingId(offeringId);
        if (enrollments.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> studentNumbers = enrollments.stream()
                .map(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment::getStudentNumber)
                .toList();
        return studentRepository.findByStudentIdIn(studentNumbers);
    }

    @Override
    public List<com.classroom.ai.entity.Student> getAvailableStudentsForOffering(Long offeringId) {
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        List<com.classroom.ai.modules.course.entity.OfferingStudentEnrollment> enrollments = enrollmentRepository.findByOfferingId(offeringId);
        java.util.Set<String> enrolled = enrollments.stream()
                .map(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment::getStudentNumber)
                .collect(java.util.stream.Collectors.toSet());
        return studentRepository.findAll().stream()
                .filter(s -> !enrolled.contains(s.getStudentId()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public CourseOffering addStudentsToOffering(Long offeringId, List<String> studentIds) {
        CourseArchiveRules.requireDirector();
        CourseOffering offering = courseOfferingRepository.findForUpdate(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        CourseArchiveRules.validateDepartment(offering.getCourse().getDepartment());
        if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen())) throw new IllegalStateException("历史班次已冻结");
        if (studentIds != null && !studentIds.isEmpty()) {
            List<com.classroom.ai.modules.course.entity.OfferingStudentEnrollment> existingList = enrollmentRepository.findByOfferingId(offeringId);
            java.util.Set<String> existingNumbers = existingList.stream()
                    .map(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment::getStudentNumber)
                    .collect(java.util.stream.Collectors.toSet());
            for (String sid : studentIds) {
                if (!existingNumbers.contains(sid)) {
                    studentRepository.findByStudentId(sid).ifPresent(s -> {
                        enrollmentRepository.save(com.classroom.ai.modules.course.entity.OfferingStudentEnrollment.builder()
                                .offeringId(offeringId)
                                .studentId(s.getId())
                                .studentNumber(s.getStudentId())
                                .studentName(s.getName())
                                .adminClassName(s.getClassName())
                                .build());
                        existingNumbers.add(sid);
                    });
                }
            }
        }
        int realCount = (int) enrollmentRepository.countByOfferingId(offeringId);
        offering.setStudentCount(realCount);
        return courseOfferingRepository.save(offering);
    }

    @Override
    @Transactional
    public CourseOffering removeStudentFromOffering(Long offeringId, String studentId) {
        CourseArchiveRules.requireDirector();
        CourseOffering offering = courseOfferingRepository.findForUpdate(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        CourseArchiveRules.validateDepartment(offering.getCourse().getDepartment());
        if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen())) throw new IllegalStateException("历史班次已冻结");
        enrollmentRepository.deleteByOfferingIdAndStudentNumber(offeringId, studentId);
        int realCount = (int) enrollmentRepository.countByOfferingId(offeringId);
        offering.setStudentCount(realCount);
        return courseOfferingRepository.save(offering);
    }
}
