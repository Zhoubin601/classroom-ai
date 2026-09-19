package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseImportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseImportLogRepository extends JpaRepository<CourseImportLog, Long> {
    Optional<CourseImportLog> findByBatchId(String batchId);
    List<CourseImportLog> findByOperatorOrderByCreatedAtDesc(String operator);
}
