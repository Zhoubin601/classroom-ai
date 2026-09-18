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
 * 课程内容大纲与简介版本控制实体 (CourseContentRevision)
 * 对应 US-02: 简介草稿与发布、多版本追溯与乐观锁控制
 */
@Entity
@Table(name = "t_course_content_revision", indexes = {
    @Index(name = "idx_course_version", columnList = "courseId, version"),
    @Index(name = "idx_course_status", columnList = "courseId, status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseContentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联课程ID */
    @Column(nullable = false)
    private Long courseId;

    /** 课程简介 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 考核与成绩评定方式 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String assessmentMethod;

    /** 教学目标 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String objectives;

    /** 版本号 (递增整型，支持乐观锁检测) */
    @Column(nullable = false)
    private Integer version;

    /** 状态：DRAFT (草稿), PUBLISHED (已发布) */
    @Column(nullable = false, length = 32)
    private String status;

    /** 当前草稿编辑人姓名 */
    @Column(length = 64)
    private String editorName;

    /** 审核发布人姓名 */
    @Column(length = 64)
    private String publisherName;

    /** 发布人工号/标识 */
    @Column(length = 32)
    private String publisherCode;

    /** 发布生效时间 */
    private LocalDateTime publishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
