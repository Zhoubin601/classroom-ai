package com.classroom.ai.modules.attendance.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.attendance.dto.FinishAttendanceDTO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@CrossOrigin
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/start")
    public ApiResponse<AttendanceSession> startSession(@RequestBody StartAttendanceDTO dto) {
        return ApiResponse.success("课堂智能考勤已启动", attendanceService.startSession(dto));
    }

    @PostMapping("/finish")
    public ApiResponse<AttendanceSession> finishSession(@RequestBody FinishAttendanceDTO dto) {
        return ApiResponse.success("课堂考勤已成功结束并归档至课程档案", attendanceService.finishSession(dto));
    }

    @GetMapping("/current")
    public ApiResponse<AttendanceSession> getCurrentSession() {
        return ApiResponse.success(attendanceService.getCurrentActiveSession());
    }

    @GetMapping("/offering/{offeringId}")
    public ApiResponse<List<AttendanceSession>> getSessionsByOffering(@PathVariable Long offeringId) {
        return ApiResponse.success(attendanceService.getSessionsByOffering(offeringId));
    }

    @PostMapping("/live-update")
    public ApiResponse<AttendanceSession> updateLiveStatus(@RequestParam Long sessionId,
                                                           @RequestParam Integer actualCount,
                                                           @RequestParam(required = false) Double lookupRate) {
        return ApiResponse.success(attendanceService.updateLiveStatus(sessionId, actualCount, lookupRate));
    }
}
