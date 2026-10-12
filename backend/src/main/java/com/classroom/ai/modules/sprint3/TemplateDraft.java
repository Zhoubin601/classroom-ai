package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_template_draft")
@Getter @Setter @NoArgsConstructor
public class TemplateDraft {
 private java.time.LocalDateTime touchedAt;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long revision=0L;
 private String name;
 private String scenario;
 @Column(columnDefinition="TEXT") private String descriptions;
 @Column(precision=7,scale=3) private java.math.BigDecimal attitude;
 @Column(precision=7,scale=3) private java.math.BigDecimal content;
 @Column(precision=7,scale=3) private java.math.BigDecimal method;
 @Column(precision=7,scale=3) private java.math.BigDecimal effect;
 private Long basedOnVersionId;
 private Long publishedId;
}
