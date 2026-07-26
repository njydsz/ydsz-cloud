package com.njydsz.common.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.security.TokenBlacklistBloomFilter;
import com.njydsz.common.auth.util.AuthDigestUtils;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Token 黑名单服务
 *
 * 用户登出后，将 Token 加入 Redis 黑名单，使其失效。
 * 每次请求都需要校验 Token 是否在黑名单中。
 *
 * <p><b>优化：</b>使用 SHA-256 摘要后的 token 作为 Redis key，避免完整 JWT（500+ 字节）
 * 作为 key 浪费内存且可能超过 key 长度限制。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Service
@ConditionalOnBean(RedisStringOps.class)
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "auth:token:blacklist:";
    private static final String REFRESH_LOCK_KEY_PREFIX = "auth:token:refresh-lock:";
    private static final long REFRESH_LOCK_TTL_SECONDS = 10;

    private final RedisStringOps redisStringOps;
    private final AuthProperties authProperties;
    private final TokenBlacklistBloomFilter bloomFilter;

    public TokenBlacklistService(RedisStringOps redisStringOps, AuthProperties authProperties) {
        this.redisStringOps = redisStringOps;
        this.authProperties = authProperties;
        this.bloomFilter = new TokenBlacklistBloomFilter(1_000_000);
    }

    /**
     * 将 Token 的 SHA-256 摘要作为 Redis key，避免完整 JWT 作为 key 浪费内存。
     */
    private String buildBlacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + AuthDigestUtils.sha256Hex(token);
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
        // 同步加入 Bloom Filter，后续查询可前置过滤
        bloomFilter.addToBlacklist(token);
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
        // Bloom Filter 前置过滤：返回 false 时一定不在黑名单中，无需查 Redis
        if (!bloomFilter.mightBeBlacklisted(token)) {
            return false;
        }
        String key = buildBlacklistKey(token);
        return redisStringOps.hasKey(key);
    }

    /**
     * 尝试获取 Token 刷新分布式锁。
     *
     * <p>使用 Redis SET NX EX 实现分布式锁，确保同一 refresh_token 在并发场景下
     * 只能有一个请求成功刷新，防止重放攻击窗口。
     *
     * @param refreshToken 刷新令牌
     * @return 获锁成功返回 true，获取失败（已有其他请求正在刷新）返回 false
     */
    public boolean tryAcquireRefreshLock(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        String lockKey = REFRESH_LOCK_KEY_PREFIX + AuthDigestUtils.sha256Hex(refreshToken);
        try {
            Boolean acquired = redisStringOps.setIfAbsent(lockKey, "1", REFRESH_LOCK_TTL_SECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("获取刷新锁成功: key={}", lockKey);
                return true;
            }
            log.warn("获取刷新锁失败，已有其他请求正在刷新同一 token");
            return false;
        } catch (Exception e) {
            log.error("获取刷新锁异常，降级为允许刷新: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 释放 Token 刷新分布式锁。
     *
     * @param refreshToken 刷新令牌
     */
    public void releaseRefreshLock(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String lockKey = REFRESH_LOCK_KEY_PREFIX + AuthDigestUtils.sha256Hex(refreshToken);
        try {
            redisStringOps.del(lockKey);
        } catch (Exception e) {
            log.debug("释放刷新锁异常（锁会自动过期）: {}", e.getMessage());
        }
    }
}
