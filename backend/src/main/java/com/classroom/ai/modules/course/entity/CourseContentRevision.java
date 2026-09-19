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

    /** 业务发布版本号 (US-02: 正式发布版本 v1, v2, v3...；未发布时为 null) */
    @Column
    private Integer publishVersion;

    /** 并发锁版本号 (采用 JPA 乐观锁 @Version，每次编辑/保存/发布自动递增，用于并发修改冲突检测) */
    @Version
    @Column(nullable = false)
    private Integer lockVersion;

    /** 兼容历史版本字段 (优先取 publishVersion，若为空则取 lockVersion) */
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

    @PrePersist
    @PreUpdate
    public void syncVersion() {
        if (this.lockVersion == null) {
            this.lockVersion = 0;
        }
        if (this.version == null) {
            this.version = this.publishVersion != null ? this.publishVersion : this.lockVersion;
        }
    }
}
