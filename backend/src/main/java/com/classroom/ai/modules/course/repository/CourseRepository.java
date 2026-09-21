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

    Optional<Course> findByCourseCode(String courseCode);

    Optional<Course> findByCourseName(String courseName);

    boolean existsByCourseCode(String courseCode);

    List<Course> findByDepartment(String department);

    @Query("SELECT c FROM Course c WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR c.courseCode LIKE %:keyword% OR c.courseName LIKE %:keyword%) AND " +
           "(:courseType IS NULL OR :courseType = '' OR c.courseType = :courseType)")
    List<Course> searchCourses(@Param("keyword") String keyword, @Param("courseType") String courseType);
}
