package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 班次—学生选课名单实体 (OfferingStudentEnrollment)
 * 统一作为考勤与人数统计的唯一名单真值来源
 * 行政班级 (adminClassName) 仅作为学生属性存储
 */
@Entity
@Table(name = "t_offering_student_enrollment", indexes = {
    @Index(name = "uk_offering_student", columnList = "offeringId, studentNumber", unique = true),
    @Index(name = "idx_offering_id", columnList = "offeringId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferingStudentEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offeringId;

    /** 关联 student.id (可为空，针对外系学生) */
    private Long studentId;

    /** 学号，唯一标识 */
    @Column(nullable = false, length = 64)
    private String studentNumber;

    /** 学生姓名 */
    @Column(nullable = false, length = 64)
    private String studentName;

    /** 行政班级名称，如 软件工程2024级2班 (仅作为属性) */
    @Column(length = 64)
    private String adminClassName;

    @CreationTimestamp
    private LocalDateTime enrolledAt;
}
