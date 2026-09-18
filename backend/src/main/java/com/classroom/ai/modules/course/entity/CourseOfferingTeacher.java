package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 开课班次与授课教师多对多关联实体
 * 支持一门开课班次由多名教师联合授课
 */
@Entity
@Table(name = "t_course_offering_teacher", indexes = {
    @Index(name = "idx_offering_teacher", columnList = "offeringId, teacherId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOfferingTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offeringId;

    @Column(nullable = false)
    private Long teacherId;

    @Column(length = 32)
    private String teacherCode;

    @Column(nullable = false, length = 64)
    private String teacherName;

    /** 教学分工角色：PRIMARY (主讲), ASSISTANT (助课) */
    @Builder.Default
    @Column(length = 32)
    private String roleInOffering = "PRIMARY";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
