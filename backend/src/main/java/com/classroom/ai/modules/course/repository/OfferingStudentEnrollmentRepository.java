package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.OfferingStudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfferingStudentEnrollmentRepository extends JpaRepository<OfferingStudentEnrollment, Long> {
    List<OfferingStudentEnrollment> findByOfferingId(Long offeringId);
    long countByOfferingId(Long offeringId);
    void deleteByOfferingId(Long offeringId);
    void deleteByOfferingIdAndStudentNumber(Long offeringId, String studentNumber);
    boolean existsByOfferingIdAndStudentNumber(Long offeringId, String studentNumber);
}
