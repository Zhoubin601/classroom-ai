package com.classroom.ai.modules.auth.security;

import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌生成与验签核心工具类 (基于 JJWT 0.12.5)
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String DEFAULT_SECRET = "ClassroomAiEmbodiedRobotTeachingManagementPlatformSecretKey2026SprintOne";
    private static final long DEFAULT_EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24小时有效

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${classroom.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
            @Value("${classroom.jwt.expiration-ms:" + DEFAULT_EXPIRATION_MS + "}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 根据用户实体/VO 生成标准 JWT 字符串
     */
    public String generateToken(UserVO user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("id", user.getId())
                .claim("role", user.getRole() != null ? user.getRole().name() : "")
                .claim("realName", user.getRealName())
                .claim("department", user.getDepartment())
                .claim("teacherCode", user.getTeacherCode())
                .claim("authorizedMajors", user.getAuthorizedMajors())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 JWT Token 并提取 Claims
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中还原 UserVO
     */
    public UserVO parseUserFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Number idNum = claims.get("id", Number.class);
        Long id = idNum != null ? idNum.longValue() : null;
        String username = claims.getSubject();
        String roleStr = claims.get("role", String.class);
        RoleEnum role = roleStr != null && !roleStr.isEmpty() ? RoleEnum.valueOf(roleStr) : null;
        String realName = claims.get("realName", String.class);
        String department = claims.get("department", String.class);
        String teacherCode = claims.get("teacherCode", String.class);
        String authorizedMajors = claims.get("authorizedMajors", String.class);

        return UserVO.builder()
                .id(id)
                .username(username)
                .role(role)
                .realName(realName)
                .department(department)
                .teacherCode(teacherCode)
                .authorizedMajors(authorizedMajors)
                .token(token)
                .build();
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT 签名校验或解析失败: {}", e.getMessage());
            return false;
        }
    }
}
