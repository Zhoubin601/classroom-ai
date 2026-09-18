package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 教师档案实体 (Teacher)
 * 具备稳定 ID 和工号
 */
@Entity
@Table(name = "t_teacher", indexes = {
    @Index(name = "uk_teacher_code", columnList = "teacherCode", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 教师工号，唯一稳定标识 */
    @Column(nullable = false, unique = true, length = 32)
    private String teacherCode;

    /** 教师姓名 */
    @Column(nullable = false, length = 64)
    private String teacherName;

    /** 所属教研室 */
    @Column(length = 64)
    private String department;

    /** 职称，如 教授、副教授、讲师 */
    @Column(length = 32)
    private String title;

    /** 关联登录账号 ID (可为空) */
    private Long userId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
