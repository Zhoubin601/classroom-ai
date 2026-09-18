package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import com.classroom.ai.modules.course.service.CourseScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseScheduleServiceImpl implements CourseScheduleService {

    private final CourseScheduleRepository scheduleRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CourseOfferingTeacherRepository offeringTeacherRepository;

    @Override
    public List<CourseSchedule> getSchedulesByOfferingId(Long offeringId) {
        return scheduleRepository.findByOfferingId(offeringId);
    }

    @Override
    public List<CourseSchedule> getSchedulesByClassroom(String classroom) {
        return scheduleRepository.findByClassroom(classroom);
    }

    @Override
    public List<CourseSchedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }

    @Override
    public List<CourseSchedule> getFilteredSchedules(String term, String teacher, Integer week, String classroom, Long offeringId) {
        List<CourseSchedule> all = scheduleRepository.findAll();
        return all.stream()
                .filter(s -> offeringId == null || (s.getOffering() != null && offeringId.equals(s.getOffering().getId())))
                .filter(s -> classroom == null || classroom.trim().isEmpty() || classroom.trim().equalsIgnoreCase(s.getClassroom()))
                .filter(s -> term == null || term.trim().isEmpty() || (s.getOffering() != null && term.trim().equalsIgnoreCase(s.getOffering().getAcademicTerm())))
                .filter(s -> {
                    if (teacher == null || teacher.trim().isEmpty()) return true;
                    String q = teacher.trim();
                    if (s.getOffering() == null) return false;
                    if (q.equalsIgnoreCase(s.getOffering().getTeacherName()) || q.equalsIgnoreCase(s.getOffering().getTeacherCode())) {
                        return true;
                    }
                    // 查联合开课教师
                    List<CourseOfferingTeacher> otList = offeringTeacherRepository.findByOfferingId(s.getOffering().getId());
                    return otList.stream().anyMatch(ot -> q.equalsIgnoreCase(ot.getTeacherName()) || q.equalsIgnoreCase(ot.getTeacherCode()));
                })
                .filter(s -> {
                    if (week == null) return true;
                    int start = s.getStartWeek() != null ? s.getStartWeek() : 1;
                    int end = s.getEndWeek() != null ? s.getEndWeek() : 16;
                    return week >= start && week <= end;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CourseSchedule saveSchedule(CourseScheduleDTO dto) {
        dto.setStartWeek(dto.getStartWeek() != null ? dto.getStartWeek() : 1);
        dto.setEndWeek(dto.getEndWeek() != null ? dto.getEndWeek() : 16);

        CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getOfferingId() + " 的开课班次"));

        String term = offering.getAcademicTerm() != null ? offering.getAcademicTerm() : "DEFAULT_TERM";

        // 按学期串行执行冲突校验与写入 (避免并发排课双通过)
        synchronized (term.intern()) {
            List<CourseSchedule> conflicts = checkConflict(
                    dto.getOfferingId(),
                    dto.getClassroom(),
                    dto.getDayOfWeek(),
                    dto.getStartWeek(),
                    dto.getEndWeek(),
                    dto.getStartPeriod(),
                    dto.getEndPeriod(),
                    dto.getId()
            );

            if (!conflicts.isEmpty()) {
                CourseSchedule conflict = conflicts.get(0);
                String courseName = (conflict.getOffering() != null && conflict.getOffering().getCourse() != null)
                        ? conflict.getOffering().getCourse().getCourseName() : "其他课程";
                String teacherName = conflict.getOffering() != null ? conflict.getOffering().getTeacherName() : "相关教师";

                boolean isClassroomConflict = dto.getClassroom().equalsIgnoreCase(conflict.getClassroom());
                if (isClassroomConflict) {
                    throw new IllegalStateException(String.format("排课冲突拦截：教室【%s】在星期%d 第%d-%d节已被【%s】占用！",
                            dto.getClassroom(), dto.getDayOfWeek(), dto.getStartPeriod(), dto.getEndPeriod(), courseName));
                } else {
                    throw new IllegalStateException(String.format("排课冲突拦截：任课教师【%s】在同一时段（星期%d 第%d-%d节）已安排在【%s】讲授【%s】！",
                            teacherName, dto.getDayOfWeek(), dto.getStartPeriod(), dto.getEndPeriod(),
                            conflict.getClassroom(), courseName));
                }
            }

            CourseSchedule schedule;
            if (dto.getId() != null) {
                schedule = scheduleRepository.findById(dto.getId())
                        .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getId() + " 的排课记录"));
            } else {
                schedule = new CourseSchedule();
                schedule.setOffering(offering);
            }

            schedule.setClassroom(dto.getClassroom().trim());
            schedule.setWeekRange(dto.getWeekRange() != null ? dto.getWeekRange() : String.format("%d-%d周", dto.getStartWeek(), dto.getEndWeek()));
            schedule.setStartWeek(dto.getStartWeek());
            schedule.setEndWeek(dto.getEndWeek());
            schedule.setDayOfWeek(dto.getDayOfWeek());
            schedule.setStartPeriod(dto.getStartPeriod());
            schedule.setEndPeriod(dto.getEndPeriod());

            return scheduleRepository.save(schedule);
        }
    }

    @Override
    @Transactional
    public void deleteSchedule(Long id) {
        scheduleRepository.deleteById(id);
    }

    @Override
    public List<CourseSchedule> checkConflict(Long offeringId, String classroom, Integer dayOfWeek, Integer startWeek,
                                             Integer endWeek, Integer startPeriod, Integer endPeriod, Long excludeId) {
        int sWeek = startWeek != null ? startWeek : 1;
        int eWeek = endWeek != null ? endWeek : 16;
        if (classroom == null || classroom.isBlank() || dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7
                || sWeek < 1 || eWeek < sWeek || startPeriod == null || endPeriod == null
                || startPeriod < 1 || endPeriod < startPeriod) {
            throw new IllegalArgumentException("教室、星期、周次和节次范围必须有效");
        }

        CourseOffering currentOffering = offeringId != null ? offeringRepository.findById(offeringId).orElse(null) : null;
        String academicTerm = currentOffering != null ? currentOffering.getAcademicTerm() : null;

        // 1. 检测教室在同学期的时段冲突
        List<CourseSchedule> classroomConflicts = scheduleRepository.findConflictingClassroomSchedules(
                academicTerm, classroom.trim(), dayOfWeek, sWeek, eWeek, startPeriod, endPeriod, excludeId);

        Set<Long> conflictIds = new HashSet<>();
        List<CourseSchedule> results = new ArrayList<>();

        for (CourseSchedule cs : classroomConflicts) {
            if (conflictIds.add(cs.getId())) {
                results.add(cs);
            }
        }

        // 2. 检测授课教师在同学期的时段冲突
        if (currentOffering != null) {
            // 获取当前班次所有任课教师
            Set<String> teacherNames = new HashSet<>();
            Set<String> teacherCodes = new HashSet<>();
            if (currentOffering.getTeacherName() != null) teacherNames.add(currentOffering.getTeacherName());
            if (currentOffering.getTeacherCode() != null) teacherCodes.add(currentOffering.getTeacherCode());

            offeringTeacherRepository.findByOfferingId(currentOffering.getId()).forEach(ot -> {
                if (ot.getTeacherName() != null) teacherNames.add(ot.getTeacherName());
                if (ot.getTeacherCode() != null) teacherCodes.add(ot.getTeacherCode());
            });

            // 查找同学期内涉及这些任课教师的所有开课班次 (排除当前 offering)
            List<CourseOffering> termOfferings = academicTerm != null ? offeringRepository.findByAcademicTerm(academicTerm) : offeringRepository.findAll();
            Set<Long> teacherOfferingIds = new HashSet<>();

            for (CourseOffering termOff : termOfferings) {
                if (offeringId.equals(termOff.getId())) continue;
                boolean teacherMatch = teacherNames.contains(termOff.getTeacherName())
                        || (termOff.getTeacherCode() != null && teacherCodes.contains(termOff.getTeacherCode()));

                if (!teacherMatch) {
                    List<CourseOfferingTeacher> otList = offeringTeacherRepository.findByOfferingId(termOff.getId());
                    teacherMatch = otList.stream().anyMatch(ot -> teacherNames.contains(ot.getTeacherName()) || teacherCodes.contains(ot.getTeacherCode()));
                }

                if (teacherMatch) {
                    teacherOfferingIds.add(termOff.getId());
                }
            }

            if (!teacherOfferingIds.isEmpty()) {
                List<CourseSchedule> teacherConflicts = scheduleRepository.findConflictingTeacherSchedules(
                        academicTerm, teacherOfferingIds, dayOfWeek, sWeek, eWeek, startPeriod, endPeriod, excludeId);
                for (CourseSchedule cs : teacherConflicts) {
                    if (conflictIds.add(cs.getId())) {
                        results.add(cs);
                    }
                }
            }
        }

        return results;
    }
}
