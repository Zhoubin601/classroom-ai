package com.classroom.ai.modules.supervision;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.modules.supervision.service.impl.SupervisionAnalyticsServiceImpl;
import com.classroom.ai.modules.supervision.vo.SupervisionAlertVO;
import com.classroom.ai.modules.supervision.vo.SupervisionDashboardVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisionAnalyticsTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private SupervisionEvaluationRepository evaluationRepository;

    @InjectMocks
    private SupervisionAnalyticsServiceImpl analyticsService;

    @Test
    @DisplayName("覆盖率仪表盘：精准计算全院课程覆盖率和均分")
    void testDashboardMetrics() {
        Course c1 = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        Course c2 = Course.builder().id(2L).courseCode("CS2002").courseName("计算机组成原理").build();

        when(courseRepository.findAll()).thenReturn(List.of(c1, c2));
        // 假设只有 c1 有督导记录
        when(evaluationRepository.findSupervisedCourseIds()).thenReturn(List.of(1L));

        SupervisionEvaluation eval = SupervisionEvaluation.builder()
                .id(1L)
                .totalScore(90.0)
                .status("PUBLISHED")
                .build();
        when(evaluationRepository.findAll()).thenReturn(List.of(eval));

        SupervisionDashboardVO vo = analyticsService.getDashboardMetrics();

        assertEquals(2, vo.getTotalCourses());
        assertEquals(1, vo.getSupervisedCourses());
        assertEquals(50.0, vo.getCoverageRate()); // 1 / 2 = 50%
        assertEquals(90.0, vo.getCollegeAvgScore());
        assertEquals(1, vo.getPendingCourses());
    }

    @Test
    @DisplayName("预警引擎：未覆盖课程标黄 (YELLOW)，均分低于75分课程标红 (RED)")
    void testAlertRules_YellowAndRed() {
        Course c1 = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").build();
        Course c2 = Course.builder().id(2L).courseCode("CS3002").courseName("操作系统原理").build();

        CourseOffering off2 = CourseOffering.builder().id(20L).course(c2).teacherName("测试教师").build();

        when(courseRepository.findAll()).thenReturn(List.of(c1, c2));

        // c1 没有听课记录 -> 应该触发 YELLOW (零覆盖)
        when(evaluationRepository.findByCourseId(1L)).thenReturn(List.of());

        // c2 有一条 70 分的听课记录 -> 应该触发 RED (低于75分)
        SupervisionEvaluation lowEval = SupervisionEvaluation.builder()
                .offering(off2)
                .totalScore(70.0)
                .status("PUBLISHED")
                .build();
        when(evaluationRepository.findByCourseId(2L)).thenReturn(List.of(lowEval));

        List<SupervisionAlertVO> alerts = analyticsService.getAlertList();

        assertEquals(2, alerts.size());

        SupervisionAlertVO yellowAlert = alerts.stream().filter(a -> "YELLOW".equals(a.getAlertLevel())).findFirst().orElse(null);
        assertNotNull(yellowAlert);
        assertEquals("CS3001", yellowAlert.getCourseCode());
        assertEquals("ZERO_SUPERVISION", yellowAlert.getAlertType());

        SupervisionAlertVO redAlert = alerts.stream().filter(a -> "RED".equals(a.getAlertLevel())).findFirst().orElse(null);
        assertNotNull(redAlert);
        assertEquals("CS3002", redAlert.getCourseCode());
        assertEquals("LOW_SCORE_WARNING", redAlert.getAlertType());
    }

    @Test
    @DisplayName("报表导出：CSV 报表格式规范与 UTF-8 BOM 防 Excel 乱码")
    void testAnnualReportExportCsv() {
        Course c1 = Course.builder().id(1L).courseCode("CS3001").courseName("软件项目管理").credits(3.0).hours(48).build();
        CourseOffering off1 = CourseOffering.builder().id(10L).course(c1).teacherName("郭军").className("软工2班").studentCount(95).build();

        when(offeringRepository.findAll()).thenReturn(List.of(off1));
        when(evaluationRepository.findAll()).thenReturn(List.of());

        String csv = analyticsService.generateAnnualQualityReportCsv();

        assertNotNull(csv);
        // 包含 UTF-8 BOM
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("课程代码,课程名称,任课教师"));
        assertTrue(csv.contains("CS3001,软件项目管理,郭军"));
    }
}
