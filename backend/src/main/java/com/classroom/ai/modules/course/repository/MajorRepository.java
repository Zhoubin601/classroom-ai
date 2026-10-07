package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MajorRepository extends JpaRepository<Major, Long> {
    Optional<Major> findByMajorCode(String majorCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Major m where m.majorCode = :code")
    Optional<Major> findForUpdate(@org.springframework.data.repository.query.Param("code") String code);
    /** Lead or explicitly associated department; never inferred from arbitrary courses. */
    @org.springframework.data.jpa.repository.Query("""
            select m from Major m where m.department = :department or exists
            (select d.id from MajorDepartment d where d.major = m and d.department = :department)
            order by m.majorCode
            """)
    java.util.List<Major> findByDepartment(@org.springframework.data.repository.query.Param("department") String department);
}
