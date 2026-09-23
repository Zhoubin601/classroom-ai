package com.classroom.ai.modules.supervision.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_evaluation_audit_log")
@Data
public class EvaluationAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long evaluationId;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false, length = 64) private String actorUsername;
    @Column(nullable = false, length = 32) private String action;
    @Column(length = 32) private String fromStatus;
    @Column(nullable = false, length = 32) private String toStatus;
    @Column(nullable = false) private LocalDateTime occurredAt;
}
