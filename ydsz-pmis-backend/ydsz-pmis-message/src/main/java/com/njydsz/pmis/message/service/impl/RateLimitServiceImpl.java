package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 限流与频率控制服务实现。
 *
 * <p>令牌桶使用 Redisson {@link RRateLimiter}；每日 / 每小时频率使用 Redis INCR + EXPIRE，
 * 上限取自用户偏好 {@link MsgPreferenceDO#getDailyLimit()} / {@code hourlyLimit}。
 *
 * <p>P2-5: 新增 {@link #checkSendLimit} 方法，按 receiver / templateCode / tenant
 * 三个维度分别做令牌桶限流，任一维度超限即拒绝发送。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final PreferenceService preferenceService;
    private final MessageProperties messageProperties;

    @Override
    @SuppressWarnings("deprecation")
    public boolean tryAcquire(String key, int permits) {
        if (key == null || key.isBlank() || permits <= 0) {
            return true;
        }
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(MessageConstants.RATE_LIMIT_KEY_PREFIX + key);
            // 令牌桶：每秒补充 permits 个令牌（首次初始化时设置）
            limiter.trySetRate(RateType.OVERALL, permits, 1, RateIntervalUnit.SECONDS);
            return limiter.tryAcquire(1);
        } catch (Exception e) {
            // 限流器异常降级为放行，避免 Redis 故障阻断业务
            log.warn("[RateLimit] tryAcquire 降级放行: key={} err={}", key, e.getMessage());
            return true;
        }
    }

    @Override
    public boolean checkFrequency(String userId, String channel, String bizType) {
        if (userId == null || userId.isBlank()) {
            return true;
        }
        MsgPreferenceDO pref = preferenceService.getByUser(userId, channel, bizType);
        if (pref == null || pref.getEnabled() == null) {
            // 无偏好配置视为不限制
            return true;
        }
        if (pref.getEnabled() == 0) {
            // 用户关闭该通道，不允许发送
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        // 每小时上限
        if (pref.getHourlyLimit() != null && pref.getHourlyLimit() > 0) {
            Long cur = readCounter(MessageConstants.FREQUENCY_HOURLY_PREFIX, userId, channel, bizType,
                    now.format(HOUR_FMT));
            if (cur != null && cur >= pref.getHourlyLimit()) {
                log.info("[RateLimit] 频率超限(小时): user={} channel={} cur={} limit={}",
                        userId, channel, cur, pref.getHourlyLimit());
                return false;
            }
        }
        // 每日上限
        if (pref.getDailyLimit() != null && pref.getDailyLimit() > 0) {
            Long cur = readCounter(MessageConstants.FREQUENCY_DAILY_PREFIX, userId, channel, bizType,
                    now.format(DAY_FMT));
            if (cur != null && cur >= pref.getDailyLimit()) {
                log.info("[RateLimit] 频率超限(日): user={} channel={} cur={} limit={}",
                        userId, channel, cur, pref.getDailyLimit());
                return false;
            }
        }
        return true;
    }

    @Override
    public void recordFrequency(String userId, String channel, String bizType) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        incrCounter(MessageConstants.FREQUENCY_HOURLY_PREFIX, userId, channel, bizType,
                now.format(HOUR_FMT), Duration.ofHours(1).plusMinutes(5).getSeconds());
        incrCounter(MessageConstants.FREQUENCY_DAILY_PREFIX, userId, channel, bizType,
                now.format(DAY_FMT), Duration.ofDays(1).plusHours(1).getSeconds());
    }

    /**
     * P2-5: 多维度发送限流检查。
     *
     * <p>按 receiver / templateCode / tenant 三个维度分别做令牌桶限流，
     * 任一维度超限即返回 false。空值维度跳过。各维度开关与 permits 由配置控制。
     *
     * <p>注意：{@link #tryAcquire} 内部会自动加 {@code RATE_LIMIT_KEY_PREFIX} 前缀,
     * 此处传入的 key 仅包含维度标识 + 值(如 {@code receiver:u1}),避免前缀重复拼接。
     */
    @Override
    public boolean checkSendLimit(String channel, String receiver, String templateCode, String tenantId) {
        MessageProperties.RateLimitConfig cfg = messageProperties.getRateLimit();
        if (cfg == null) {
            // 无配置视为不限制
            return true;
        }
        // receiver 维度
        if (cfg.isReceiverEnabled() && receiver != null && !receiver.isBlank()) {
            if (!tryAcquire("receiver:" + receiver, cfg.getReceiverPermits())) {
                log.info("[RateLimit] receiver 维度限流: channel={} receiver={} permits={}/s",
                        channel, receiver, cfg.getReceiverPermits());
                return false;
            }
        }
        // templateCode 维度
        if (cfg.isTemplateEnabled() && templateCode != null && !templateCode.isBlank()) {
            if (!tryAcquire("template:" + templateCode, cfg.getTemplatePermits())) {
                log.info("[RateLimit] template 维度限流: channel={} template={} permits={}/s",
                        channel, templateCode, cfg.getTemplatePermits());
                return false;
            }
        }
        // tenant 维度
        if (cfg.isTenantEnabled() && tenantId != null && !tenantId.isBlank()) {
            if (!tryAcquire("tenant:" + tenantId, cfg.getTenantPermits())) {
                log.info("[RateLimit] tenant 维度限流: channel={} tenant={} permits={}/s",
                        channel, tenantId, cfg.getTenantPermits());
                return false;
            }
        }
        return true;
    }

    private Long readCounter(String prefix, String userId, String channel, String bizType, String suffix) {
        String key = prefix + userId + ":" + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
                + ":" + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType) + ":" + suffix;
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null || val.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private void incrCounter(String prefix, String userId, String channel, String bizType, String suffix, long ttlSeconds) {
        String key = prefix + userId + ":" + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
                + ":" + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType) + ":" + suffix;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("[RateLimit] 计数失败(降级忽略): key={} err={}", key, e.getMessage());
        }
    }
}
