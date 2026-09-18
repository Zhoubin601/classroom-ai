package com.classroom.ai.modules.supervision.repository;

import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupervisionEvaluationRepository extends JpaRepository<SupervisionEvaluation, Long> {

    List<SupervisionEvaluation> findByOfferingId(Long offeringId);

    List<SupervisionEvaluation> findBySupervisorName(String supervisorName);

    @Query("SELECT e FROM SupervisionEvaluation e WHERE e.offering.teacherName = :teacherName")
    List<SupervisionEvaluation> findByTeacherName(@Param("teacherName") String teacherName);

    @Query("SELECT e FROM SupervisionEvaluation e WHERE e.offering.course.id = :courseId")
    List<SupervisionEvaluation> findByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT e.offering.course.id FROM SupervisionEvaluation e WHERE e.status != 'DRAFT'")
    List<Long> findSupervisedCourseIds();

    @Query("SELECT AVG(e.totalScore) FROM SupervisionEvaluation e WHERE e.offering.course.id = :courseId AND e.status != 'DRAFT'")
    Double getAvgScoreByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT AVG(e.totalScore) FROM SupervisionEvaluation e WHERE e.offering.teacherName = :teacherName AND e.status != 'DRAFT'")
    Double getAvgScoreByTeacherName(@Param("teacherName") String teacherName);
}
