package com.classroom.ai.modules.resource.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.entity.CourseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ResourceAccessService {
    private final CourseAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.classroom.ai.modules.sprint3.ResourceGrantReader grants;

    public long epoch(CourseResource r) { if(r.getSeriesId()==null)return 0; var s=(grants==null?em.find(com.classroom.ai.modules.sprint3.ResourceSeries.class,r.getSeriesId()):grants.read(r.getSeriesId()));return s.getAccessEpoch(); }

    public boolean canRead(CourseResource resource) {
        var user = authorization.requireCurrentUser();
        Boolean shared=resource.getIsPublic();
        if(resource.getSeriesId()!=null){var s=(grants==null?em.find(com.classroom.ai.modules.sprint3.ResourceSeries.class,resource.getSeriesId()):grants.read(resource.getSeriesId()));if(s==null||Boolean.TRUE.equals(s.getRemoved()))return false;shared=s.getShared();}
        try {
            authorization.validateCourseRead(resource.getCourse());
            return true;
        } catch (ForbiddenException ex) {
            return Boolean.TRUE.equals(shared)
                    && (user.getRole() == RoleEnum.TEACHER || user.getRole() == RoleEnum.DIRECTOR)
                    && user.getDepartment() != null && !user.getDepartment().isBlank()
                    && Objects.equals(user.getDepartment().trim(), resource.getCourse().getDepartment());
        }
    }

    public void requireRead(CourseResource resource) {
        if (!canRead(resource)) throw new ForbiddenException("无权访问该教学资源");
    }

    public void requireWrite(CourseResource resource) {
        authorization.validateCourseWrite(resource.getCourse());
    }

    public void requireCourseWrite(Long courseId) {
        authorization.validateCourseWrite(courseId);
    }
}
