package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    List<CourseOffering> findByAcademicTerm(String academicTerm);

    List<CourseOffering> findByTeacherName(String teacherName);

    List<CourseOffering> findByCourseId(Long courseId);

    @Query("SELECT o FROM CourseOffering o WHERE " +
           "(:term IS NULL OR o.academicTerm = :term) AND " +
           "(:teacher IS NULL OR o.teacherName LIKE %:teacher%) AND " +
           "(:courseKeyword IS NULL OR o.course.courseName LIKE %:courseKeyword% OR o.course.courseCode LIKE %:courseKeyword%)")
    List<CourseOffering> searchOfferings(@Param("term") String term, 
                                         @Param("teacher") String teacher, 
                                         @Param("courseKeyword") String courseKeyword);

    @Query("SELECT COALESCE(SUM(o.studentCount), 0) FROM CourseOffering o WHERE o.teacherName = :teacherName")
    Long sumTotalStudentsByTeacher(@Param("teacherName") String teacherName);
}
