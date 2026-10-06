package com.classroom.ai.modules.supervision.repository;

import com.classroom.ai.modules.supervision.entity.EvaluationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvaluationAuditLogRepository extends JpaRepository<EvaluationAuditLog, Long> {
    List<EvaluationAuditLog> findByEvaluationIdOrderByOccurredAtAsc(Long evaluationId);
}
