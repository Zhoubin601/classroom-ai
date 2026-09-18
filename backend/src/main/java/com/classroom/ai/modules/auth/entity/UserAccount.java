package com.classroom.ai.modules.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 本地用户账户实体 (支持教研室主任、任课教师、教学督导)
 */
@Entity
@Table(name = "t_user_account", indexes = {
    @Index(name = "uk_user_username", columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 密码哈希/明文存储(教学实验环境默认采用标准散列) */
    @Column(nullable = false, length = 128)
    private String password;

    /** 用户真实姓名，如 郭军、李主任 */
    @Column(nullable = false, length = 64)
    private String realName;

    /** 业务角色 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoleEnum role;

    /** 所属院系/教研室，如 软件工程教研室 */
    @Column(length = 64)
    private String department;

    /** 关联教师工号/标识（针对任课教师） */
    @Column(length = 32)
    private String teacherCode;

    /** 教学督导授权检索的专业编码列表（分号分隔，如 "SE;CS"） */
    @Column(length = 255)
    private String authorizedMajors;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
