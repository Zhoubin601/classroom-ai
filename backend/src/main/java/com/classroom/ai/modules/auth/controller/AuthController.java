package com.classroom.ai.modules.auth.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.dto.LoginDTO;
import com.classroom.ai.modules.auth.dto.SupervisorRegisterDTO;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.JwtTokenProvider;
import com.classroom.ai.modules.auth.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    public static final String SESSION_USER_KEY = "LOGIN_USER";
    public static final String SESSION_CSRF_KEY = "CSRF_TOKEN";

    private final UserAccountRepository userAccountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ApiResponse<UserVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            return ApiResponse.error(400, "用户名和密码均不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(dto.getUsername().trim())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        // 基于 PasswordEncoder 校验（兼容 BCrypt 与旧明文）
        if (!passwordEncoder.matches(dto.getPassword().trim(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        // 若历史密码仍为旧明文存储，在验证成功后自动无缝升级为 BCrypt 哈希持久化
        if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$") && !user.getPassword().startsWith("$2y$")) {
            user.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
            userAccountRepository.save(user);
            log.info("【密码安全迁移】账号 [{}] 的明文密码已成功透明升级为 BCrypt 哈希", user.getUsername());
        }

        UserVO vo = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .department(user.getDepartment())
                .teacherCode(user.getTeacherCode())
                .authorizedMajors(user.getAuthorizedMajors())
                .build();

        // 基于 JJWT 签发无状态认证令牌
        String token = jwtTokenProvider.generateToken(vo);
        vo.setToken(token);

        return ApiResponse.success("登录成功", vo);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AuthContext.clear();
        return ApiResponse.success("已成功登出", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        if (AuthContext.isAuthenticated()) {
            return ApiResponse.success(AuthContext.getCurrentUser());
        }

        // 尝试从 Bearer Token 解析
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(token)) {
                UserVO vo = jwtTokenProvider.parseUserFromToken(token);
                return ApiResponse.success(vo);
            }
        }

        throw new UnauthorizedException("未登录或会话已过期");
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> getCsrfToken() {
        return ApiResponse.success(Map.of("csrfToken", UUID.randomUUID().toString()));
    }
}
