package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

/** Explicit participating departments; Major.department remains the lead department. */
@Entity
@Table(name = "t_major_department", uniqueConstraints =
        @UniqueConstraint(name = "uk_major_department", columnNames = {"major_id", "department"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MajorDepartment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    @Column(nullable = false, length = 64)
    private String department;
}
