package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_feedback_revision")
@Getter @Setter @NoArgsConstructor
public class FeedbackRevision {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long feedbackId;
 private Long recordVersion;
 private Long actorId;
 private java.time.LocalDateTime occurredAt;
 @Column(columnDefinition="TEXT") private String snapshotJson;
}
