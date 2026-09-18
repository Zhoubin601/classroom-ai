package com.classroom.ai.modules.attendance.entity;

import com.classroom.ai.modules.course.entity.CourseOffering;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 课堂智能考勤会话实体 (AttendanceSession)
 * 将摄像头 1:N 人脸识别、实时在座人数、抬头率波形与教务开课排课深度绑定
 */
@Entity
@Table(name = "t_attendance_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    /** 教学周次，如 2 */
    @Column(nullable = false)
    private Integer weekNumber;

    /** 上课教室，如 文管 A447 */
    @Column(nullable = false, length = 64)
    private String classroom;

    /** 应到学生人数 */
    @Column(nullable = false)
    private Integer expectedCount;

    /** 实时/最终实到人数 */
    @Builder.Default
    private Integer actualCount = 0;

    /** 出勤率百分比，如 95.8 */
    @Builder.Default
    private Double attendanceRate = 0.0;

    /** 课堂平均抬头率 LookUp Rate，如 82.4 */
    @Builder.Default
    private Double avgLookupRate = 0.0;

    /** 考勤状态：ACTIVE (正在考勤推流中) / FINISHED (已下课归档) */
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    /** 考勤开始时间 */
    private LocalDateTime startTime;

    /** 考勤结束归档时间 */
    private LocalDateTime endTime;

    /** 缺勤学生学号列表 (逗号分隔) */
    @Column(columnDefinition = "TEXT")
    private String absentStudentIds;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
