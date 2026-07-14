package com.njydsz.pmis.common.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;

/**
 * Token 黑名单服务
 *
 * 用户登出后，将 Token 加入 Redis 黑名单，使其失效。
 * 每次请求都需要校验 Token 是否在黑名单中。
 *
 * <p><b>优化：</b>使用 SHA-256 摘要后的 token 作为 Redis key，避免完整 JWT（500+ 字节）
 * 作为 key 浪费内存且可能超过 key 长度限制。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Service
@ConditionalOnBean(RedisStringOps.class)
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "auth:token:blacklist:";

    private final RedisStringOps redisStringOps;
    private final AuthProperties authProperties;

    public TokenBlacklistService(RedisStringOps redisStringOps, AuthProperties authProperties) {
        this.redisStringOps = redisStringOps;
        this.authProperties = authProperties;
    }

    /**
     * 将 Token 的 SHA-256 摘要作为 Redis key，避免完整 JWT 作为 key 浪费内存。
     */
    private String buildBlacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + sha256(token);
    }

    /**
     * 计算 SHA-256 摘要并转为十六进制字符串。
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 内置算法，理论上不会缺失
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 将 Token 加入黑名单（登出）
     *
     * @param token JWT Token
     */
    public void addToBlacklist(String token) {
        if (!authProperties.getBlacklist().isEnabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            return;
        }
        String key = buildBlacklistKey(token);
        long expire = authProperties.getBlacklist().getExpireSeconds();
        redisStringOps.set(key, "1", Duration.ofSeconds(expire));
        log.info("Token added to blacklist, expires in {}s", expire);
    }

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT Token
     * @return true 表示在黑名单中
     */
    public boolean isBlacklisted(String token) {
        if (!authProperties.getBlacklist().isEnabled()) {
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = buildBlacklistKey(token);
        return redisStringOps.hasKey(key);
    }
}
