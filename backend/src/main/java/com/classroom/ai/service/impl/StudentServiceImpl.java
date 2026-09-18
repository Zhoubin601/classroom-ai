package com.classroom.ai.service.impl;

import com.classroom.ai.dto.StudentDTO;
import com.classroom.ai.entity.Student;
import com.classroom.ai.repository.StudentRepository;
import com.classroom.ai.service.FaceService;
import com.classroom.ai.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final FaceService faceService;
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository;

    @Override
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @Override
    public Student getStudentByStudentId(String studentId) {
        return studentRepository.findByStudentId(studentId).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Student saveStudent(StudentDTO dto) {
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be empty");
        }

        Student student = studentRepository.findByStudentId(dto.getStudentId())
                .orElse(Student.builder().studentId(dto.getStudentId()).build());

        String oldClass = student.getClassName();
        student.setName(dto.getName() != null ? dto.getName() : student.getName());
        student.setGender(dto.getGender() != null ? dto.getGender() : student.getGender());
        student.setClassName(dto.getClassName() != null ? dto.getClassName() : student.getClassName());
        student.setAvatarUrl(dto.getAvatarUrl() != null ? dto.getAvatarUrl() : student.getAvatarUrl());

        Student saved = studentRepository.save(student);
        syncClassStudentCount(oldClass);
        syncClassStudentCount(saved.getClassName());
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStudent(String studentId) {
        Student student = studentRepository.findByStudentId(studentId).orElse(null);
        String className = student != null ? student.getClassName() : null;

        faceService.deleteFace(studentId);
        studentRepository.deleteByStudentId(studentId);

        if (className != null) {
            syncClassStudentCount(className);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Student updateStudentClass(String studentId, String newClassName) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("未找到学号为 " + studentId + " 的学生档案"));
        String oldClass = student.getClassName();
        student.setClassName(newClassName);
        Student saved = studentRepository.save(student);

        syncClassStudentCount(oldClass);
        syncClassStudentCount(newClassName);
        return saved;
    }

    private void syncClassStudentCount(String className) {
        if (className == null || className.isBlank()) return;
        int count = (int) studentRepository.countByClassName(className);
        var offerings = courseOfferingRepository.findAll();
        for (var off : offerings) {
            if (className.equals(off.getClassName())) {
                off.setStudentCount(count);
                courseOfferingRepository.save(off);
            }
        }
    }
}
