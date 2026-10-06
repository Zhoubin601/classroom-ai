package com.classroom.ai.modules.resource.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_resource_access_log")
@Data
public class ResourceAccessLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long resourceId;
    @Column(nullable = false)
    private Long viewerId;
    @Column(nullable = false, length = 64)
    private String viewerUsername;
    @Column(nullable = false, length = 16)
    private String action;
    @Column(nullable = false)
    private LocalDateTime accessedAt;
}
