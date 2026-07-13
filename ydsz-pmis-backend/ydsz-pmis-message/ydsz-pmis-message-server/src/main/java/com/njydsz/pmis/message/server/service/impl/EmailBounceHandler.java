package com.njydsz.pmis.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * P2-16: 邮件退信处理闭环。
 *
 * <p>当邮件服务商回调退信通知时，将退信邮箱加入黑名单，
 * 后续发送时自动跳过，避免持续向无效地址发送影响发送方信誉。
 *
 * <p>处理流程：
 * <ol>
 *   <li>接收退信回调（webhook/回调接口）</li>
 *   <li>将退信邮箱加入 Redis 黑名单（TTL 90 天）</li>
 *   <li>记录退信日志到 DB（便于分析退信趋势）</li>
 *   <li>发送前检查黑名单，自动跳过</li>
 * </ol>
 *
 * <p>Redis Key 格式：{@code email:bounce:{email}} → bounceReason
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailBounceHandler {

    private final StringRedisTemplate redisTemplate;

    /** 退信黑名单 Key 前缀 */
    private static final String BOUNCE_KEY_PREFIX = "email:bounce:";

    /** 退信黑名单 TTL（天） */
    private static final long BOUNCE_TTL_DAYS = 90L;

    /**
     * 记录退信。
     *
     * @param email        退信邮箱
     * @param bounceReason 退信原因
     */
    public void recordBounce(String email, String bounceReason) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
        redisTemplate.opsForValue().set(key, bounceReason != null ? bounceReason : "unknown",
                Duration.ofDays(BOUNCE_TTL_DAYS));
        log.warn("[Bounce] 邮件退信已记录: email={} reason={}", email, bounceReason);
    }

    /**
     * 检查邮箱是否在退信黑名单中。
     *
     * @param email 邮箱地址
     * @return true 表示在黑名单中，应跳过发送
     */
    public boolean isBounced(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 从黑名单中移除（用户更新邮箱后可手动清除）。
     *
     * @param email 邮箱地址
     */
    public void removeFromBounceList(String email) {
        String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
        redisTemplate.delete(key);
        log.info("[Bounce] 邮箱已从退信黑名单移除: email={}", email);
    }

    /**
     * 获取退信原因。
     *
     * @param email 邮箱地址
     * @return 退信原因，null 表示不在黑名单中
     */
    public String getBounceReason(String email) {
        String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
        return redisTemplate.opsForValue().get(key);
    }
}
