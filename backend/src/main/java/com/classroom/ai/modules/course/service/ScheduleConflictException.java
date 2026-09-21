package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.entity.CourseSchedule;
import java.util.List;

public class ScheduleConflictException extends IllegalStateException {
    private final List<CourseSchedule> conflicts;
    public ScheduleConflictException(List<CourseSchedule> conflicts) {
        super("发现 " + conflicts.size() + " 条排课冲突，请调整教师、教室或时间");
        this.conflicts = List.copyOf(conflicts);
    }
    public List<CourseSchedule> getConflicts() { return conflicts; }
}
