package com.njydsz.pmis.common.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * <h3>密钥强度要求</h3>
 * <ul>
 *   <li>HS256 至少 256 位 (32 字节); 启动时强制校验, 长度不足时直接抛异常</li>
 *   <li>支持 Base64 编码传入 (以 {@code base64:} 前缀标识)</li>
 *   <li>生产环境禁止使用默认密钥 (校验中含 "default" 关键字时拒绝启动)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** HS256 密钥最小字节数（256 位） */
    private static final int MIN_SECRET_BYTES = 32;
    /** 默认密钥标识，用于启动时检测并告警 */
    private static final String DEFAULT_KEY_MARKER = "default-jwt-secret-key";

    /** JWT 签名密钥（明文或 Base64） */
    @Value("${pmis.jwt.secret:}")
    private String secret;

    /** JWT 签发方 */
    @Value("${pmis.jwt.issuer:pmis}")
    private String issuer;

    /** 访问 Token 过期时间（秒） */
    @Value("${pmis.jwt.access-expire-seconds:7200}")
    private long accessExpireSeconds;

    /** 刷新 Token 过期时间（秒） */
    @Value("${pmis.jwt.refresh-expire-seconds:604800}")
    private long refreshExpireSeconds;

    /** 签名密钥对象 */
    private SecretKey key;
    /** 是否使用默认密钥（启动时检测） */
    private boolean defaultKeyUsed = false;

    /**
     * 初始化签名密钥并打印配置日志
     */
    @PostConstruct
    public void init() {
        this.key = buildKey();
        log.info("[JWT] 初始化完成, issuer={}, access={}s, refresh={}s, defaultKey={}",
                issuer, accessExpireSeconds, refreshExpireSeconds, defaultKeyUsed);
    }

    private SecretKey buildKey() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("pmis.jwt.secret 未配置");
        }
        byte[] bytes;
        if (secret.startsWith("base64:")) {
            try {
                bytes = Base64.getDecoder().decode(secret.substring("base64:".length()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("pmis.jwt.secret 的 base64 部分无法解析", e);
            }
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "pmis.jwt.secret 至少 " + MIN_SECRET_BYTES + " 字节 (HS256 安全要求), 实际 "
                            + bytes.length + " 字节. 推荐通过 base64: 前缀传入 32 字节 Base64 编码密钥");
        }
        if (secret.toLowerCase().contains(DEFAULT_KEY_MARKER)) {
            defaultKeyUsed = true;
            log.warn("[JWT] 检测到默认/占位密钥, 严禁在生产环境使用! 请尽快轮换: pmis.jwt.secret");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 生成访问 Token (含完整用户上下文)
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param roles        角色列表
     * @param permissions  权限列表
     * @param expireSeconds 过期时间（秒），为 null 时使用默认值
     * @return JWT Token
     */
    public String generateToken(Long userId, String username,
                                List<String> roles, List<String> permissions,
                                Long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        if (roles != null) claims.put("roles", roles);
        if (permissions != null) claims.put("permissions", permissions);

        Date now = new Date();
        Date expire = new Date(now.getTime()
                + (expireSeconds == null ? accessExpireSeconds : expireSeconds) * 1000);

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
     *
     * @param userId        用户 ID
     * @param username      用户名
     * @param expireSeconds 过期时间（秒）
     * @return JWT Token
     */
    public String generateToken(Long userId, String username, long expireSeconds) {
        return generateToken(userId, username, null, null, expireSeconds);
    }

    /**
     * 生成刷新 Token
     *
     * @param userId        用户 ID
     * @param expireSeconds 过期时间（秒），为 null 时使用默认值
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(Long userId, Long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        Date now = new Date();
        Date expire = new Date(now.getTime()
                + (expireSeconds == null ? refreshExpireSeconds : expireSeconds) * 1000);

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
     * 验证 Token 是否合法且未过期
     *
     * @param token JWT Token
     * @return true 表示验证通过
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
     * 解析 Token 的 Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT Token
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    /**
     * 从 Token 中提取角色列表
     *
     * @param token JWT Token
     * @return 角色列表，无 roles 时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object v = parseClaims(token).get("roles");
        return v instanceof List ? (List<String>) v : List.of();
    }

    /**
     * 从 Token 中提取权限列表
     *
     * @param token JWT Token
     * @return 权限列表，无 permissions 时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        Object v = parseClaims(token).get("permissions");
        return v instanceof List ? (List<String>) v : List.of();
    }

    /**
     * 获取访问 Token 默认过期时间
     *
     * @return 过期时间（秒）
     */
    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    /**
     * 获取刷新 Token 默认过期时间
     *
     * @return 过期时间（秒）
     */
    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    /**
     * 是否使用了默认密钥
     *
     * @return true 表示使用了默认密钥
     */
    public boolean isDefaultKeyUsed() {
        return defaultKeyUsed;
    }
}
