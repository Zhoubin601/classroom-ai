package com.classroom.ai.modules.supervision;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.supervision.dto.EvaluationSubmitDTO;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.modules.supervision.service.impl.SupervisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupervisionServiceTest {

    @Mock
    private SupervisionEvaluationRepository evaluationRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @InjectMocks
    private SupervisionServiceImpl supervisionService;

    private CourseOffering mockOffering;

    @BeforeEach
    void setUp() {
        Course course = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        mockOffering = CourseOffering.builder()
                .id(10L)
                .course(course)
                .teacherName("郭军")
                .className("软件工程2024级2班")
                .studentCount(95)
                .build();
    }

    @Test
    @DisplayName("BOPPPS 四维打分范围校验：单项分数超过 25 分必须拦截")
    void testEvaluationScoreOutOfRange_ShouldThrowException() {
        EvaluationSubmitDTO dto = EvaluationSubmitDTO.builder()
                .offeringId(10L)
                .supervisorName("沈越 (校督导)")
                .scoreAttitude(26.0) // 超过 25 分上限
                .scoreContent(24.0)
                .scoreMethod(23.0)
                .scoreEffect(24.0)
                .build();

        when(offeringRepository.findById(10L)).thenReturn(Optional.of(mockOffering));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            supervisionService.submitEvaluation(dto);
        });

        assertTrue(ex.getMessage().contains("配置权重范围内"));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("正式提交评教：进入主任审核，不提前开始反馈延迟")
    void testEvaluationSubmit_ShouldEnterDesensitizationPeriod() {
        EvaluationSubmitDTO dto = EvaluationSubmitDTO.builder()
                .offeringId(10L)
                .supervisorName("沈越 (校督导)")
                .listenTopic("随堂听评")
                .scoreAttitude(24.5)
                .scoreContent(24.0)
                .scoreMethod(23.5)
                .scoreEffect(24.0)
                .highlights("教学组织严密，BOPPPS实践好")
                .suggestions("保持良好势头")
                .isDraft(false)
                .build();

        when(offeringRepository.findById(10L)).thenReturn(Optional.of(mockOffering));
        when(evaluationRepository.save(any(SupervisionEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionEvaluation saved = supervisionService.submitEvaluation(dto);

        assertNotNull(saved);
        assertEquals(96.0, saved.getTotalScore());
        assertEquals("PENDING_REVIEW", saved.getStatus());
        assertNotNull(saved.getSubmitTime());
        assertNull(saved.getPublishTime());
    }

    @Test
    @DisplayName("草稿暂存：状态设为 DRAFT，不触发脱敏计时")
    void testEvaluationDraft_ShouldBeDraftStatus() {
        EvaluationSubmitDTO dto = EvaluationSubmitDTO.builder()
                .offeringId(10L)
                .supervisorName("王督导")
                .scoreAttitude(20.0)
                .scoreContent(20.0)
                .scoreMethod(20.0)
                .scoreEffect(20.0)
                .isDraft(true)
                .build();

        when(offeringRepository.findById(10L)).thenReturn(Optional.of(mockOffering));
        when(evaluationRepository.save(any(SupervisionEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionEvaluation saved = supervisionService.submitEvaluation(dto);

        assertEquals("DRAFT", saved.getStatus());
        assertEquals(80.0, saved.getTotalScore());
    }
}
