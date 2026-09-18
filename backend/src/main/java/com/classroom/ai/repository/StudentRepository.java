package com.classroom.ai.repository;

import com.classroom.ai.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    void deleteByStudentId(String studentId);

    long countByClassName(String className);

    java.util.List<Student> findByClassName(String className);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s WHERE s.className IS NULL OR s.className != :className")
    java.util.List<Student> findAvailableStudentsForClass(@org.springframework.data.repository.query.Param("className") String className);
}
