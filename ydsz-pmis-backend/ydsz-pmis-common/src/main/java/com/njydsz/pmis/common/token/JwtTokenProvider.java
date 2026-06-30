package com.njydsz.pmis.common.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT Token 工具 (Common)
 *
 * <p>支持自定义 Claims：username/roles/permissions/deptId/dataScope。
 * 部署在 common 模块,网关 / auth / 其他服务均可解析同一份 Token。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${pmis.jwt.secret:pmis-default-jwt-secret-key-please-change-in-production-environment-must-be-256-bits}")
    private String secret;

    @Value("${pmis.jwt.issuer:pmis}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问 Token (含完整用户上下文)
     */
    public String generateToken(Long userId, String username,
                                List<String> roles, List<String> permissions,
                                long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        if (roles != null) claims.put("roles", roles);
        if (permissions != null) claims.put("permissions", permissions);

        Date now = new Date();
        Date expire = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /**
     * 兼容旧签名: 仅 userId + username
     */
    public String generateToken(Long userId, String username, long expireSeconds) {
        return generateToken(userId, username, null, null, expireSeconds);
    }

    /**
     * 生成刷新 Token
     */
    public String generateRefreshToken(Long userId, long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        Date now = new Date();
        Date expire = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("[JWT] Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Claims
     */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object v = parseClaims(token).get("roles");
        return v instanceof List ? (List<String>) v : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        Object v = parseClaims(token).get("permissions");
        return v instanceof List ? (List<String>) v : List.of();
    }
}
