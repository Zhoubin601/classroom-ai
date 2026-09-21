package com.classroom.ai.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

/** Durable InnoDB lock key. Rows are retained so all application instances share the same lock. */
@Entity
@Table(name = "t_academic_term_lock")
@Data
@NoArgsConstructor
public class AcademicTermLock {
    @Id
    @Column(length = 32)
    private String academicTerm;
}
