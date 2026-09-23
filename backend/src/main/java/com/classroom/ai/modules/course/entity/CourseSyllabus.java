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
 * 课程大纲版本实体 (CourseSyllabus)
 * 对应 US-05: 按工程认证标准审查并锁定课程大纲与指标点对应关系
 */
@Entity
@Table(name = "t_course_syllabus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSyllabus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 大纲版本，如 2026版、2025版 */
    @Column(nullable = false, length = 32)
    private String version;

    /** Version of the major training plan used for the indicator snapshot. */
    @Column(length = 32)
    private String planVersion;

    /** 审核状态：DRAFT / SUBMITTED / APPROVED / LOCKED (版本锁定) */
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String status = "APPROVED";

    /** 制定人/责任教师 */
    @Column(length = 64)
    private String authorTeacher;

    /** 审查锁定人 (教研室主任) */
    @Column(length = 64)
    private String lockedBy;

    /** 课程目标详细说明 (JSON 或文本) */
    @Column(columnDefinition = "TEXT")
    private String courseGoals;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
