package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.SyllabusDTO;
import com.classroom.ai.modules.course.entity.CourseSyllabus;
import com.classroom.ai.modules.course.entity.GraduationIndicator;

import java.util.List;

public interface SyllabusService {
    List<CourseSyllabus> getSyllabusByCourseId(Long courseId);
    CourseSyllabus getLatestSyllabus(Long courseId);
    List<GraduationIndicator> getIndicatorsByCourseId(Long courseId);
    CourseSyllabus saveSyllabus(SyllabusDTO dto);
    CourseSyllabus lockSyllabus(Long syllabusId, String lockedBy);
}
