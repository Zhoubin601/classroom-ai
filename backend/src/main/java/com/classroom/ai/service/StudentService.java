package com.classroom.ai.service;

import com.classroom.ai.dto.StudentDTO;
import com.classroom.ai.entity.Student;

import java.util.List;

public interface StudentService {

    List<Student> getAllStudents();

    Student getStudentByStudentId(String studentId);

    Student saveStudent(StudentDTO dto);

    boolean deleteStudent(String studentId);

    Student updateStudentClass(String studentId, String newClassName);
}
