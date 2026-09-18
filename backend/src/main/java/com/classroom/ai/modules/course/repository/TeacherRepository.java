package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByTeacherCode(String teacherCode);
    Optional<Teacher> findByTeacherName(String teacherName);
}
