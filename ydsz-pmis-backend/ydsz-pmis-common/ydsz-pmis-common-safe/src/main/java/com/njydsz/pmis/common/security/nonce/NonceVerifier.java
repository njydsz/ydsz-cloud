package com.njydsz.pmis.common.security.nonce;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Nonce 防重放组件 —— 基于 Redis 的请求唯一性校验。
 * <p>
 * 对标 remi-comm NonceVerifier，防止 API 请求被重放攻击。
 * 工作流程：
 * <ol>
 *   <li>客户端生成 UUID 作为 nonce，随请求发送</li>
 *   <li>服务端检查 Redis 中是否已存在该 nonce</li>
 *   <li>不存在 → 放行，缓存 nonce（设置 TTL）</li>
 *   <li>已存在 → 拒绝（重放攻击）</li>
 * </ol>
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class NonceVerifier {

    private static final String NONCE_KEY_PREFIX = "pmis:nonce:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    public NonceVerifier(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 验证 nonce（如果不存在则缓存）。
     *
     * @param nonce 客户端提供的 nonce
     * @return true 如果 nonce 有效（首次出现）
     */
    public boolean verify(String nonce) {
        return verify(nonce, DEFAULT_TTL);
    }

    /**
     * 验证 nonce（指定 TTL）。
     *
     * @param nonce 客户端提供的 nonce
     * @param ttl   nonce 缓存时间
     * @return true 如果 nonce 有效（首次出现）
     */
    public boolean verify(String nonce, Duration ttl) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        String key = NONCE_KEY_PREFIX + nonce;
        Boolean result = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 生成新的 nonce。
     *
     * @return UUID 格式的 nonce
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
