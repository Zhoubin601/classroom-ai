package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CourseScheduleServiceImpl implements CourseScheduleService {
    private final CourseScheduleRepository scheduleRepository;
    private final CourseOfferingRepository offeringRepository;
    private final ScheduleConflictService conflicts;
    private final AcademicTermLockService termLocks;
    private final CourseAuthorizationService authorization;

    public List<CourseSchedule> getSchedulesByOfferingId(Long id) { return getFilteredSchedules(null,null,null,null,id); }
    public List<CourseSchedule> getSchedulesByClassroom(String room) { return getFilteredSchedules(null,null,null,room,null); }
    public List<CourseSchedule> getAllSchedules() { return getFilteredSchedules(null,null,null,null,null); }

    public List<CourseSchedule> getFilteredSchedules(String term, String teacher, Integer week, String room, Long offeringId) {
        if (week != null && (week < 1 || week > 53)) throw new IllegalArgumentException("周次必须在 1–53 之间");
        if (offeringId != null) authorization.validateOfferingRead(requireOffering(offeringId));
        Set<Long> allowed = new HashSet<>();
        authorization.filterOfferings(offeringRepository.findAll()).forEach(o -> allowed.add(o.getId()));
        return scheduleRepository.findAll().stream().filter(s -> allowed.contains(s.getOffering().getId()))
            .filter(s -> offeringId == null || offeringId.equals(s.getOffering().getId()))
            .filter(s -> term == null || term.isBlank() || ScheduleConflictService.key(term).equals(ScheduleConflictService.key(s.getOffering().getAcademicTerm())))
            .filter(s -> room == null || room.isBlank() || ScheduleConflictService.key(room).equals(ScheduleConflictService.key(s.getClassroom())))
            .filter(s -> week == null || week >= s.getStartWeek() && week <= s.getEndWeek())
            .peek(s -> conflicts.hydrate(s.getOffering()))
            .filter(s -> teacher == null || teacher.isBlank() || teacher.trim().equals(s.getOffering().getTeacherName())
                || teacher.trim().equalsIgnoreCase(s.getOffering().getTeacherCode())
                || s.getOffering().getTeachers().stream().anyMatch(t -> teacher.trim().equals(t.getTeacherName()) || teacher.trim().equalsIgnoreCase(t.getTeacherCode())))
            .toList();
    }

    private CourseOffering requireOffering(Long id) {
        if (id == null) throw new IllegalArgumentException("开课班次不能为空");
        return offeringRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("开课班次不存在"));
    }

    private CourseOffering lockOffering(Long id) {
        CourseArchiveRules.requireDirector();
        if (id == null) throw new IllegalArgumentException("开课班次不能为空");
        CourseOffering o = offeringRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("开课班次不存在"));
        CourseArchiveRules.validateDepartment(o.getCourse().getDepartment());
        if (Boolean.TRUE.equals(o.getIsSnapshotFrozen())) throw new IllegalStateException("历史班次已冻结");
        termLocks.lock(o.getAcademicTerm());
        return o;
    }

    public CourseSchedule saveSchedule(CourseScheduleDTO dto) {
        int sw = dto.getStartWeek() == null ? 1 : dto.getStartWeek();
        int ew = dto.getEndWeek() == null ? 16 : dto.getEndWeek();
        ScheduleConflictService.validate(dto.getClassroom(),dto.getDayOfWeek(),sw,ew,dto.getStartPeriod(),dto.getEndPeriod());
        CourseOffering offering = lockOffering(dto.getOfferingId());
        CourseSchedule row = dto.getId() == null ? new CourseSchedule() : scheduleRepository.findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("排课记录不存在"));
        if (row.getId() != null && !offering.getId().equals(row.getOffering().getId()))
            throw new IllegalArgumentException("编辑排课不能更换班次，请删除后新建");
        List<CourseSchedule> found = conflicts.find(offering, conflicts.teacherCodes(offering), dto.getClassroom(), dto.getDayOfWeek(), sw, ew, dto.getStartPeriod(), dto.getEndPeriod(), dto.getId());
        if (!found.isEmpty()) throw new ScheduleConflictException(found);
        row.setOffering(offering);
        row.setClassroom(dto.getClassroom().trim()); row.setStartWeek(sw); row.setEndWeek(ew);
        row.setWeekRange(sw + "-" + ew + "周"); row.setDayOfWeek(dto.getDayOfWeek());
        row.setStartPeriod(dto.getStartPeriod()); row.setEndPeriod(dto.getEndPeriod());
        conflicts.hydrate(offering);
        return scheduleRepository.saveAndFlush(row);
    }

    public void deleteSchedule(Long id) {
        // Read only the parent ID first; refresh the record after waiting for its parent lock.
        Long offeringId = scheduleRepository.findOfferingId(id).orElseThrow(() -> new IllegalArgumentException("排课记录不存在"));
        lockOffering(offeringId);
        scheduleRepository.deleteById(id);
        scheduleRepository.flush();
    }

    public List<CourseSchedule> checkConflict(Long offeringId, String room, Integer day, Integer sw, Integer ew, Integer sp, Integer ep, Long excludeId) {
        sw = sw == null ? 1 : sw; ew = ew == null ? 16 : ew;
        ScheduleConflictService.validate(room,day,sw,ew,sp,ep);
        CourseArchiveRules.requireDirector();
        CourseOffering o = requireOffering(offeringId);
        CourseArchiveRules.validateDepartment(o.getCourse().getDepartment());
        if (excludeId != null && !scheduleRepository.findById(excludeId).map(s -> offeringId.equals(s.getOffering().getId())).orElse(false))
            throw new IllegalArgumentException("排除的排课记录不属于该班次");
        return conflicts.find(o,conflicts.teacherCodes(o),room,day,sw,ew,sp,ep,excludeId);
    }
}
