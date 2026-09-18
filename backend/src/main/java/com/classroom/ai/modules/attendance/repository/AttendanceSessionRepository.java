package com.classroom.ai.modules.attendance.repository;

import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByOfferingId(Long offeringId);

    Optional<AttendanceSession> findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(Long offeringId, String status);

    Optional<AttendanceSession> findFirstByStatusOrderByCreatedAtDesc(String status);
}
