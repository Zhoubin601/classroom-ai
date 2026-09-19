package com.classroom.ai.modules.attendance.service.impl;

import com.classroom.ai.modules.attendance.dto.FinishAttendanceDTO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.AttendanceService;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository;

    @Autowired
    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository,
                                 @Autowired(required = false) CourseScheduleRepository scheduleRepository,
                                 @Autowired(required = false) com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository) {
        this.sessionRepository = sessionRepository;
        this.offeringRepository = offeringRepository;
        this.scheduleRepository = scheduleRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository,
                                 CourseScheduleRepository scheduleRepository) {
        this(sessionRepository, offeringRepository, scheduleRepository, null);
    }

    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository) {
        this(sessionRepository, offeringRepository, null, null);
    }

    @Override
    @Transactional
    public AttendanceSession startSession(StartAttendanceDTO dto) {
        CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次ID: " + dto.getOfferingId()));

        // 智能解析真实上课教室：优先显式指定；若无则从排课中读取真实教室 (如文管 A447)；最后兜底文管 A447
        String resolvedClassroom = dto.getClassroom();
        if (resolvedClassroom == null || resolvedClassroom.isBlank()) {
            if (scheduleRepository != null) {
                List<CourseSchedule> schedules = scheduleRepository.findByOfferingId(offering.getId());
                if (schedules != null && !schedules.isEmpty() && schedules.get(0).getClassroom() != null) {
                    resolvedClassroom = schedules.get(0).getClassroom();
                }
            }
        }
        if (resolvedClassroom == null || resolvedClassroom.isBlank()) {
            resolvedClassroom = "文管 A447";
        }
        final String finalClassroom = resolvedClassroom;

        // 考勤操作人身份解析与绑定 (是谁考的勤：教学督导、教研室主任还是任课教师)
        String opRole = dto.getOperatorRole();
        String opName = dto.getOperatorName();
        String opTitle = dto.getOperatorTitle();

        if (opTitle == null || opTitle.isBlank()) {
            if ("SUPERVISOR".equalsIgnoreCase(opRole)) {
                opTitle = "教学督导";
            } else if ("DIRECTOR".equalsIgnoreCase(opRole)) {
                opTitle = "教研室主任";
            } else {
                opTitle = "任课教师";
            }
        }
        if (opName == null || opName.isBlank()) {
            if ("TEACHER".equalsIgnoreCase(opRole) || opRole == null) {
                opName = offering.getTeacherName();
            } else if ("SUPERVISOR".equalsIgnoreCase(opRole)) {
                opName = "张督导";
            } else if ("DIRECTOR".equalsIgnoreCase(opRole)) {
                opName = "李主任";
            } else {
                opName = "考勤管理员";
            }
        }
        if (opRole == null || opRole.isBlank()) {
            opRole = "TEACHER";
        }
        final String finalOpName = opName;
        final String finalOpRole = opRole;
        final String finalOpTitle = opTitle;

        // 如果已有正在进行的考勤，则复用或先完成
        AttendanceSession session = sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(offering.getId(), "ACTIVE")
                .map(existing -> {
                    if (dto.getOperatorName() != null && !dto.getOperatorName().isBlank()) {
                        existing.setOperatorName(finalOpName);
                        existing.setOperatorRole(finalOpRole);
                        existing.setOperatorTitle(finalOpTitle);
                    }
                    return existing;
                })
                .orElseGet(() -> AttendanceSession.builder()
                        .offering(offering)
                        .weekNumber(dto.getWeekNumber() != null ? dto.getWeekNumber() : 2)
                        .classroom(finalClassroom)
                        .operatorName(finalOpName)
                        .operatorRole(finalOpRole)
                        .operatorTitle(finalOpTitle)
                        .expectedCount(resolveExpectedCount(offering))
                        .actualCount(0)
                        .attendanceRate(0.0)
                        .avgLookupRate(0.0)
                        .status("ACTIVE")
                        .startTime(LocalDateTime.now())
                        .build());

        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public AttendanceSession finishSession(FinishAttendanceDTO dto) {
        AttendanceSession session = sessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("未找到考勤会话ID: " + dto.getSessionId()));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new IllegalStateException("考勤已归档，不能重复修改");
        }
        int actual = dto.getActualCount() != null ? dto.getActualCount() : (session.getActualCount() != null ? session.getActualCount() : 0);
        int expected = session.getExpectedCount() != null ? session.getExpectedCount() : 0;
        validateCounts(actual, dto.getAvgLookupRate());
        double rate = expected > 0 ? BigDecimal.valueOf((double) actual / expected * 100).setScale(1, RoundingMode.HALF_UP).doubleValue() : 0.0;

        session.setActualCount(actual);
        session.setAttendanceRate(rate);
        session.setAvgLookupRate(dto.getAvgLookupRate() != null ? dto.getAvgLookupRate() : session.getAvgLookupRate());
        session.setStatus("FINISHED");
        session.setEndTime(LocalDateTime.now());
        if (dto.getAbsentStudentIds() != null) {
            session.setAbsentStudentIds(String.join(",", dto.getAbsentStudentIds()));
        }

        // 归档时记录或补充操作人信息
        if (dto.getOperatorName() != null && !dto.getOperatorName().isBlank()) {
            session.setOperatorName(dto.getOperatorName());
        }
        if (dto.getOperatorRole() != null && !dto.getOperatorRole().isBlank()) {
            session.setOperatorRole(dto.getOperatorRole());
        }
        if (dto.getOperatorTitle() != null && !dto.getOperatorTitle().isBlank()) {
            session.setOperatorTitle(dto.getOperatorTitle());
        }
        if (session.getOperatorName() == null || session.getOperatorName().isBlank()) {
            session.setOperatorName(session.getOffering() != null ? session.getOffering().getTeacherName() : "郭军");
            session.setOperatorRole("TEACHER");
            session.setOperatorTitle("任课教师");
        }

        return sessionRepository.save(session);
    }

    @Override
    public AttendanceSession getCurrentActiveSession() {
        return sessionRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE").orElse(null);
    }

    @Override
    public List<AttendanceSession> getSessionsByOffering(Long offeringId) {
        return sessionRepository.findByOfferingId(offeringId);
    }

    @Override
    @Transactional
    public AttendanceSession updateLiveStatus(Long sessionId, Integer actualCount, Double lookupRate) {
        AttendanceSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session != null && "ACTIVE".equals(session.getStatus())) {
            validateCounts(actualCount, lookupRate);
            session.setActualCount(actualCount);
            if (session.getExpectedCount() != null && session.getExpectedCount() > 0) {
                double rate = BigDecimal.valueOf((double) actualCount / session.getExpectedCount() * 100)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();
                session.setAttendanceRate(rate);
            }
            if (lookupRate != null) {
                session.setAvgLookupRate(lookupRate);
            }
            return sessionRepository.save(session);
        }
        return session;
    }

    private void validateCounts(Integer actual, Double lookupRate) {
        if (actual == null || actual < 0 || (lookupRate != null
                && (!Double.isFinite(lookupRate) || lookupRate < 0 || lookupRate > 100))) {
            throw new IllegalArgumentException("实到人数必须非负，抬头率必须在0到100之间");
        }
    }

    private int resolveExpectedCount(CourseOffering offering) {
        if (enrollmentRepository != null) {
            long count = enrollmentRepository.countByOfferingId(offering.getId());
            if (count > 0) {
                return (int) count;
            }
        }
        return offering.getStudentCount() != null ? offering.getStudentCount() : 0;
    }
}
