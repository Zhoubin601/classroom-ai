package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseSyllabus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSyllabusRepository extends JpaRepository<CourseSyllabus, Long> {

    List<CourseSyllabus> findByCourseId(Long courseId);

    Optional<CourseSyllabus> findByCourseIdAndVersion(Long courseId, String version);

    Optional<CourseSyllabus> findFirstByCourseIdOrderByCreatedAtDesc(Long courseId);
}
