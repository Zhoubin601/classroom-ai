package com.classroom.ai.modules.auth;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.controller.DirectorController;
import com.classroom.ai.modules.auth.dto.SupervisorRegisterDTO;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.MajorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectorSupervisorManagementTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private MajorRepository majorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DirectorController directorController;

    @BeforeEach
    void setUp() {
        directorController = new DirectorController(userAccountRepository, majorRepository, passwordEncoder);

        // 主任管辖软件工程教研室
        AuthContext.setCurrentUser(UserVO.builder()
                .id(10L)
                .username("director.se")
                .realName("李主任")
                .role(RoleEnum.DIRECTOR)
                .department("软件工程教研室")
                .build());

        when(majorRepository.findByDepartment("软件工程教研室")).thenReturn(List.of(
                Major.builder().majorCode("SE").majorName("软件工程").department("软件工程教研室").build()
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("主任创建督导：分配自己管辖的专业 SE 成功")
    void testCreateSupervisor_SuccessWithManagedMajor() {
        SupervisorRegisterDTO dto = SupervisorRegisterDTO.builder()
                .username("new_sup")
                .password("123456")
                .realName("王督导")
                .authorizedMajors("SE")
                .build();

        when(userAccountRepository.findByUsername("new_sup")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encrypted");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<UserVO> resp = directorController.createSupervisor(dto);
        assertNotNull(resp);
        assertEquals(200, resp.getCode());
        assertEquals("new_sup", resp.getData().getUsername());
        assertEquals("SE", resp.getData().getAuthorizedMajors());
        assertEquals(RoleEnum.SUPERVISOR, resp.getData().getRole());
    }

    @Test
    @DisplayName("主任创建督导越权：分配非本教研室专业 CS 抛出 403 Forbidden")
    void testCreateSupervisor_UnauthorizedMajor_Throws403() {
        SupervisorRegisterDTO dto = SupervisorRegisterDTO.builder()
                .username("new_sup_cs")
                .password("123456")
                .realName("跨专业督导")
                .authorizedMajors("SE;CS") // 包含 CS
                .build();

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> directorController.createSupervisor(dto));
        assertTrue(ex.getMessage().contains("教研室主任仅能分配本教研室管辖的专业"));
        assertTrue(ex.getMessage().contains("CS"));
    }

    @Test
    @DisplayName("督导专业再授权越权拦截：主任尝试追加未管辖专业时抛出 403")
    void testAssignMajors_UnauthorizedMajor_Throws403() {
        UserAccount supervisor = UserAccount.builder()
                .id(20L)
                .username("sup.test")
                .role(RoleEnum.SUPERVISOR)
                .build();
        when(userAccountRepository.findById(20L)).thenReturn(Optional.of(supervisor));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            directorController.updateSupervisorMajors(20L, java.util.Map.of("authorizedMajors", "CS"));
        });
        assertTrue(ex.getMessage().contains("教研室主任仅能分配本教研室管辖的专业"));
    }

    @Test
    @DisplayName("督导专业再授权成功：主任更新自己管辖范围内的专业")
    void testUpdateSupervisorMajors_Success() {
        UserAccount supervisor = UserAccount.builder()
                .id(20L)
                .username("sup.test")
                .role(RoleEnum.SUPERVISOR)
                .authorizedMajors("NONE")
                .build();
        when(userAccountRepository.findById(20L)).thenReturn(Optional.of(supervisor));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<UserVO> resp = directorController.updateSupervisorMajors(20L, java.util.Map.of("authorizedMajors", "SE"));
        assertNotNull(resp);
        assertEquals(200, resp.getCode());
        assertEquals("SE", resp.getData().getAuthorizedMajors());
    }
}