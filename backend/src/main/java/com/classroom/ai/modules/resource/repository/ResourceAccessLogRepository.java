package com.classroom.ai.modules.resource.repository;

import com.classroom.ai.modules.resource.entity.ResourceAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceAccessLogRepository extends JpaRepository<ResourceAccessLog, Long> {}
