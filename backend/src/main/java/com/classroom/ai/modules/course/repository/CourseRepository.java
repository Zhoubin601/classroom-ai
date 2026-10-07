package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 课程内容变更串行到事务提交，跨应用实例也不会创建重复草稿/发布版本。 */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findForUpdate(@Param("id") Long id);

    /** Serializes dependency graph edits, including cross-major edges, in a stable order. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c ORDER BY c.id")
    List<Course> findAllForUpdate();

    Optional<Course> findByCourseCode(String courseCode);

    Optional<Course> findByCourseName(String courseName);
    List<Course> findAllByCourseName(String courseName);

    /** Every course-owned record whose meaning depends on its major. */
    @Query(value = """
        SELECT (SELECT COUNT(*) FROM t_course_offering WHERE course_id = :id)
             + (SELECT COUNT(*) FROM t_course_syllabus WHERE course_id = :id)
             + (SELECT COUNT(*) FROM t_graduation_indicator WHERE course_id = :id)
             + (SELECT COUNT(*) FROM t_course_resource WHERE course_id = :id)
             + (SELECT COUNT(*) FROM t_micro_teaching_slice WHERE course_id = :id)
             + (SELECT COUNT(*) FROM t_course_content_revision WHERE course_id = :id)
        """, nativeQuery = true)
    long countAssociatedRecords(@Param("id") Long id);

    boolean existsByCourseCode(String courseCode);

    List<Course> findByDepartment(String department);

    List<Course> findByMajorCode(String majorCode);

    @Query("SELECT c FROM Course c WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR c.courseCode LIKE %:keyword% OR c.courseName LIKE %:keyword%) AND " +
           "(:courseType IS NULL OR :courseType = '' OR c.courseType = :courseType)")
    List<Course> searchCourses(@Param("keyword") String keyword, @Param("courseType") String courseType);
}
