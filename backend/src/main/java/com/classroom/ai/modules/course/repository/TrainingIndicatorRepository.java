package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.TrainingIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrainingIndicatorRepository extends JpaRepository<TrainingIndicator, Long> {
    @org.springframework.data.jpa.repository.Query("select distinct t.planVersion from TrainingIndicator t where t.majorCode = :majorCode order by t.planVersion")
    List<String> findPlanVersions(@org.springframework.data.repository.query.Param("majorCode") String majorCode);
    List<TrainingIndicator> findByMajorCodeAndPlanVersionOrderByIndicatorCode(String majorCode, String planVersion);
    Optional<TrainingIndicator> findByMajorCodeAndPlanVersionAndIndicatorCode(String majorCode, String planVersion, String indicatorCode);
}
