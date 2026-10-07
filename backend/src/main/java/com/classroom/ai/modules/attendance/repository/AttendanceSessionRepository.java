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

    List<AttendanceSession> findByStatusOrderByCreatedAtDescIdDesc(String status);
    List<AttendanceSession> findByStatusOrderByCreatedAtDesc(String status);

    @org.springframework.data.jpa.repository.Query("SELECT s.offering.id FROM AttendanceSession s WHERE s.id = :id")
    Optional<Long> findOfferingId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM AttendanceSession s WHERE s.id = :id")
    Optional<AttendanceSession> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
