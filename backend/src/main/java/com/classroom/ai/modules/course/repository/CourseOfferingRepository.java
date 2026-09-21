package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CourseOffering o WHERE o.id = :id")
    java.util.Optional<CourseOffering> findForUpdate(@Param("id") Long id);

    List<CourseOffering> findByAcademicTerm(String academicTerm);

    List<CourseOffering> findByTeacherName(String teacherName);

    List<CourseOffering> findByCourseId(Long courseId);

    List<CourseOffering> findByClassName(String className);

    @Query("SELECT o FROM CourseOffering o WHERE " +
           "(:term IS NULL OR o.academicTerm = :term) AND " +
           "(:teacher IS NULL OR o.teacherName LIKE %:teacher%) AND " +
           "(:courseKeyword IS NULL OR o.course.courseName LIKE %:courseKeyword% OR o.course.courseCode LIKE %:courseKeyword%)")
    List<CourseOffering> searchOfferings(@Param("term") String term, 
                                         @Param("teacher") String teacher, 
                                         @Param("courseKeyword") String courseKeyword);

    @Query("SELECT o FROM CourseOffering o WHERE " +
           "(:term IS NULL OR o.academicTerm = :term) AND " +
           "(:teacher IS NULL OR o.teacherName LIKE %:teacher% OR o.teacherCode = :teacher OR EXISTS (SELECT t.id FROM CourseOfferingTeacher t WHERE t.offeringId = o.id AND (t.teacherName LIKE %:teacher% OR t.teacherCode = :teacher))) AND " +
           "(:courseKeyword IS NULL OR o.course.courseName LIKE %:courseKeyword% OR o.course.courseCode LIKE %:courseKeyword%) AND " +
           "(:majorCode IS NULL OR o.majorCode = :majorCode OR o.course.majorCode = :majorCode)")
    List<CourseOffering> searchOfferingsWithMajor(
            @Param("term") String term,
            @Param("teacher") String teacher,
            @Param("courseKeyword") String courseKeyword,
            @Param("majorCode") String majorCode
    );


    @Query("SELECT COALESCE(SUM(o.studentCount), 0) FROM CourseOffering o WHERE o.teacherName = :teacherName")
    Long sumTotalStudentsByTeacher(@Param("teacherName") String teacherName);
    @Query("SELECT o FROM CourseOffering o WHERE " +
        "(:term IS NULL OR o.academicTerm = :term) AND " +
        "(:majorId IS NULL OR COALESCE(o.majorId, o.course.majorId) = :majorId) AND " +
        "(:majorCode IS NULL OR COALESCE(NULLIF(o.majorCode, ''), o.course.majorCode) = :majorCode) AND " +
        "(:keyword IS NULL OR o.course.courseName LIKE %:keyword% OR o.course.courseCode LIKE %:keyword%) AND " +
        "(:teacherId IS NULL OR EXISTS (SELECT t.id FROM CourseOfferingTeacher t WHERE t.offeringId = o.id AND t.teacherId = :teacherId)) AND " +
        "(:teacher IS NULL OR EXISTS (SELECT t.id FROM CourseOfferingTeacher t WHERE t.offeringId = o.id AND (t.teacherName LIKE %:teacher% OR t.teacherCode = :teacher))) ORDER BY o.academicTerm DESC, o.id DESC")
    List<CourseOffering> searchCombined(@Param("term") String term,@Param("teacherId") Long teacherId,@Param("teacher") String teacher,
        @Param("keyword") String keyword,@Param("majorId") Long majorId,@Param("majorCode") String majorCode);
}
