package com.classroom.ai.modules.course.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseOfferingQueryService {
    private final CourseOfferingRepository offerings;
    private final MajorRepository majors;
    private final TeacherRepository teachers;
    private final CourseAuthorizationService authorization;
    private final CourseOfferingTeacherRepository relations;
    private final OfferingStudentEnrollmentRepository enrollments;

    @Transactional(readOnly = true)
    public List<CourseOffering> search(String term,String teacher,String keyword,String majorCode,Long majorId,Long teacherId) {
        var user = authorization.requireCurrentUser();
        String major = clean(majorCode);
        if (majorId != null) major = majors.findById(majorId).orElseThrow(() -> new IllegalArgumentException("专业 ID 不存在")).getMajorCode();
        if (teacherId != null && !teachers.existsById(teacherId)) throw new IllegalArgumentException("教师 ID 不存在");
        if (major != null && user.getRole() == RoleEnum.SUPERVISOR) {
            var allowed = Arrays.stream(Optional.ofNullable(user.getAuthorizedMajors()).orElse("").split(";")).map(String::trim).map(String::toUpperCase).toList();
            if (!allowed.contains(major.toUpperCase(Locale.ROOT))) throw new ForbiddenException("无权查询该专业");
        }
        var result = authorization.filterOfferings(offerings.searchCombined(clean(term),teacherId,teacherId == null ? clean(teacher) : null,clean(keyword),majorId,majorId == null ? major : null));
        // Response-only copies: querying a count must never dirty persisted offering entities.
        return result.stream().map(o -> {
            CourseOffering copy = new CourseOffering(); org.springframework.beans.BeanUtils.copyProperties(o,copy);
            copy.setTeachers(relations.findByOfferingId(o.getId()));
            if (Boolean.TRUE.equals(o.getIsSnapshotFrozen())) copy.setStudentCount(o.getSnapshotStudentCount());
            else copy.setStudentCount(Math.toIntExact(enrollments.countByOfferingId(o.getId())));
            return copy;
        }).toList();
    }
    private static String clean(String s) {return s == null || s.isBlank() ? null : s.trim();}
}
