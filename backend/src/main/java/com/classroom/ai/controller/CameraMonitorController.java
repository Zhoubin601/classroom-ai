package com.classroom.ai.controller;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.dto.FaceRegisterDTO;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.AttendanceAccessService;
import com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository;
import com.classroom.ai.repository.FaceFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Authenticated, active-offering-only context for the local camera process. */
@RestController
@RequestMapping("/api/visual")
@RequiredArgsConstructor
public class CameraMonitorController {
    private final AttendanceAccessService access;
    private final AttendanceSessionRepository sessions;
    private final OfferingStudentEnrollmentRepository enrollments;
    private final FaceFeatureRepository faces;

    @GetMapping("/monitor-context")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> context(@RequestParam Long offeringId, @RequestParam Long sessionId) {
        var offering = access.requireRead(offeringId);
        if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus()))
            throw new IllegalStateException("班次已冻结，摄像头考勤已停止");
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("考勤会话不存在"));
        if (!Objects.equals(session.getOffering().getId(), offeringId))
            throw new IllegalArgumentException("考勤会话与所选班次不一致");
        if (!"ACTIVE".equals(session.getStatus())) throw new IllegalStateException("考勤会话已结束");
        var roster = enrollments.findByOfferingId(offeringId);
        var byId = new HashMap<String, com.classroom.ai.modules.course.entity.OfferingStudentEnrollment>();
        roster.forEach(e -> byId.put(e.getStudentNumber(), e));
        var scopedFaces = new ArrayList<FaceRegisterDTO>();
        if (!byId.isEmpty()) for (var face : faces.findAllByStudentIdIn(new ArrayList<>(byId.keySet()))) {
            var enrolled = byId.get(face.getStudentId());
            if (enrolled == null) continue;
            var vector = JSON.parseArray(face.getFeatureVector(), Float.class);
            if (vector == null || vector.size() != 512 || vector.stream().anyMatch(v -> v == null || !Float.isFinite(v))) continue;
            scopedFaces.add(FaceRegisterDTO.builder().studentId(face.getStudentId()).name(enrolled.getStudentName())
                    .className(offering.getClassName()).featureVector(vector).build());
        }
        return ApiResponse.success(Map.of("offeringId", offeringId, "sessionId", sessionId,
                "courseName", offering.getCourse().getCourseName(), "className", offering.getClassName(),
                "expectedCount", roster.size(), "faces", scopedFaces));
    }
}
