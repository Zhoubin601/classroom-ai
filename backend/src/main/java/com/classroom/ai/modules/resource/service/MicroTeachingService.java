package com.classroom.ai.modules.resource.service;

import com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;

import java.util.List;

public interface MicroTeachingService {
    List<MicroTeachingSlice> getSlicesByCourseId(Long courseId);
    List<MicroTeachingSlice> getSlicesByStage(String stage);
    MicroTeachingSlice mountSlice(MicroTeachingSliceDTO dto);
    void deleteSlice(Long id);
}
