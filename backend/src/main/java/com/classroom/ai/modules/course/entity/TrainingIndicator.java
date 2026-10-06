package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.Data;

/** A graduation indicator imported from a major's training plan. */
@Entity
@Table(name = "t_training_indicator", uniqueConstraints =
        @UniqueConstraint(name = "uk_training_indicator", columnNames = {"major_code", "plan_version", "indicator_code"}))
@Data
public class TrainingIndicator {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "major_code", nullable = false, length = 32)
    private String majorCode;
    @Column(name = "plan_version", nullable = false, length = 32)
    private String planVersion;
    @Column(name = "indicator_code", nullable = false, length = 32)
    private String indicatorCode;
    @Column(nullable = false, length = 64)
    private String requirementCategory;
    @Column(columnDefinition = "TEXT")
    private String indicatorDescription;
}
