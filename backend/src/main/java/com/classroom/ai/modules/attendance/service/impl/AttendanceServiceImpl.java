package com.classroom.ai.modules.attendance.service.impl;

import com.classroom.ai.modules.attendance.dto.FinishAttendanceDTO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.AttendanceService;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final CourseOfferingRepository offeringRepository;

    @Override
    @Transactional
    public AttendanceSession startSession(StartAttendanceDTO dto) {
        CourseOffering offering = offeringRepository.findById(dto.getOfferingId())
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次ID: " + dto.getOfferingId()));

        // 如果已有正在进行的考勤，则复用或先完成
        AttendanceSession session = sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(offering.getId(), "ACTIVE")
                .orElseGet(() -> AttendanceSession.builder()
                        .offering(offering)
                        .weekNumber(dto.getWeekNumber() != null ? dto.getWeekNumber() : 2)
                        .classroom(dto.getClassroom() != null ? dto.getClassroom() : "智慧教室")
                        .expectedCount(offering.getStudentCount())
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
}
