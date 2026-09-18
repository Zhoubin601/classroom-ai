package com.classroom.ai.repository;

import com.classroom.ai.entity.ClassroomRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassroomRecordRepository extends JpaRepository<ClassroomRecord, Long> {

    @Query("SELECT r FROM ClassroomRecord r ORDER BY r.recordTime DESC")
    List<ClassroomRecord> findLatestRecords(Pageable pageable);

    List<ClassroomRecord> findBySessionIdOrderByRecordTimeAsc(String sessionId);
}
