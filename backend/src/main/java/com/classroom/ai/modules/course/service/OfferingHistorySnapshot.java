package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.vo.OfferingHistoryItemVO;
import java.util.List;

public final class OfferingHistorySnapshot {
    private OfferingHistorySnapshot() {}
    public static OfferingHistoryItemVO build(CourseOffering o, List<CourseOfferingTeacher> teachers, List<CourseSchedule> schedules, int count) {
        return OfferingHistoryItemVO.builder().offeringId(o.getId()).courseCode(o.getCourse().getCourseCode())
            .courseName(o.getCourse().getCourseName()).academicTerm(o.getAcademicTerm()).primaryTeacher(o.getTeacherName())
            .teachers(teachers.stream().map(CourseOfferingTeacher::getTeacherName).toList()).className(o.getClassName())
            .classroom(schedules.stream().map(CourseSchedule::getClassroom).distinct().sorted().collect(java.util.stream.Collectors.joining("、")))
            .studentCount(count).status(o.getStatus()).isSnapshotFrozen(Boolean.TRUE.equals(o.getIsSnapshotFrozen()))
            .archivedAt(o.getArchivedAt()).archivedBy(o.getArchivedBy()).build();
    }
}
