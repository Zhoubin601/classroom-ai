package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_video_asset")
@Getter @Setter @NoArgsConstructor
public class VideoAsset {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long courseId;
 private String storedName;
 private String sha256;
 private Long durationMs;
 private String sourceType;
 private String bopppsStage;
 private String sourceDescription;
 @Column(columnDefinition="TEXT") private String sourceReference;
 @Column(columnDefinition="TEXT") private String authorizationDeclaration;
 private Long submittedBy;
 private java.time.LocalDateTime submittedAt;
}
