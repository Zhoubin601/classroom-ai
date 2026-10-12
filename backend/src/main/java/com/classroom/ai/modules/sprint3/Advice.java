package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_advice")
@Getter @Setter @NoArgsConstructor
public class Advice {
 private java.time.LocalDateTime touchedAt;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long revision=0L;
 private Long evaluationId;
 @Column(columnDefinition="TEXT") private String text;
 @Column(columnDefinition="TEXT") private String evidenceJson;
 @Column(columnDefinition="TEXT") private String stageTagsJson;
 private Long createdBy;
 private boolean defaultGenerated;
}
