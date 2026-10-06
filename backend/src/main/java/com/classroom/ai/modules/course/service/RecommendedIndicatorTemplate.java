package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.entity.TrainingIndicator;
import java.util.List;
import java.util.stream.IntStream;

/** A clearly labelled recommendation, kept separate from imported professional plans. */
public final class RecommendedIndicatorTemplate {
    public static final String VERSION = "RECOMMENDED-12";
    private static final List<String> CATEGORIES = List.of("工程知识", "问题分析", "设计/开发解决方案", "研究",
            "使用现代工具", "工程与社会", "环境和可持续发展", "职业规范", "个人和团队", "沟通", "项目管理", "终身学习");
    private RecommendedIndicatorTemplate() {}
    public static List<TrainingIndicator> items(String majorCode) {
        return IntStream.range(0, CATEGORIES.size()).mapToObj(i -> {
            var item = new TrainingIndicator();
            item.setMajorCode(majorCode);
            item.setPlanVersion(VERSION);
            item.setIndicatorCode((i + 1) + "-1");
            item.setRequirementCategory(CATEGORIES.get(i));
            item.setIndicatorDescription(CATEGORIES.get(i) + "的课程支撑要求（推荐草案，请按课程实际内容修订）");
            return item;
        }).toList();
    }
    public static boolean contains(String code) {
        return code != null && code.matches("(?:[1-9]|1[0-2])-[1-9][0-9]*");
    }
}
