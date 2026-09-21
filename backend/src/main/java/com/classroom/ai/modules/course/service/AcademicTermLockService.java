package com.classroom.ai.modules.course.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AcademicTermLockService {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String... terms) {
        // INSERT acquires an exclusive row lock even when the record already exists.
        // Sorting also gives semester transfers a consistent lock order. No unlock before commit.
        Arrays.stream(terms).map(term -> {
            if (term == null || term.isBlank()) throw new IllegalArgumentException("学期不能为空");
            return ScheduleConflictService.key(term);
        }).distinct().sorted().forEach(term -> {
            if (term == null || term.isBlank()) throw new IllegalArgumentException("学期不能为空");
            entityManager.createNativeQuery("INSERT INTO t_academic_term_lock (academic_term) VALUES (:term) ON DUPLICATE KEY UPDATE academic_term = academic_term")
                    .setParameter("term", term).executeUpdate();
        });
    }
}
