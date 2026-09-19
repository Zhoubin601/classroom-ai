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

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final com.classroom.ai.repository.StudentRepository studentRepository;
    private final com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository;

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
        Course course;
        if (dto.getId() != null) {
            course = getCourseById(dto.getId());
        } else {
            if (courseRepository.existsByCourseCode(dto.getCourseCode())) {
                throw new IllegalArgumentException("课程编码 " + dto.getCourseCode() + " 已存在，请勿重复创建");
            }
            course = new Course();
        }

        course.setCourseCode(dto.getCourseCode());
        course.setCourseName(dto.getCourseName());
        course.setDepartment(dto.getDepartment());
        course.setTeacherName(dto.getTeacherName());
        course.setCredits(dto.getCredits());
        course.setHours(dto.getHours());
        course.setTheoryHours(dto.getTheoryHours() != null ? dto.getTheoryHours() : dto.getHours());
        course.setPracticeHours(dto.getPracticeHours() != null ? dto.getPracticeHours() : 0);
        course.setCourseType(dto.getCourseType());
        course.setPrerequisites(dto.getPrerequisites());
        course.setDescription(dto.getDescription());
        course.setObjectives(dto.getObjectives());
        course.setAssessmentMethod(dto.getAssessmentMethod());

        return courseRepository.save(course);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
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
        Course course = getCourseById(courseId);
        int realCount = studentCount != null ? studentCount : (int) studentRepository.countByClassName(className);
        CourseOffering offering = CourseOffering.builder()
                .course(course)
                .academicTerm(term)
                .teacherName(teacher)
                .className(className)
                .studentCount(realCount)
                .status("IN_PROGRESS")
                .build();
        return courseOfferingRepository.save(offering);
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
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
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
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        enrollmentRepository.deleteByOfferingIdAndStudentNumber(offeringId, studentId);
        int realCount = (int) enrollmentRepository.countByOfferingId(offeringId);
        offering.setStudentCount(realCount);
        return courseOfferingRepository.save(offering);
    }
}
