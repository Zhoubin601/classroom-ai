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

    @org.springframework.data.jpa.repository.Query("""
        select count(s) from CourseSyllabus s where s.course.majorCode = :majorCode
        and coalesce(s.planVersion, s.version) = :planVersion
        """)
    long countPlanReferences(@org.springframework.data.repository.query.Param("majorCode") String majorCode,
                             @org.springframework.data.repository.query.Param("planVersion") String planVersion);
}
