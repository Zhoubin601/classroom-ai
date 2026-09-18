package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseOfferingTeacherRepository extends JpaRepository<CourseOfferingTeacher, Long> {
    List<CourseOfferingTeacher> findByOfferingId(Long offeringId);
    List<CourseOfferingTeacher> findByTeacherId(Long teacherId);
    List<CourseOfferingTeacher> findByTeacherCode(String teacherCode);
    void deleteByOfferingId(Long offeringId);
}
