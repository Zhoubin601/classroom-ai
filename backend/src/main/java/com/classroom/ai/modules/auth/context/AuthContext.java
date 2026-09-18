package com.classroom.ai.modules.auth.context;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;

import java.util.Arrays;

/**
 * 请求上下文用户信息持有者
 */
public class AuthContext {

    private static final ThreadLocal<UserVO> CURRENT_USER = new ThreadLocal<>();

    public static void setCurrentUser(UserVO user) {
        CURRENT_USER.set(user);
    }

    public static UserVO getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    public static boolean isAuthenticated() {
        return CURRENT_USER.get() != null;
    }

    public static void requireAuthenticated() {
        if (!isAuthenticated()) {
            throw new UnauthorizedException("未登录或会话已过期，请先登录");
        }
    }

    public static void requireRole(RoleEnum... allowedRoles) {
        requireAuthenticated();
        UserVO user = CURRENT_USER.get();
        boolean match = Arrays.stream(allowedRoles).anyMatch(r -> r == user.getRole());
        if (!match) {
            throw new ForbiddenException("当前角色无权执行此操作 (" + user.getRole() + ")");
        }
    }
}
