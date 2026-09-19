package com.classroom.ai.modules.auth.security;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 6 核心安全配置：无状态 JWT 鉴权与细粒度角色权限隔离
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 平滑兼容现有内置与测试账号密码
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword != null ? rawPassword.toString() : "";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (rawPassword == null || encodedPassword == null) return false;
                return rawPassword.toString().equals(encodedPassword);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（因采用无状态 JWT 机制）
            .csrf(AbstractHttpConfigurer::disable)
            // 启用 CORS
            .cors(Customizer.withDefaults())
            // 会话管理设为无状态
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 异常响应处理器：未登录 401 与越权拒绝 403
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    ApiResponse<Void> apiResp = ApiResponse.error(401, "未登录或登录令牌已过期，请重新登录");
                    response.getWriter().write(JSON.toJSONString(apiResp));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    ApiResponse<Void> apiResp = ApiResponse.error(403, "权限拒绝：当前角色无权执行此操作或访问该工作台");
                    response.getWriter().write(JSON.toJSONString(apiResp));
                })
            )
            // 请求路由鉴权规则
            .authorizeHttpRequests(authorize -> authorize
                // 放行跨域预检
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 放行公开认证接口与基础静态资源
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register-supervisor",
                    "/api/v1/auth/csrf",
                    "/uploads/**",
                    "/error",
                    "/v3/api-docs/**",
                    "/swagger-ui/**"
                ).permitAll()
                // 指标点维护写操作：严禁督导访问，仅任课教师和教研室主任可操作
                .requestMatchers(HttpMethod.POST, "/api/v1/syllabus/course/*/indicators").hasAnyRole("TEACHER", "DIRECTOR")
                .requestMatchers(HttpMethod.PUT, "/api/v1/syllabus/indicators/*").hasAnyRole("TEACHER", "DIRECTOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/syllabus/indicators/*").hasAnyRole("TEACHER", "DIRECTOR")
                // 大纲草稿与发布写操作：仅任课教师和教研室主任
                .requestMatchers(HttpMethod.PUT, "/api/v1/courses/*/content/draft").hasAnyRole("TEACHER", "DIRECTOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/content/publish").hasAnyRole("TEACHER", "DIRECTOR")
                // 大纲审查锁定：仅教研室主任
                .requestMatchers(HttpMethod.POST, "/api/v1/syllabus/*/lock").hasRole("DIRECTOR")
                // 督导评课写操作：仅督导和主任
                .requestMatchers(HttpMethod.POST, "/api/v1/supervision/evaluations").hasAnyRole("SUPERVISOR", "DIRECTOR")
                // 学生人脸特征档案库管理：严禁督导或教师越权访问，仅教研室主任专属管辖
                .requestMatchers("/api/student/**").hasRole("DIRECTOR")
                // 其余接口放行或按方法注解管控
                .anyRequest().permitAll()
            )
            // 挂载 JWT 认证拦截过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
