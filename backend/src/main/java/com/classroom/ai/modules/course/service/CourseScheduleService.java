package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.CourseSchedule;

import java.util.List;

public interface CourseScheduleService {
    List<CourseSchedule> getSchedulesByOfferingId(Long offeringId);
    List<CourseSchedule> getSchedulesByClassroom(String classroom);
    List<CourseSchedule> getAllSchedules();
    List<CourseSchedule> getFilteredSchedules(String term, String teacher, Integer week, String classroom, Long offeringId);
    CourseSchedule saveSchedule(CourseScheduleDTO dto);
    void deleteSchedule(Long id);
    
    /**
     * 检查排课冲突 (支持教室冲突与任一授课教师冲突联合检测)
     */
    List<CourseSchedule> checkConflict(Long offeringId, String classroom, Integer dayOfWeek, Integer startWeek, Integer endWeek, Integer startPeriod, Integer endPeriod, Long excludeId);

    /**
     * 保持向后兼容的方法签名
     */
    default List<CourseSchedule> checkConflict(String classroom, Integer dayOfWeek, Integer startWeek, Integer endWeek, Integer startPeriod, Integer endPeriod, Long excludeId) {
        return checkConflict(null, classroom, dayOfWeek, startWeek, endWeek, startPeriod, endPeriod, excludeId);
    }
}
