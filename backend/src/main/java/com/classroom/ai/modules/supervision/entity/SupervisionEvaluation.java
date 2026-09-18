package com.classroom.ai.modules.supervision.entity;

import com.classroom.ai.modules.course.entity.CourseOffering;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 督导随堂听课评价表 (SupervisionEvaluation)
 * 对应 Epic 3 / US-13, US-14: BOPPPS 四维 100 分制结构化表单与 24 小时延迟脱敏归档
 */
@Entity
@Table(name = "t_supervision_evaluation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisionEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    /** 督导专家姓名，如 "沈越 (校督导)", "张教授 (院督导)" */
    @Column(nullable = false, length = 64)
    private String supervisorName;

    /** 听课日期，如 "2026-09-12" */
    @Column(nullable = false, length = 32)
    private String evaluateDate;

    /** 听课教学章节/主题 (US-13) */
    @Column(nullable = false, length = 128)
    private String listenTopic;

    // --- BOPPPS 四维 100 分制评分 (各 25 分) ---
    /** 1. 教学态度 (0-25) */
    @Column(nullable = false)
    private Double scoreAttitude;

    /** 2. 教学内容 (0-25) */
    @Column(nullable = false)
    private Double scoreContent;

    /** 3. 教学方法 (0-25) */
    @Column(nullable = false)
    private Double scoreMethod;

    /** 4. 教学效果 (0-25) */
    @Column(nullable = false)
    private Double scoreEffect;

    /** 综合总分 (0-100)，自动计算四维之和 */
    @Column(nullable = false)
    private Double totalScore;

    // --- 质性评价文本 (US-14) ---
    /** 教学亮点 (限 500 字) */
    @Column(length = 1000)
    private String highlights;

    /** 改进建议 (限 500 字) */
    @Column(length = 1000)
    private String suggestions;

    /**
     * 评价状态 (US-14 业务规则)：
     * DRAFT: 暂存草稿
     * PENDING_DESENSITIZE: 已提交，正在经历 24 小时延迟脱敏期（防激化师生矛盾）
     * PUBLISHED: 已脱敏归档并对任课教师公开
     */
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String status = "PENDING_DESENSITIZE";

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 脱敏归档与对教师正式开放时间 (提交时间 + 24小时) */
    private LocalDateTime publishTime;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
