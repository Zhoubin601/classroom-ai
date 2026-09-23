package com.classroom.ai.modules.course;

import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CourseAuthorizationPrimaryTeacherTest {

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void primaryTeacherCanSeeOfferingWithoutTeamRelationRow() {
        var authorization = new CourseAuthorizationService(
                mock(CourseRepository.class),
                mock(CourseOfferingRepository.class),
                mock(CourseOfferingTeacherRepository.class));
        var primaryOffering = CourseOffering.builder()
                .id(12L)
                .teacherCode("T2024006")
                .teacherName("刘博")
                .build();
        var unrelatedOffering = CourseOffering.builder()
                .id(13L)
                .teacherCode("T2024001")
                .teacherName("郭军")
                .build();
        AuthContext.setCurrentUser(UserVO.builder()
                .username("liubo")
                .realName("刘博")
                .role(RoleEnum.TEACHER)
                .teacherCode("T2024006")
                .build());

        assertEquals(List.of(primaryOffering), authorization.filterOfferings(List.of(primaryOffering, unrelatedOffering)));
    }
}
