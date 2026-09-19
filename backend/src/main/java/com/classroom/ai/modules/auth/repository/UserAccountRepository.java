package com.classroom.ai.modules.auth.repository;

import com.classroom.ai.modules.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByTeacherCode(String teacherCode);
    java.util.List<UserAccount> findByRole(com.classroom.ai.modules.auth.entity.RoleEnum role);
}
