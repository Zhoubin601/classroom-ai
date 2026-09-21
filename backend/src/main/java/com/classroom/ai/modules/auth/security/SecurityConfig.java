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
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                if (rawPassword == null) return "";
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (rawPassword == null || encodedPassword == null) return false;
                // 若为标准 BCrypt 哈希
                if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
                    return bcrypt.matches(rawPassword, encodedPassword);
                }
                // 兼容旧明文密码
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
                    ApiResponse<Void> apiResp = ApiResponse.error(403, "权限拒绝：当前角色无权执行此操作或访问该资源");
                    response.getWriter().write(JSON.toJSONString(apiResp));
                })
            )
            // 请求路由鉴权规则 (Deny by Default)
            .authorizeHttpRequests(authorize -> authorize
                // 放行跨域预检
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 放行公开登录认证接口与基础静态资源 (关闭公开注册 register-supervisor)
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/csrf",
                    "/uploads/**",
                    "/error",
                    "/v3/api-docs/**",
                    "/swagger-ui/**"
                ).permitAll()
                // 主任专属管理路由：督导建档授权与学生底库管理
                .requestMatchers("/api/v1/director/**").hasRole("DIRECTOR")
                .requestMatchers("/api/student/**").hasRole("DIRECTOR")
                // 指标点维护写操作：仅任课教师和教研室主任
                .requestMatchers(HttpMethod.POST, "/api/v1/syllabus/course/*/indicators").hasAnyRole("TEACHER", "DIRECTOR")
                .requestMatchers(HttpMethod.PUT, "/api/v1/syllabus/indicators/*").hasAnyRole("TEACHER", "DIRECTOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/syllabus/indicators/*").hasAnyRole("TEACHER", "DIRECTOR")
                // 大纲草稿与发布写操作：仅任课教师和教研室主任
                .requestMatchers(HttpMethod.GET, "/api/v1/courses/*/content/draft").hasRole("TEACHER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/courses/*/content/draft").hasRole("TEACHER")
                .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/content/publish").hasRole("TEACHER")
                // 大纲审查锁定：仅教研室主任
                .requestMatchers(HttpMethod.POST, "/api/v1/syllabus/*/lock").hasRole("DIRECTOR")
                // 督导评课写操作：仅督导和主任
                .requestMatchers(HttpMethod.POST, "/api/v1/supervision/evaluations").hasAnyRole("SUPERVISOR", "DIRECTOR")
                // 课程档案只能由主任维护；资源范围仍由服务端业务规则检查。
                .requestMatchers("/api/v1/courses/import/**").hasRole("DIRECTOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/courses").hasRole("DIRECTOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/*").hasRole("DIRECTOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/courses/offerings", "/api/v1/courses/offerings/*/students/**", "/api/v1/courses/offerings/*/archive", "/api/v1/schedules").hasRole("DIRECTOR")
                .requestMatchers(HttpMethod.PUT, "/api/v1/courses/offerings/*").hasRole("DIRECTOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/offerings/*", "/api/v1/schedules/*").hasRole("DIRECTOR")
                .requestMatchers("/api/v1/schedules/check-conflict").hasRole("DIRECTOR")
                // 实验二核心教务业务接口全量强制要求认证登录
                .requestMatchers("/api/v1/courses/**").authenticated()
                .requestMatchers("/api/v1/syllabus/**").authenticated()
                .requestMatchers("/api/v1/schedules/**").authenticated()
                .requestMatchers("/api/v1/supervision/**").authenticated()
                .requestMatchers("/api/v1/resources/**").authenticated()
                .requestMatchers("/api/v1/attendance/**").authenticated()
                .requestMatchers("/api/visual/**").authenticated()
                // 默认策略：其余任何请求均须登录认证通过
                .anyRequest().authenticated()
            )
            // 挂载 JWT 认证拦截过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
