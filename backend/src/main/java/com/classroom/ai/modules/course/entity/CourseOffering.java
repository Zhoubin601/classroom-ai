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
 * 教师开课班次实体 (CourseOffering)
 * 对应 US-03 / US-04: 开课统筹、多教师开课、历史开课与学生选课总人次
 */
@Entity
@Table(name = "t_course_offering")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 学年学期，如 2026-2027秋季 */
    @Column(nullable = false, length = 32)
    private String academicTerm;

    /** 主讲任课教师姓名，如 郭军、姜琳颖 */
    @Column(nullable = false, length = 64)
    private String teacherName;

    /** 教师工号/标识 */
    @Column(length = 32)
    private String teacherCode;

    /** 授课教学班级名称，如 软件工程2024级2班 */
    @Column(nullable = false, length = 64)
    private String className;

    /** 关联专业ID (US-06) */
    private Long majorId;

    /** 关联专业编码 (US-06) */
    @Column(length = 32)
    private String majorCode;

    /** 学生选课总人次/班额，如 95, 120 */
    @Column(nullable = false)
    private Integer studentCount;

    /** 结课时固化的人数快照 (US-04 历史归档冻结) */
    private Integer snapshotStudentCount;

    /** 是否已冻结历史快照 (US-04) */
    @Builder.Default
    private Boolean isSnapshotFrozen = false;

    /** 开课状态：PENDING / IN_PROGRESS / FINISHED */
    @Builder.Default
    @Column(length = 32)
    private String status = "IN_PROGRESS";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
