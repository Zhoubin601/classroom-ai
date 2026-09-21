package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseContentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseContentRevisionRepository extends JpaRepository<CourseContentRevision, Long> {
    /** 保留历史异常重复草稿作为证据，发布后不能让旧草稿再次成为当前草稿。 */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE CourseContentRevision r SET r.status = 'SUPERSEDED' WHERE r.courseId = :courseId AND r.status = 'DRAFT' AND r.id <> :currentId")
    int retireOtherDrafts(@org.springframework.data.repository.query.Param("courseId") Long courseId,
                          @org.springframework.data.repository.query.Param("currentId") Long currentId);
    List<CourseContentRevision> findByCourseIdOrderByVersionDesc(Long courseId);
    List<CourseContentRevision> findByCourseIdOrderByPublishVersionDesc(Long courseId);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByVersionDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByPublishVersionDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdAndStatusOrderByIdDesc(Long courseId, String status);
    Optional<CourseContentRevision> findFirstByCourseIdOrderByVersionDesc(Long courseId);
    Optional<CourseContentRevision> findFirstByCourseIdOrderByIdDesc(Long courseId);
}
