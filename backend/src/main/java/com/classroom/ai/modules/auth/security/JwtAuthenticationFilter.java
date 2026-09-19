package com.classroom.ai.modules.auth.security;

import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.vo.UserVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * JWT 请求认证拦截器：从 Authorization: Bearer <token> 提取令牌并从数据库重载最新权限注入 SecurityContext 与 AuthContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = resolveToken(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                UserVO user = jwtTokenProvider.parseUserFromToken(jwt);
                
                // 关键点：以服务端当前授权为准，避免旧 JWT 长期保留已撤销权限
                if (userAccountRepository != null) {
                    Optional<UserAccount> latestAccountOpt = Optional.empty();
                    if (user.getId() != null) {
                        latestAccountOpt = userAccountRepository.findById(user.getId());
                    }
                    if (latestAccountOpt.isEmpty() && user.getUsername() != null) {
                        latestAccountOpt = userAccountRepository.findByUsername(user.getUsername());
                    }
                    if (latestAccountOpt.isPresent()) {
                        UserAccount acc = latestAccountOpt.get();
                        user = UserVO.builder()
                                .id(acc.getId())
                                .username(acc.getUsername())
                                .realName(acc.getRealName())
                                .role(acc.getRole())
                                .department(acc.getDepartment())
                                .teacherCode(acc.getTeacherCode())
                                .authorizedMajors(acc.getAuthorizedMajors()) // 最新授权专业
                                .build();
                    }
                }

                AuthContext.setCurrentUser(user);

                List<SimpleGrantedAuthority> authorities = Collections.emptyList();
                if (user.getRole() != null) {
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }
}
