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
import com.classroom.ai.modules.attendance.service.AttendanceAccessService;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
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
    private final AttendanceAccessService access;
    private final CourseAuthorizationService authorization;

    @Autowired
    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository,
                                 @Autowired(required = false) CourseScheduleRepository scheduleRepository,
                                 @Autowired(required = false) com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository,
                                 AttendanceAccessService access, CourseAuthorizationService authorization) {
        this.sessionRepository = sessionRepository;
        this.offeringRepository = offeringRepository;
        this.scheduleRepository = scheduleRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.access = access;
        this.authorization = authorization;
    }

    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository,
                                 CourseScheduleRepository scheduleRepository,
                                 com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository,
                                 CourseAuthorizationService authorization) {
        this(sessionRepository, offeringRepository, scheduleRepository, enrollmentRepository,
                new AttendanceAccessService(offeringRepository, authorization), authorization);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AttendanceSession startSession(StartAttendanceDTO dto) {
        CourseOffering offering = access.lockForWrite(dto.getOfferingId());

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

        // Parent lock prevents concurrent starts and serializes against archive.
        AttendanceSession session = sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(offering.getId(), "ACTIVE")
                .orElseGet(() -> AttendanceSession.builder()
                        .offering(offering)
                        .weekNumber(dto.getWeekNumber() != null ? dto.getWeekNumber() : 2)
                        .classroom(finalClassroom)
                        .expectedCount(resolveExpectedCount(offering))
                        .actualCount(0)
                        .attendanceRate(0.0)
                        .avgLookupRate(0.0)
                        .status("ACTIVE")
                        .startTime(LocalDateTime.now())
                        .build());
        bindOperator(session);
        return sessionRepository.save(session);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AttendanceSession finishSession(FinishAttendanceDTO dto) {
        AttendanceSession session = lockSession(dto.getSessionId());

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

        bindOperator(session);

        return sessionRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceSession getCurrentActiveSession() {
        return getCurrentActiveSession(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceSession getCurrentActiveSession(Long offeringId) {
        authorization.requireCurrentUser();
        if (offeringId != null) {
            CourseOffering offering = access.requireRead(offeringId);
            if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus())) return null;
            return sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(offeringId, "ACTIVE").orElse(null);
        }
        // Find the newest visible session, rather than exposing the global newest.
        for (AttendanceSession session : sessionRepository.findByStatusOrderByCreatedAtDescIdDesc("ACTIVE")) {
            CourseOffering offering = session.getOffering();
            if (offering == null || Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus())) continue;
            try {
                authorization.validateOfferingRead(offering);
                return session;
            } catch (com.classroom.ai.common.exception.ForbiddenException ignored) { }
        }
        return null;
    }

    @Override
    public List<AttendanceSession> getSessionsByOffering(Long offeringId) {
        access.requireRead(offeringId);
        return sessionRepository.findByOfferingId(offeringId);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AttendanceSession updateLiveStatus(Long sessionId, Integer actualCount, Double lookupRate) {
        AttendanceSession session = lockSession(sessionId);
        if ("ACTIVE".equals(session.getStatus())) {
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
        throw new IllegalStateException("考勤已归档，不能更新实时数据");
    }

    private AttendanceSession lockSession(Long id) {
        authorization.requireCurrentUser();
        if (id == null) throw new IllegalArgumentException("必须指定考勤会话");
        Long offeringId = sessionRepository.findOfferingId(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到考勤会话ID: " + id));
        access.lockForWrite(offeringId);
        return sessionRepository.findForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到考勤会话ID: " + id));
    }

    private void bindOperator(AttendanceSession session) {
        UserVO actor = authorization.requireCurrentUser();
        session.setOperatorRole(actor.getRole().name());
        session.setOperatorName(actor.getRealName() == null || actor.getRealName().isBlank() ? actor.getUsername() : actor.getRealName());
        session.setOperatorTitle(switch (actor.getRole()) {
            case DIRECTOR -> "教研室主任";
            case SUPERVISOR -> "教学督导";
            default -> "任课教师";
        });
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
