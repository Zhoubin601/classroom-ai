package com.classroom.ai.modules.supervision.service.impl;

import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.supervision.repository.SupervisionEvaluationRepository;
import com.classroom.ai.modules.supervision.service.SupervisionAnalyticsService;
import com.classroom.ai.modules.supervision.vo.SupervisionAlertVO;
import com.classroom.ai.modules.supervision.vo.SupervisionDashboardVO;
import com.classroom.ai.modules.supervision.vo.TeacherQualityRadarVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupervisionAnalyticsServiceImpl implements SupervisionAnalyticsService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final SupervisionEvaluationRepository evaluationRepository;

    @Override
    public SupervisionDashboardVO getDashboardMetrics() {
        List<Course> allCourses = courseRepository.findAll();
        int totalCourses = allCourses.size();
        List<Long> supervisedCourseIds = evaluationRepository.findSupervisedCourseIds();
        int supervisedCount = supervisedCourseIds.size();

        double coverageRate = 0.0;
        if (totalCourses > 0) {
            coverageRate = BigDecimal.valueOf((double) supervisedCount / totalCourses * 100)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        List<SupervisionEvaluation> validEvals = evaluationRepository.findAll().stream()
                .filter(e -> !"DRAFT".equals(e.getStatus()))
                .toList();

        double avgScore = 0.0;
        if (!validEvals.isEmpty()) {
            double sum = validEvals.stream().mapToDouble(SupervisionEvaluation::getTotalScore).sum();
            avgScore = BigDecimal.valueOf(sum / validEvals.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        return SupervisionDashboardVO.builder()
                .totalCourses(totalCourses)
                .supervisedCourses(supervisedCount)
                .coverageRate(coverageRate)
                .totalEvaluations(validEvals.size())
                .collegeAvgScore(avgScore)
                .pendingCourses(Math.max(0, totalCourses - supervisedCount))
                .build();
    }

    @Override
    public List<SupervisionAlertVO> getAlertList() {
        List<SupervisionAlertVO> alerts = new ArrayList<>();
        List<Course> allCourses = courseRepository.findAll();

        for (Course course : allCourses) {
            List<SupervisionEvaluation> evals = evaluationRepository.findByCourseId(course.getId()).stream()
                    .filter(e -> !"DRAFT".equals(e.getStatus()))
                    .toList();

            // 1. 检查覆盖率或零督导预警 (US-16: 覆盖率<30% 或零覆盖黄色预警)
            if (evals.isEmpty()) {
                alerts.add(SupervisionAlertVO.builder()
                        .courseId(course.getId())
                        .courseCode(course.getCourseCode())
                        .courseName(course.getCourseName())
                        .teacherName("待安排")
                        .department(course.getDepartment())
                        .alertLevel("YELLOW")
                        .alertType("ZERO_SUPERVISION")
                        .alertMessage("该课程本学期尚未安排督导听课巡检（零覆盖率预警）")
                        .currentScore(0.0)
                        .evaluationCount(0)
                        .suggestAction("建议教研室主任尽快委派院督导进班随堂听课")
                        .build());
            } else {
                // 计算平均分
                double avg = evals.stream().mapToDouble(SupervisionEvaluation::getTotalScore).average().orElse(0.0);
                avg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();

                // 2. 检查低分预警 (US-16: 督导均分 < 75 分红色预警)
                if (avg < 75.0) {
                    alerts.add(SupervisionAlertVO.builder()
                            .courseId(course.getId())
                            .courseCode(course.getCourseCode())
                            .courseName(course.getCourseName())
                            .teacherName(evals.get(0).getOffering().getTeacherName())
                            .department(course.getDepartment())
                            .alertLevel("RED")
                            .alertType("LOW_SCORE_WARNING")
                            .alertMessage(String.format("督导听课均分仅为 %.1f 分（低于 75 分合格线，触发教学质量红色预警）", avg))
                            .currentScore(avg)
                            .evaluationCount(evals.size())
                            .suggestAction("建议组织资深教学名师开展一对一磨课与 BOPPPS 教学法针对性帮扶")
                            .build());
                }
            }
        }

        return alerts;
    }

    @Override
    public TeacherQualityRadarVO getTeacherRadar(String teacherName) {
        List<SupervisionEvaluation> evals = evaluationRepository.findByTeacherName(teacherName).stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus()) || ("PENDING_DESENSITIZE".equals(e.getStatus()) && e.getPublishTime() != null && !e.getPublishTime().isAfter(java.time.LocalDateTime.now())))
                .toList();

        if (evals.isEmpty()) {
            return TeacherQualityRadarVO.builder()
                    .teacherName(teacherName)
                    .courseName("暂无听课评价")
                    .evaluationCount(0)
                    .overallScore(0.0)
                    .attitudeScore(0.0)
                    .contentScore(0.0)
                    .methodScore(0.0)
                    .effectScore(0.0)
                    .highlightList(List.of())
                    .suggestionList(List.of())
                    .wordCloud(List.of())
                    .build();
        }

        int count = evals.size();
        double avgTotal = evals.stream().mapToDouble(SupervisionEvaluation::getTotalScore).average().orElse(0.0);
        double avgAttitude = evals.stream().mapToDouble(SupervisionEvaluation::getScoreAttitude).average().orElse(0.0);
        double avgContent = evals.stream().mapToDouble(SupervisionEvaluation::getScoreContent).average().orElse(0.0);
        double avgMethod = evals.stream().mapToDouble(SupervisionEvaluation::getScoreMethod).average().orElse(0.0);
        double avgEffect = evals.stream().mapToDouble(SupervisionEvaluation::getScoreEffect).average().orElse(0.0);

        List<String> highlights = evals.stream()
                .map(SupervisionEvaluation::getHighlights)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> suggestions = evals.stream()
                .map(SupervisionEvaluation::getSuggestions)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        // Count actual review phrases; do not fabricate keyword analysis.
        Map<String, Integer> phrases = new LinkedHashMap<>();
        java.util.stream.Stream.concat(highlights.stream(), suggestions.stream())
                .flatMap(text -> Arrays.stream(text.split("[，。；、,;\\n]+")))
                .map(String::trim).filter(text -> !text.isEmpty())
                .forEach(text -> phrases.merge(text, 1, Integer::sum));
        List<TeacherQualityRadarVO.WordCloudItem> wordCloud = phrases.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(20)
                .map(entry -> new TeacherQualityRadarVO.WordCloudItem(entry.getKey(), entry.getValue())).toList();

        String courseName = evals.get(0).getOffering().getCourse().getCourseName();

        return TeacherQualityRadarVO.builder()
                .teacherName(teacherName)
                .courseName(courseName)
                .evaluationCount(count)
                .overallScore(BigDecimal.valueOf(avgTotal).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .attitudeScore(BigDecimal.valueOf(avgAttitude).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .contentScore(BigDecimal.valueOf(avgContent).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .methodScore(BigDecimal.valueOf(avgMethod).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .effectScore(BigDecimal.valueOf(avgEffect).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .highlightList(highlights)
                .suggestionList(suggestions)
                .wordCloud(wordCloud)
                .build();
    }

    @Override
    public String generateAnnualQualityReportCsv() {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM
        sb.append("\uFEFF");
        sb.append("序号,课程代码,课程名称,任课教师,学分,学时,选课班级,班额人次,督导听课次数,综合均分,教学态度均分,教学内容均分,教学方法均分,教学效果均分,质量达成评价\n");

        List<SupervisionEvaluation> allEvals = evaluationRepository.findAll().stream()
                .filter(e -> !"DRAFT".equals(e.getStatus()))
                .toList();

        Map<Long, List<SupervisionEvaluation>> evalMap = allEvals.stream()
                .collect(Collectors.groupingBy(e -> e.getOffering().getId()));

        List<com.classroom.ai.modules.course.entity.CourseOffering> offerings = offeringRepository.findAll();
        int idx = 1;
        for (var offering : offerings) {
            List<SupervisionEvaluation> list = evalMap.getOrDefault(offering.getId(), Collections.emptyList());
            double totalAvg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(SupervisionEvaluation::getTotalScore).average().orElse(0.0);
            double attAvg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(SupervisionEvaluation::getScoreAttitude).average().orElse(0.0);
            double conAvg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(SupervisionEvaluation::getScoreContent).average().orElse(0.0);
            double metAvg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(SupervisionEvaluation::getScoreMethod).average().orElse(0.0);
            double effAvg = list.isEmpty() ? 0.0 : list.stream().mapToDouble(SupervisionEvaluation::getScoreEffect).average().orElse(0.0);

            String statusLevel = list.isEmpty() ? "待督导" : (totalAvg >= 85 ? "优秀" : (totalAvg >= 75 ? "合格" : "需帮扶预警"));

            sb.append(String.format(Locale.ROOT, "%d,%s,%s,%s,%.1f,%d,%s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%s\n",
                    idx++,
                    csvCell(offering.getCourse().getCourseCode()),
                    csvCell(offering.getCourse().getCourseName()),
                    csvCell(offering.getTeacherName()),
                    offering.getCourse().getCredits(),
                    offering.getCourse().getHours(),
                    csvCell(offering.getClassName()),
                    offering.getStudentCount(),
                    list.size(),
                    totalAvg, attAvg, conAvg, metAvg, effAvg,
                    statusLevel
            ));
        }

        return sb.toString();
    }
    private String csvCell(String value) {
        if (value == null) return "";
        if (value.matches("^[=+@-].*")) value = "'" + value;
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
