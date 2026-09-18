package com.classroom.ai.modules.resource.entity;

import com.classroom.ai.modules.course.entity.Course;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 课件教案与多版本教学资源实体 (CourseResource)
 * 对应 Epic 2 / US-07, US-08, US-09, US-11
 */
@Entity
@Table(name = "t_course_resource")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 所属章节，如 "第一章 软件项目管理概论" (US-07 按章挂载) */
    @Column(nullable = false, length = 128)
    private String chapter;

    /** 资源文件名称，如 《软件项目管理第1讲-绪论.pptx》 */
    @Column(nullable = false, length = 255)
    private String resourceName;

    /** 文件格式类型：PDF / PPTX / DOCX 等 (US-07) */
    @Column(nullable = false, length = 32)
    private String fileType;

    /** 文件下载/预览URL */
    @Column(nullable = false, length = 512)
    private String fileUrl;

    /** 文件大小字符串，如 "18.5 MB" (US-07 单文件≤100MB) */
    @Column(length = 32)
    private String fileSize;

    /** 文件大小字节数 */
    private Long fileSizeBytes;

    /** 教学环节标签：理论 / 实验 / 研讨 (US-08) */
    @Column(nullable = false, length = 32)
    private String tag;

    /** 版本号，如 "v1.0", "v2.0" (US-11) */
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String version = "v1.0";

    /** 是否教研室全员公开共享 (US-10) */
    @Builder.Default
    private Boolean isPublic = true;

    /** 动态数字防盗链水印文本 (US-11 只读防下载) */
    @Column(length = 128)
    private String dynamicWatermark;

    /** 上传教师姓名 */
    @Column(length = 64)
    private String uploaderTeacher;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
