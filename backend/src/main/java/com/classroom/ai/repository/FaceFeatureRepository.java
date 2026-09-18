package com.classroom.ai.repository;

import com.classroom.ai.entity.FaceFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaceFeatureRepository extends JpaRepository<FaceFeature, Long> {

    Optional<FaceFeature> findByStudentId(String studentId);

    List<FaceFeature> findAllByStudentIdIn(List<String> studentIds);

    void deleteByStudentId(String studentId);
}
