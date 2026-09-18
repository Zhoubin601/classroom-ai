package com.classroom.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "classroom_record")
public class ClassroomRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "course_name", length = 64)
    @Builder.Default
    private String courseName = "智能课堂分析";

    @Column(name = "class_name", length = 64)
    private String className;

    @Column(name = "total_expected", nullable = false)
    @Builder.Default
    private Integer totalExpected = 0;

    @Column(name = "actual_present", nullable = false)
    @Builder.Default
    private Integer actualPresent = 0;

    @Column(name = "attendance_rate", nullable = false)
    @Builder.Default
    private Double attendanceRate = 0.0;

    @Column(name = "lookup_rate", nullable = false)
    @Builder.Default
    private Double lookupRate = 0.0;

    @Column(name = "look_down_count", nullable = false)
    @Builder.Default
    private Integer lookDownCount = 0;

    @CreationTimestamp
    @Column(name = "record_time", nullable = false, updatable = false)
    private LocalDateTime recordTime;
}
