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
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Token 黑名单服务
 *
 * <p>用户登出后，将 Token 加入 Redis 黑名单使其失效；每次请求都需要校验 Token 是否在黑名单中。
 * 同时支持 refresh_token 刷新场景的分布式锁，避免并发刷新导致的 token 重放风险。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>登出失效</b>：用户主动登出时把 access_token 加入黑名单</li>
 *   <li><b>请求校验</b>：每个请求到达时检查 token 是否已被拉黑</li>
 *   <li><b>刷新保护</b>：refresh_token 刷新前获取分布式锁，防止并发重放</li>
 * </ul>
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>使用 SHA-256 摘要后的 token 作为 Redis key，避免完整 JWT（500+ 字节）
 *       作为 key 浪费内存且可能超过 key 长度限制</li>
 *   <li>本地 Bloom Filter 前置过滤：99.99% 非黑名单 token 无需访问 Redis</li>
 * </ul>
 *
 * <p><b>激活条件：</b>当 {@link RedisStringOps} Bean 存在时自动激活，
 * 无 Redis 时 {@link AuthFilterConfiguration} 走降级方案。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see AuthProperties.Blacklist 黑名单配置
 * @see TokenBlacklistBloomFilter 本地 Bloom Filter
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

    /**
     * 构造 Token 黑名单服务。
     *
     * <p>同时初始化 Bloom Filter，预期容量 100 万 token，约占 1.2 MB 内存。
     *
     * @param redisStringOps Redis 字符串操作服务
     * @param authProperties 认证配置（含黑名单开关与过期时间）
     */
    public TokenBlacklistService(RedisStringOps redisStringOps, AuthProperties authProperties) {
        this.redisStringOps = redisStringOps;
        this.authProperties = authProperties;
        this.bloomFilter = new TokenBlacklistBloomFilter(1_000_000);
    }

    /**
     * 构建黑名单 Redis key。
     *
     * <p>使用 SHA-256 摘要后的 token 作为 key 段，避免完整 JWT（500+ 字节）
     * 作为 key 浪费内存且可能超过 Redis key 长度限制。
     *
     * @param token 原始 JWT Token
     * @return 形如 {@code auth:token:blacklist:<sha256-hex>} 的 Redis key
     */
    private String buildBlacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + DigestUtils.sha256Hex(token);
    }

    /**
     * 将 Token 加入黑名单（登出）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>检查黑名单总开关（{@code ydsz.auth.blacklist.enabled}）</li>
     *   <li>写入 Redis key（TTL = {@code expireSeconds}，与 access_token 剩余有效期对齐）</li>
     *   <li>同步更新本地 Bloom Filter，使后续查询可前置过滤</li>
     * </ol>
     *
     * @param token JWT Token（可能为 null 或空，方法内已做防护）
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
     * 检查 Token 是否在黑名单中。
     *
     * <p>执行流程（短路优化）：
     * <ol>
     *   <li>开关未启用直接返回 false（零开销）</li>
     *   <li>Bloom Filter 返回 false → 一定不在黑名单中（无需查 Redis）</li>
     *   <li>Bloom Filter 返回 true → 可能存在，查 Redis 确认</li>
     * </ol>
     *
     * @param token JWT Token
     * @return true 表示在黑名单中（需拒绝请求）；false 表示正常
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
     * <p>使用 Redis SET NX EX 实现分布式锁（原子操作），确保同一 refresh_token 在并发场景下
     * 只能有一个请求成功刷新，防止重放攻击窗口。
     *
     * <p><b>降级策略：</b>Redis 异常时默认放行刷新请求（fail-open），避免 Redis 抖动
     * 导致所有用户都无法刷新 token；安全风险可由短 TTL 缓解。
     *
     * @param refreshToken 刷新令牌
     * @return 获锁成功返回 true，获取失败（已有其他请求正在刷新）返回 false
     */
    public boolean tryAcquireRefreshLock(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        String lockKey = REFRESH_LOCK_KEY_PREFIX + DigestUtils.sha256Hex(refreshToken);
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
     * <p>无论是否抛异常都静默处理，锁 10s 后会自动过期；
     * 主要用于刷新成功后立即释放，避免短时间内的并发重试阻塞。
     *
     * @param refreshToken 刷新令牌
     */
    public void releaseRefreshLock(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String lockKey = REFRESH_LOCK_KEY_PREFIX + DigestUtils.sha256Hex(refreshToken);
        try {
            redisStringOps.del(lockKey);
        } catch (Exception e) {
            log.debug("释放刷新锁异常（锁会自动过期）: {}", e.getMessage());
        }
    }
}
