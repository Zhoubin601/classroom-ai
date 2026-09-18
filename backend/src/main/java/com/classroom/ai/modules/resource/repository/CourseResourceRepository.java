package com.classroom.ai.modules.resource.repository;

import com.classroom.ai.modules.resource.entity.CourseResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseResourceRepository extends JpaRepository<CourseResource, Long> {

    List<CourseResource> findByCourseId(Long courseId);

    List<CourseResource> findByCourseIdAndChapter(Long courseId, String chapter);

    @Query("SELECT r FROM CourseResource r WHERE " +
           "(:courseId IS NULL OR r.course.id = :courseId) AND " +
           "(:tag IS NULL OR :tag = '' OR r.tag = :tag) AND " +
           "(:isPublic IS NULL OR r.isPublic = :isPublic) AND " +
           "(:keyword IS NULL OR :keyword = '' OR r.resourceName LIKE %:keyword% OR r.chapter LIKE %:keyword%)")
    List<CourseResource> searchResources(@Param("courseId") Long courseId,
                                         @Param("tag") String tag,
                                         @Param("isPublic") Boolean isPublic,
                                         @Param("keyword") String keyword);
}
