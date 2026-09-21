package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleConflictService {
    private final CourseScheduleRepository schedules;
    private final CourseOfferingTeacherRepository teachers;

    public CourseOffering hydrate(CourseOffering offering) {
        offering.setTeachers(teachers.findByOfferingId(offering.getId()));
        return offering;
    }

    public Set<String> teacherCodes(CourseOffering offering) {
        Set<String> result = new HashSet<>();
        if (offering.getTeacherCode() != null && !offering.getTeacherCode().isBlank()) result.add(key(offering.getTeacherCode()));
        teachers.findByOfferingId(offering.getId()).forEach(t -> {
            if (t.getTeacherCode() != null && !t.getTeacherCode().isBlank()) result.add(key(t.getTeacherCode()));
        });
        return result;
    }

    public static String key(String text) { return text == null ? "" : text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }

    public static void validate(String room, Integer day, Integer sw, Integer ew, Integer sp, Integer ep) {
        if (room == null || room.isBlank() || room.trim().length() > 64 || day == null || day < 1 || day > 7
                || sw == null || ew == null || sw < 1 || ew < sw || ew > 53
                || sp == null || ep == null || sp < 1 || ep < sp || ep > 24)
            throw new IllegalArgumentException("教室、星期、周次(1–53)和节次(1–24)范围必须有效");
    }

    /** Called after acquiring the semester lock, in READ_COMMITTED transactions. */
    public List<CourseSchedule> find(CourseOffering candidate, Set<String> codes, String room, int day,
                                      int sw, int ew, int sp, int ep, Long excludeId) {
        List<CourseSchedule> result = new ArrayList<>();
        // Include this offering's rows even during a semester move: those rows move together.
        for (CourseSchedule row : schedules.findAll()) {
            CourseOffering other = row.getOffering();
            boolean sameOffering = Objects.equals(other.getId(), candidate.getId());
            if (!sameOffering && !key(candidate.getAcademicTerm()).equals(key(other.getAcademicTerm()))) continue;
            if (excludeId != null && excludeId.equals(row.getId())) continue;
            if (row.getDayOfWeek() != day || row.getStartWeek() > ew || row.getEndWeek() < sw
                    || row.getStartPeriod() > ep || row.getEndPeriod() < sp) continue;
            List<String> reasons = new ArrayList<>();
            if (key(room).equals(key(row.getClassroom()))) reasons.add("教室冲突");
            Set<String> shared = new TreeSet<>(sameOffering ? codes : teacherCodes(other));
            shared.retainAll(codes);
            if (!shared.isEmpty()) reasons.add("教师冲突（工号：" + String.join("、", shared) + "）");
            if (!reasons.isEmpty()) {
                hydrate(other);
                row.setConflictReasons(reasons);
                result.add(row);
            }
        }
        return result;
    }
}
