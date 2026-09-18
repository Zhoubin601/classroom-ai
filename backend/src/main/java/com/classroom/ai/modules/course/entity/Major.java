package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 独立专业字典实体 (Major)
 * 严格独立于教研室与行政班
 */
@Entity
@Table(name = "t_major", indexes = {
    @Index(name = "uk_major_code", columnList = "majorCode", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Major {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 专业编码，如 SE, CS, AI */
    @Column(nullable = false, unique = true, length = 32)
    private String majorCode;

    /** 专业名称，如 软件工程、计算机科学与技术 */
    @Column(nullable = false, length = 64)
    private String majorName;

    /** 所属院系/教研室 */
    @Column(length = 64)
    private String department;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
