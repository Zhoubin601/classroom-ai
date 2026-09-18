package com.classroom.ai.modules.auth.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.dto.LoginDTO;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    public static final String SESSION_USER_KEY = "LOGIN_USER";
    public static final String SESSION_CSRF_KEY = "CSRF_TOKEN";

    private final UserAccountRepository userAccountRepository;

    @PostMapping("/login")
    public ApiResponse<UserVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            return ApiResponse.error(400, "用户名和密码均不能为空");
        }

        UserAccount user = userAccountRepository.findByUsername(dto.getUsername().trim())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        if (!user.getPassword().equals(dto.getPassword().trim())) {
            throw new UnauthorizedException("用户名或密码错误");
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

        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_KEY, vo);
        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF_KEY, csrfToken);

        return ApiResponse.success("登录成功", vo);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        AuthContext.clear();
        return ApiResponse.success("已成功登出", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthorizedException("未登录或会话已过期");
        }
        UserVO vo = (UserVO) session.getAttribute(SESSION_USER_KEY);
        if (vo == null) {
            throw new UnauthorizedException("未登录或会话已过期");
        }
        return ApiResponse.success(vo);
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> getCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String csrf = (String) session.getAttribute(SESSION_CSRF_KEY);
        if (csrf == null) {
            csrf = UUID.randomUUID().toString();
            session.setAttribute(SESSION_CSRF_KEY, csrf);
        }
        return ApiResponse.success(Map.of("csrfToken", csrf));
    }
}
