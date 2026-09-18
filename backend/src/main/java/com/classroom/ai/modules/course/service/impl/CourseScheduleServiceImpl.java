package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import com.classroom.ai.modules.course.service.CourseScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseScheduleServiceImpl implements CourseScheduleService {

    private final CourseScheduleRepository scheduleRepository;
    private final CourseOfferingRepository offeringRepository;

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
    @Transactional
    public CourseSchedule saveSchedule(CourseScheduleDTO dto) {
        dto.setStartWeek(dto.getStartWeek() != null ? dto.getStartWeek() : 1);
        dto.setEndWeek(dto.getEndWeek() != null ? dto.getEndWeek() : 16);
        // 1. 冲突排查
        List<CourseSchedule> conflicts = checkConflict(
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
            String courseName = conflict.getOffering() != null && conflict.getOffering().getCourse() != null
                    ? conflict.getOffering().getCourse().getCourseName()
                    : "已知课程";
            throw new IllegalStateException(String.format("排课冲突拦截：教室 %s 在星期%d 第%d-%d节（%s）已被【%s】占用！",
                    dto.getClassroom(), dto.getDayOfWeek(), dto.getStartPeriod(), dto.getEndPeriod(),
                    conflict.getWeekRange(), courseName));
        }

        CourseSchedule schedule;
        if (dto.getId() != null) {
            schedule = scheduleRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getId() + " 的排课记录"));
        } else {
            schedule = new CourseSchedule();
            CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到ID为 " + dto.getOfferingId() + " 的开课班次"));
            schedule.setOffering(offering);
        }

        schedule.setClassroom(dto.getClassroom());
        schedule.setWeekRange(dto.getWeekRange() != null ? dto.getWeekRange() : String.format("%d-%d周", dto.getStartWeek(), dto.getEndWeek()));
        schedule.setStartWeek(dto.getStartWeek() != null ? dto.getStartWeek() : 1);
        schedule.setEndWeek(dto.getEndWeek() != null ? dto.getEndWeek() : 16);
        schedule.setDayOfWeek(dto.getDayOfWeek());
        schedule.setStartPeriod(dto.getStartPeriod());
        schedule.setEndPeriod(dto.getEndPeriod());

        return scheduleRepository.save(schedule);
    }

    @Override
    @Transactional
    public void deleteSchedule(Long id) {
        scheduleRepository.deleteById(id);
    }

    @Override
    public List<CourseSchedule> checkConflict(String classroom, Integer dayOfWeek, Integer startWeek, Integer endWeek,
                                             Integer startPeriod, Integer endPeriod, Long excludeId) {
        int sWeek = startWeek != null ? startWeek : 1;
        int eWeek = endWeek != null ? endWeek : 16;
        if (classroom == null || classroom.isBlank() || dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7
                || sWeek < 1 || eWeek < sWeek || startPeriod == null || endPeriod == null
                || startPeriod < 1 || endPeriod < startPeriod) {
            throw new IllegalArgumentException("教室、星期、周次和节次范围必须有效");
        }
        return scheduleRepository.findConflictingSchedules(classroom, dayOfWeek, sWeek, eWeek, startPeriod, endPeriod, excludeId);
    }
}
