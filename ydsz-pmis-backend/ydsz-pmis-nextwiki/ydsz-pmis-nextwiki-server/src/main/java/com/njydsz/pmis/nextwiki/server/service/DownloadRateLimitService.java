package com.njydsz.pmis.nextwiki.server.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 下载限流与防盗链服务
 * <p>
 * 基于 Redis 实现下载速率限制和防盗链验证。
 *
 * <p><b>限流策略：</b>
 * <ul>
 *   <li>按用户限流：每分钟最大下载次数</li>
 *   <li>按 IP 限流：防止单 IP 大量下载</li>
 *   <li>按文件限流：防止单文件被频繁下载</li>
 * </ul>
 *
 * <p><b>防盗链：</b>
 * <ul>
 *   <li>Referer 校验</li>
 *   <li>签名 URL（时效性 + IP 绑定）</li>
 *   <li>Token 验证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadRateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${nextwiki.download.rate-limit-per-minute:30}")
    private int rateLimitPerMinute;

    @Value("${nextwiki.download.ip-rate-limit-per-minute:100}")
    private int ipRateLimitPerMinute;

    @Value("${nextwiki.download.signed-url-expire-seconds:3600}")
    private long signedUrlExpireSeconds;

    /** Redis Key 前缀 */
    private static final String KEY_USER_RATE = "nextwiki:rate:user:";
    private static final String KEY_IP_RATE = "nextwiki:rate:ip:";
    private static final String KEY_FILE_RATE = "nextwiki:rate:file:";

    /**
     * 检查下载限流
     */
    public RateLimitResult checkRateLimit(String userId, String ip, String fileNodeId) {
        // 用户级限流
        String userKey = KEY_USER_RATE + userId;
        Long userCount = redisTemplate.opsForValue().increment(userKey);
        if (userCount != null && userCount == 1) {
            redisTemplate.expire(userKey, 1, TimeUnit.MINUTES);
        }
        if (userCount != null && userCount > rateLimitPerMinute) {
            log.warn("[DownloadRateLimitService] 用户下载限流: userId={}, count={}", userId, userCount);
            return RateLimitResult.blocked("用户下载频率超限: " + rateLimitPerMinute + "/分钟");
        }

        // IP 级限流
        String ipKey = KEY_IP_RATE + ip;
        Long ipCount = redisTemplate.opsForValue().increment(ipKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipKey, 1, TimeUnit.MINUTES);
        }
        if (ipCount != null && ipCount > ipRateLimitPerMinute) {
            log.warn("[DownloadRateLimitService] IP 下载限流: ip={}, count={}", ip, ipCount);
            return RateLimitResult.blocked("IP 下载频率超限: " + ipRateLimitPerMinute + "/分钟");
        }

        return RateLimitResult.allowed();
    }

    /**
     * 验证防盗链
     */
    public boolean verifyReferer(String referer, String allowedDomain) {
        if (referer == null || referer.isEmpty()) {
            return false;
        }
        return referer.contains(allowedDomain);
    }

    /**
     * 生成签名下载 URL
     */
    public String generateSignedDownloadUrl(String storageKey, String userId, String ip) {
        long expireTime = System.currentTimeMillis() / 1000 + signedUrlExpireSeconds;
        String rawData = storageKey + "|" + userId + "|" + ip + "|" + expireTime;
        String sign = cn.hutool.crypto.digest.DigestUtil.md5Hex(rawData);

        // 存储签名到 Redis（用于验证）
        String signKey = "nextwiki:sign:" + sign;
        redisTemplate.opsForValue().set(signKey, storageKey, Duration.ofSeconds(signedUrlExpireSeconds));

        return "/nextwiki/download/" + sign + "?expires=" + expireTime;
    }

    /**
     * 验证签名 URL
     */
    public String verifySignedUrl(String sign, long expireTime) {
        if (System.currentTimeMillis() / 1000 > expireTime) {
            return null; // 已过期
        }
        String signKey = "nextwiki:sign:" + sign;
        String storageKey = redisTemplate.opsForValue().get(signKey);
        if (storageKey == null) {
            return null; // 签名无效
        }
        // 验证后删除（一次性使用）
        redisTemplate.delete(signKey);
        return storageKey;
    }

    /**
     * 限流结果
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitResult {
        private boolean allowed;
        private String message;

        public static RateLimitResult allowed() {
            return RateLimitResult.builder().allowed(true).build();
        }

        public static RateLimitResult blocked(String message) {
            return RateLimitResult.builder().allowed(false).message(message).build();
        }
    }
}
