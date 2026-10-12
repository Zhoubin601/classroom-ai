package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_template_version", uniqueConstraints=@UniqueConstraint(columnNames={"family_id","number"}))
@Getter @Setter @NoArgsConstructor
public class TemplateVersion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long familyId;
 private Long draftId;
 private Integer number;
 private String name;
 private String scenario;
 @Column(columnDefinition="TEXT") private String descriptions;
 @Column(precision=7,scale=3) private java.math.BigDecimal attitude;
 @Column(precision=7,scale=3) private java.math.BigDecimal content;
 @Column(precision=7,scale=3) private java.math.BigDecimal method;
 @Column(precision=7,scale=3) private java.math.BigDecimal effect;
 private Long publishedBy;
 private java.time.LocalDateTime publishedAt;
}
