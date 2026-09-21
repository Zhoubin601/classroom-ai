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
 * 课程排课时段教室实体 (CourseSchedule)
 * 对应 US-03: 集中查看多位教师排课情况（展开周次/教室/人次，如文管A447、95人等数据联动与冲突校验）
 */
@Entity
@Table(name = "t_course_schedule", indexes = {
    @Index(name = "idx_classroom_time", columnList = "classroom, dayOfWeek, startPeriod, endPeriod")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSchedule {

    @Transient
    private java.util.List<String> conflictReasons;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    /** 上课教室，如 文管 A447、信息馆 B201、知行楼 302 */
    @Column(nullable = false, length = 64)
    private String classroom;

    /** 教学周次范围描述，如 1-16周(全) */
    @Column(nullable = false, length = 64)
    private String weekRange;

    /** 起始周 */
    @Builder.Default
    private Integer startWeek = 1;

    /** 结束周 */
    @Builder.Default
    private Integer endWeek = 16;

    /** 星期几 (1: 周一, 2: 周二, 3: 周三 ... 7: 周日) */
    @Column(nullable = false)
    private Integer dayOfWeek;

    /** 开始节次 (1-12) */
    @Column(nullable = false)
    private Integer startPeriod;

    /** 结束节次 (1-12) */
    @Column(nullable = false)
    private Integer endPeriod;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
