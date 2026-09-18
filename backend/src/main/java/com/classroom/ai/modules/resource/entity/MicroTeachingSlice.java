package com.classroom.ai.modules.resource.entity;

import com.classroom.ai.modules.course.entity.Course;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 微格教学视频切片与课堂录像元数据实体 (MicroTeachingSlice)
 * 对应 US-12: 开放 RESTful 视频切片元数据挂载标准接口
 */
@Entity
@Table(name = "t_micro_teaching_slice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroTeachingSlice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 视频切片标题，如 "BOPPPS参与式学习-Scrum站会实战模拟" */
    @Column(nullable = false, length = 255)
    private String videoTitle;

    /** 切片所属 BOPPPS 环节：B (导入) / O (目标) / P (前测) / P (参与式学习) / P (后测) / S (总结) */
    @Column(nullable = false, length = 32)
    private String bopppsStage;

    /** 切片时长（秒） */
    @Column(nullable = false)
    private Integer durationSeconds;

    /** 视频切片流地址 / 存储URL */
    @Column(nullable = false, length = 512)
    private String sliceUrl;

    /** 视频封面缩略图URL */
    @Column(length = 512)
    private String coverUrl;

    /** 课堂录制日期 */
    @Column(length = 32)
    private String recordedDate;

    /** 录制教室，如 文管 A447 */
    @Column(length = 64)
    private String classroom;

    /** 上传来源系统 / 智能体代号 */
    @Column(length = 64)
    private String sourceAgent;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
