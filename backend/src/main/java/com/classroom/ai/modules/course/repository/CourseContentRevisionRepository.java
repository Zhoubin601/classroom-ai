package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseContentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseContentRevisionRepository extends JpaRepository<CourseContentRevision, Long> {
    List<CourseContentRevision> findByCourseIdOrderByVersionDesc(Long courseId);
    List<CourseContentRevision> findByCourseIdOrderByPublishVersionDesc(Long courseId);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByVersionDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByPublishVersionDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByIdDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdOrderByVersionDesc(Long courseId);
    Optional<CourseContentRevision> findFirstByCourseIdOrderByIdDesc(Long courseId);
}
