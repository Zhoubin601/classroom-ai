package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.GraduationIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraduationIndicatorRepository extends JpaRepository<GraduationIndicator, Long> {

    List<GraduationIndicator> findByCourseId(Long courseId);

    List<GraduationIndicator> findBySyllabusId(Long syllabusId);

    void deleteBySyllabusId(Long syllabusId);

    void deleteByCourseId(Long courseId);
}
