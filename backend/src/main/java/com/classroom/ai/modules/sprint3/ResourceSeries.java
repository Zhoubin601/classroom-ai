package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_resource_series")
@Getter @Setter @NoArgsConstructor
public class ResourceSeries {
 private java.time.LocalDateTime touchedAt;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long revision=0L;
 private Long courseId;
 private Long currentVersionId;
 private Boolean shared;
 private Long accessEpoch;
 private Integer nextNumber;
 private Boolean removed;
}
