package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.CourseSchedule;

import java.util.List;

public interface CourseScheduleService {
    List<CourseSchedule> getSchedulesByOfferingId(Long offeringId);
    List<CourseSchedule> getSchedulesByClassroom(String classroom);
    List<CourseSchedule> getAllSchedules();
    CourseSchedule saveSchedule(CourseScheduleDTO dto);
    void deleteSchedule(Long id);
    
    /**
     * 检查排课冲突 (同一教室、同一星期几、周次重叠且节次重叠)
     */
    List<CourseSchedule> checkConflict(String classroom, Integer dayOfWeek, Integer startWeek, Integer endWeek, Integer startPeriod, Integer endPeriod, Long excludeId);
}
