package com.classroom.ai.modules.auth.repository;

import com.classroom.ai.modules.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByTeacherCode(String teacherCode);
    java.util.List<UserAccount> findByRole(com.classroom.ai.modules.auth.entity.RoleEnum role);
}
