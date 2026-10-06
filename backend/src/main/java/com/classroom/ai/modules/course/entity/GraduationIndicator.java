package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 工程教育专业认证毕业要求指标点与大纲映射表 (GraduationIndicator)
 * 对应 US-05: 培养方案指标点对应关系及支撑权重
 */
@Entity
@Table(name = "t_graduation_indicator", indexes = {
    @Index(name = "idx_course_indicator", columnList = "course_id, indicatorCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraduationIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 所属大纲（可选） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syllabus_id")
    private CourseSyllabus syllabus;

    /** 指标点编号，如 1-1, 2-3, 11-1 */
    @Column(nullable = false, length = 32)
    private String indicatorCode;

    /** 毕业要求大类，如 "1. 工程知识", "2. 问题分析", "11. 项目管理" */
    @Column(nullable = false, length = 64)
    private String requirementCategory;

    /** 指标点分解内容描述 */
    @Column(columnDefinition = "TEXT")
    private String indicatorDescription;

    /** 支撑度权重：H (强支撑), M (中等支撑), L (弱支撑) */
    @Column(nullable = false, length = 8)
    private String supportWeight;

    /** 对应课程目标编号，如 目标1, 目标2 */
    @Column(length = 64)
    private String targetGoal;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
