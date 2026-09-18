package com.classroom.ai.modules.attendance.service;

import com.classroom.ai.modules.attendance.dto.FinishAttendanceDTO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;

import java.util.List;

public interface AttendanceService {
    AttendanceSession startSession(StartAttendanceDTO dto);
    AttendanceSession finishSession(FinishAttendanceDTO dto);
    AttendanceSession getCurrentActiveSession();
    List<AttendanceSession> getSessionsByOffering(Long offeringId);
    AttendanceSession updateLiveStatus(Long sessionId, Integer actualCount, Double lookupRate);
}
