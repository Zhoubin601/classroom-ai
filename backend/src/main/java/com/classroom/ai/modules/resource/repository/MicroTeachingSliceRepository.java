package com.classroom.ai.modules.resource.repository;

import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MicroTeachingSliceRepository extends JpaRepository<MicroTeachingSlice, Long> {

    List<MicroTeachingSlice> findByCourseId(Long courseId);

    List<MicroTeachingSlice> findByBopppsStage(String bopppsStage);
}
