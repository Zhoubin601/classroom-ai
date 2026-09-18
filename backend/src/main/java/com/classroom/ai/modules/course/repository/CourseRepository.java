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

    Optional<Course> findByCourseCode(String courseCode);

    boolean existsByCourseCode(String courseCode);

    List<Course> findByDepartment(String department);

    @Query("SELECT c FROM Course c WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR c.courseCode LIKE %:keyword% OR c.courseName LIKE %:keyword%) AND " +
           "(:courseType IS NULL OR :courseType = '' OR c.courseType = :courseType)")
    List<Course> searchCourses(@Param("keyword") String keyword, @Param("courseType") String courseType);
}
