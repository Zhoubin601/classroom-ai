package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="s3_action_feedback", uniqueConstraints=@UniqueConstraint(columnNames={"suggestionId","teacherUserId"}))
@Getter @Setter @NoArgsConstructor
public class ActionFeedback {
 private java.time.LocalDateTime touchedAt;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long revision=0L;
 private Long suggestionId;
 private Long teacherUserId;
 private String decision;
 @Column(columnDefinition="TEXT") private String actionText;
 @Column(columnDefinition="TEXT") private String feedbackText;
 @Column(columnDefinition="TEXT") private String evidenceJson;
 @Column(columnDefinition="TEXT") private String stageTagsJson;
}
