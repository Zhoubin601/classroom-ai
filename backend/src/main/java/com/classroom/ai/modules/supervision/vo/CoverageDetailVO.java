package com.classroom.ai.modules.supervision.vo;

import java.util.List;

public record CoverageDetailVO(Long courseId, String courseCode, String courseName,
                               String academicTerm, boolean covered, List<Long> evaluationIds) {}
