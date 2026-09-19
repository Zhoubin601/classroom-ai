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
 * 课程全量档案实体 (Course)
 * 对应史诗 Epic 1 / US-01: 统一规范课程底座
 */
@Entity
@Table(name = "t_course", indexes = {
    @Index(name = "uk_course_code", columnList = "courseCode", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程代码，如 CS3001 (全局唯一) */
    @Column(nullable = false, unique = true, length = 64)
    private String courseCode;

    /** 课程名称，如 《软件项目管理》 */
    @Column(nullable = false, length = 128)
    private String courseName;

    /** 开课院系/教研室，如 软件工程教研室 */
    @Column(length = 64)
    private String department;

    /** 主讲/任课教师姓名，如 郭军 (教授) */
    @Column(length = 64)
    private String teacherName;

    /** 关联专业ID */
    private Long majorId;

    /** 关联专业编码，如 SE, CS */
    @Column(length = 32)
    private String majorCode;

    /** 学分，如 3.0 */
    @Column(nullable = false)
    private Double credits;

    /** 总学时，如 48 */
    @Column(nullable = false)
    private Integer hours;

    /** 理论学时 */
    private Integer theoryHours;

    /** 实验/实践学时 */
    private Integer practiceHours;

    /** 课程性质：专业核心课 / 专业选修课 / 通识必修课 */
    @Column(length = 32)
    private String courseType;

    /** 先修课程关系，如 《软件工程导论》 */
    @Column(length = 255)
    private String prerequisites;

    /** 课程简介 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 教学目标 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String objectives;

    /** 考核与成绩评定方式 (US-02) */
    @Column(columnDefinition = "TEXT")
    private String assessmentMethod;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
