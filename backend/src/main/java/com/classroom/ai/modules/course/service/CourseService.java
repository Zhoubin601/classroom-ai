package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.CourseDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;

import java.util.List;

public interface CourseService {
    List<Course> getAllCourses();
    Course getCourseById(Long id);
    Course getCourseByCode(String code);
    Course saveCourse(CourseDTO dto);
    void deleteCourse(Long id);
    List<Course> searchCourses(String keyword, String courseType);
    
    // 开课管理与班级人员选调
    List<CourseOffering> getOfferingsByTerm(String term);
    List<CourseOffering> getOfferingsByTeacher(String teacher);
    List<CourseOffering> searchOfferings(String term, String teacher, String keyword);
    CourseOffering saveOffering(Long courseId, String term, String teacher, String className, Integer studentCount);
    
    // 课程班级选人入班与人员管理
    List<com.classroom.ai.entity.Student> getOfferingStudents(Long offeringId);
    List<com.classroom.ai.entity.Student> getAvailableStudentsForOffering(Long offeringId);
    CourseOffering addStudentsToOffering(Long offeringId, List<String> studentIds);
    CourseOffering removeStudentFromOffering(Long offeringId, String studentId);
}
