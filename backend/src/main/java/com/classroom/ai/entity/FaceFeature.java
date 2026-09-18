package com.classroom.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "face_feature")
public class FaceFeature implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "feature_dim", nullable = false)
    @Builder.Default
    private Integer featureDim = 512;

    @Lob
    @Column(name = "feature_vector", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String featureVector; // 512维浮点数 JSON 数组: "[0.123, -0.456, ...]"

    @Column(name = "image_path", length = 255)
    private String imagePath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
