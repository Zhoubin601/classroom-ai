package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 课程批量导入审计记录实体 (CourseImportLog)
 * 记录 US-01 导入操作人、时间、文件名、总行数、入库数与执行结果
 */
@Entity
@Table(name = "t_course_import_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String batchId;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(length = 255)
    private String fileName;

    private Integer totalRows;

    private Integer successCount;

    private Integer errorCount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 512)
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
