package com.classroom.ai.modules.auth.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.dto.SupervisorRegisterDTO;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.MajorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 教研室主任专属管理控制器 (DirectorController)
 * 职责：
 * 1. 督导账号建档与专业授权入口；
 * 2. 严格限制主任只能分配自己管辖教研室的专业；
 * 3. 授权变更持久化后，督导端当次请求通过服务端数据库即刻生效。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/director")
@RequiredArgsConstructor
@CrossOrigin
@PreAuthorize("hasRole('DIRECTOR')")
public class DirectorController {

    private final UserAccountRepository userAccountRepository;
    private final MajorRepository majorRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取当前主任管辖的专业列表
     */
    @GetMapping("/managed-majors")
    public ApiResponse<List<Major>> getManagedMajors() {
        UserVO currentUser = getCurrentDirector();
        List<Major> majors = getMajorsForDirector(currentUser);
        return ApiResponse.success(majors);
    }

    /**
     * 获取所有教学督导专家账号列表
     */
    @GetMapping("/supervisors")
    public ApiResponse<List<UserVO>> getSupervisors() {
        getCurrentDirector(); // 校验主任身份
        List<UserAccount> supervisors = userAccountRepository.findByRole(RoleEnum.SUPERVISOR);
        List<UserVO> vos = supervisors.stream().map(s -> UserVO.builder()
                .id(s.getId())
                .username(s.getUsername())
                .realName(s.getRealName())
                .role(s.getRole())
                .department(s.getDepartment())
                .authorizedMajors(s.getAuthorizedMajors())
                .build()
        ).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * 主任创建督导专家账号并赋予管辖专业
     */
    @PostMapping("/supervisors")
    public ApiResponse<UserVO> createSupervisor(@RequestBody SupervisorRegisterDTO dto) {
        UserVO director = getCurrentDirector();
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword()) || !StringUtils.hasText(dto.getRealName())) {
            throw new IllegalArgumentException("督导账号、密码和专家姓名不能为空");
        }

        String username = dto.getUsername().trim();
        if (userAccountRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("该用户名 [" + username + "] 已存在");
        }

        // 校验分配的专业是否在主任管辖范围内
        String requestedMajors = dto.getAuthorizedMajors();
        validateMajorsInDirectorJurisdiction(director, requestedMajors);

        String encodedPwd = passwordEncoder.encode(dto.getPassword().trim());
        UserAccount supervisor = UserAccount.builder()
                .username(username)
                .password(encodedPwd)
                .realName(dto.getRealName().trim())
                .role(RoleEnum.SUPERVISOR)
                .department(StringUtils.hasText(dto.getDepartment()) ? dto.getDepartment().trim() : "校教学质量督导团")
                .authorizedMajors(requestedMajors != null ? requestedMajors.trim() : "")
                .build();

        UserAccount saved = userAccountRepository.save(supervisor);
        log.info("【主任创建督导成功】主任: {}, 新督导: {}, 授权专业: {}", director.getRealName(), saved.getUsername(), saved.getAuthorizedMajors());

        UserVO vo = UserVO.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .realName(saved.getRealName())
                .role(saved.getRole())
                .department(saved.getDepartment())
                .authorizedMajors(saved.getAuthorizedMajors())
                .build();
        return ApiResponse.success("督导账号创建与授权成功", vo);
    }

    /**
     * 主任调整已有督导的专业授权 (仅能调整主任自己管辖的专业)
     */
    @PutMapping("/supervisors/{id}/majors")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<UserVO> updateSupervisorMajors(@PathVariable Long id, @RequestBody Map<String, String> body) {
        UserVO director = getCurrentDirector();
        UserAccount supervisor = userAccountRepository.findForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到督导账号 (ID: " + id + ")"));
        if (supervisor.getRole() != RoleEnum.SUPERVISOR) {
            throw new IllegalArgumentException("指定账号非督导专家角色");
        }

        String newMajors = body.get("authorizedMajors");
        validateMajorsInDirectorJurisdiction(director, newMajors);

        Set<String> managed = getMajorsForDirector(director).stream()
                .map(m -> m.getMajorCode().trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> merged = new TreeSet<>();
        for (String code : Optional.ofNullable(supervisor.getAuthorizedMajors()).orElse("").split(";")) {
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty() && !managed.contains(normalized)) merged.add(normalized);
        }
        for (String code : Optional.ofNullable(newMajors).orElse("").split(";")) {
            if (!code.isBlank()) merged.add(code.trim().toUpperCase(Locale.ROOT));
        }
        supervisor.setAuthorizedMajors(String.join(";", merged));
        UserAccount saved = userAccountRepository.save(supervisor);
        log.info("【主任更新督导专业授权】主任: {}, 督导: {}, 最新授权: {}", director.getRealName(), saved.getUsername(), saved.getAuthorizedMajors());

        UserVO vo = UserVO.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .realName(saved.getRealName())
                .role(saved.getRole())
                .department(saved.getDepartment())
                .authorizedMajors(saved.getAuthorizedMajors())
                .build();
        return ApiResponse.success("督导专业授权更新成功", vo);
    }

    private UserVO getCurrentDirector() {
        if (!AuthContext.isAuthenticated() || AuthContext.getCurrentUser() == null) {
            throw new UnauthorizedException("未登录或会话已过期");
        }
        UserVO user = AuthContext.getCurrentUser();
        if (user.getRole() != RoleEnum.DIRECTOR) {
            throw new ForbiddenException("权限拒绝：仅教研室主任可执行此操作");
        }
        return user;
    }

    private List<Major> getMajorsForDirector(UserVO director) {
        // 与课程建档使用同一明确归属，不根据专业名/教研室关键字猜测权限。
        if (director.getDepartment() == null || director.getDepartment().isBlank()) return List.of();
        return majorRepository.findByDepartment(director.getDepartment().trim());
    }

    private void validateMajorsInDirectorJurisdiction(UserVO director, String requestedMajorsStr) {
        if (!StringUtils.hasText(requestedMajorsStr)) {
            return;
        }
        List<Major> managed = getMajorsForDirector(director);
        Set<String> managedCodes = managed.stream().map(m -> m.getMajorCode().toUpperCase()).collect(Collectors.toSet());

        String[] parts = requestedMajorsStr.split(";");
        for (String p : parts) {
            String code = p.trim().toUpperCase();
            if (!code.isEmpty() && !managedCodes.contains(code)) {
                throw new ForbiddenException("越权拦截：教研室主任仅能分配本教研室管辖的专业 (" + String.join(", ", managedCodes) + ")，无权授权其他专业 [" + code + "]");
            }
        }
    }
}
