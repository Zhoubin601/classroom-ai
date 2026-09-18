package com.classroom.ai.modules.auth.interceptor;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.controller.AuthController;
import com.classroom.ai.modules.auth.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            UserVO user = (UserVO) session.getAttribute(AuthController.SESSION_USER_KEY);
            if (user != null) {
                AuthContext.setCurrentUser(user);

                // CSRF 校验：针对已登录状态下的状态变更请求 (POST, PUT, DELETE, PATCH)
                String method = request.getMethod().toUpperCase();
                if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method)) {
                    // 放行登录接口本身的 POST 请求
                    String uri = request.getRequestURI();
                    if (!uri.endsWith("/auth/login")) {
                        String sessionCsrf = (String) session.getAttribute(AuthController.SESSION_CSRF_KEY);
                        String headerCsrf = request.getHeader("X-CSRF-TOKEN");
                        if (sessionCsrf != null && (headerCsrf == null || !sessionCsrf.equals(headerCsrf))) {
                            throw new ForbiddenException("CSRF 校验失败，拒绝非法跨站请求");
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
