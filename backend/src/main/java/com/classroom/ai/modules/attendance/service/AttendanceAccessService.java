package com.classroom.ai.modules.attendance.service;

import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Attendance is writable by all three roles, within their readable offerings. */
@Service
@RequiredArgsConstructor
public class AttendanceAccessService {
    private final CourseOfferingRepository offerings;
    private final CourseAuthorizationService authorization;

    public CourseOffering requireRead(Long id) {
        AuthContext.requireRole(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR);
        if (id == null) throw new IllegalArgumentException("必须指定考勤班次");
        CourseOffering offering = offerings.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次ID: " + id));
        authorization.validateOfferingRead(offering);
        return offering;
    }

    /** Call inside the writer's transaction; archive locks the same parent row. */
    public CourseOffering lockForWrite(Long id) {
        AuthContext.requireRole(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR);
        if (id == null) throw new IllegalArgumentException("必须指定考勤班次");
        CourseOffering offering = offerings.findForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次ID: " + id));
        authorization.validateOfferingRead(offering);
        if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus()))
            throw new IllegalStateException("历史班次已冻结，不能写入考勤");
        return offering;
    }
}
