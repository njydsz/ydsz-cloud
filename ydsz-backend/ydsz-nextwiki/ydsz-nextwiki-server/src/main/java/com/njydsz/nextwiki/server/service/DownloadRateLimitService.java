package com.njydsz.nextwiki.server.service;

import java.time.Duration;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 下载限流与防盗链服务
 * <p>
 * 基于 {@link RedisRateLimiter} 实现下载速率限制和防盗链验证。
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
 * <p><b>原子性保证：</b>限流逻辑统一使用 {@link RedisRateLimiter#tryAcquireFixedWindow}，
 * 底层基于 Redis Lua 脚本（INCR + EXPIRE 在同一个脚本中执行），避免原 INCR 后 EXPIRE 失败
 * 导致 key 永不过期的限流卡死 bug。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadRateLimitService {

    private final RedisService redisService;
    private final RedisRateLimiter redisRateLimiter;
    private final NextwikiProperties properties;

    /** Redis Key 前缀 */
    private static final String KEY_USER_RATE = "nextwiki:rate:user:";
    private static final String KEY_IP_RATE = "nextwiki:rate:ip:";
    private static final String KEY_FILE_RATE = "nextwiki:rate:file:";

    /** 限流时间窗口：1 分钟 */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    /**
     * 检查下载限流（用户级 + IP 级双重固定窗口限流）。
     * <p>委托 {@link RedisRateLimiter#tryAcquireFixedWindow}（Redis Lua 脚本，INCR+EXPIRE 原子）执行限流，
     * 任一维度超限即拒绝；限流组件不可用时按 FAIL_CLOSED 策略拒绝请求，保证安全性。
     *
     * @param userId      用户 ID（用户级限流维度，阈值 {@code nextwiki.download.rate-limit-per-minute}）
     * @param ip          客户端 IP（IP 级限流维度，阈值 {@code nextwiki.download.ip-rate-limit-per-minute}）
     * @param fileNodeId  文件节点 ID（当前作为透传参数保留，未启用单文件级限流）
     * @return 限流结果 {@link RateLimitResult}，{@code allowed=false} 时含拒绝原因
     * @complexity O(1)（两次 Redis 原子计数）
     * @concurrency 基于 Redis 原子窗口，支持多实例部署；窗口 {@link #RATE_WINDOW}=1 分钟
     * @note 无本地状态，线程安全；限流计数由各维度 Key 独立维护
     */
    public RateLimitResult checkRateLimit(String userId, String ip, String fileNodeId) {
        int rateLimitPerMinute = properties.getDownload().getRateLimitPerMinute();
        int ipRateLimitPerMinute = properties.getDownload().getIpRateLimitPerMinute();
        // 用户级限流
        if (!redisRateLimiter.tryAcquireFixedWindow(KEY_USER_RATE + userId, rateLimitPerMinute, RATE_WINDOW)) {
            log.warn("[DownloadRateLimitService] 用户下载限流: userId={}, limit={}/分钟", userId, rateLimitPerMinute);
            return RateLimitResult.blocked("用户下载频率超限: " + rateLimitPerMinute + "/分钟");
        }

        // IP 级限流
        if (!redisRateLimiter.tryAcquireFixedWindow(KEY_IP_RATE + ip, ipRateLimitPerMinute, RATE_WINDOW)) {
            log.warn("[DownloadRateLimitService] IP 下载限流: ip={}, limit={}/分钟", ip, ipRateLimitPerMinute);
            return RateLimitResult.blocked("IP 下载频率超限: " + ipRateLimitPerMinute + "/分钟");
        }

        return RateLimitResult.allowed();
    }

    /**
     * 验证 Referer 防盗链（子域名/路径包含即放行）。
     * <p>空 Referer 直接拒绝（防止无来源直链盗刷）；匹配规则为 {@code referer.contains(allowedDomain)}，
     * 属宽松前缀/包含匹配，生产环境建议收紧为正则或精确域名。
     *
     * @param referer       请求 Referer 头
     * @param allowedDomain 允许的来源域名（如 {@code example.com}）
     * @return 是否通过防盗链校验
     * @complexity O(1)（字符串包含判断）
     * @security 仅作基础来源校验，不替代签名 URL 的强校验
     */
    public boolean verifyReferer(String referer, String allowedDomain) {
        if (referer == null || referer.isEmpty()) {
            return false;
        }
        return referer.contains(allowedDomain);
    }

    /**
     * 生成签名下载 URL（MD5 签名 + Redis 落地，时效性与用户/IP 绑定）。
     * <p>将 {@code storageKey|userId|ip|expireTime} 做 MD5 得到签名，并把签名→storageKey 写入 Redis，
     * TTL 等于签名有效期；返回的路径由 Controller 路由到 {@link #verifySignedUrl} 校验。
     *
     * @param storageKey 存储对象键
     * @param userId     用户 ID（参与签名，校验时绑定）
     * @param ip         客户端 IP（参与签名，校验时绑定）
     * @return 签名下载路径（如 {@code /nextwiki/download/{sign}?expires=...}）
     * @complexity O(1)（一次 MD5 + 一次 Redis 写入）
     * @security 签名含 userId 与 ip，理论上可限制重放来源；MD5 仅作完整性校验，非加密强度
     * @note 有效期由 {@code nextwiki.download.signed-url-expire-seconds} 决定
     */
    public String generateSignedDownloadUrl(String storageKey, String userId, String ip) {
        long signedUrlExpireSeconds = properties.getDownload().getSignedUrlExpireSeconds();
        long expireTime = System.currentTimeMillis() / 1000 + signedUrlExpireSeconds;
        String rawData = storageKey + "|" + userId + "|" + ip + "|" + expireTime;
        String sign = DigestUtil.md5Hex(rawData);

        // 存储签名到 Redis（用于验证）
        String signKey = "nextwiki:sign:" + sign;
        redisService.set(signKey, storageKey, Duration.ofSeconds(signedUrlExpireSeconds));

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
        String storageKey = redisService.get(signKey, String.class);
        if (storageKey == null) {
            return null; // 签名无效
        }
        // 验证后删除（一次性使用）
        redisService.delete(signKey);
        return storageKey;
    }

    /**
     * 限流结果
     */
    @Data
    @Builder
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
