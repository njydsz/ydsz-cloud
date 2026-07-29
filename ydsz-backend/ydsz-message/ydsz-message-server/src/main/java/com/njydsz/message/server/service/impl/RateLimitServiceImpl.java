package com.njydsz.message.server.service.impl.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.config.PreferenceService;
import com.njydsz.message.server.service.core.RateLimitService;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流与频率控制服务实现。
 *
 * <p>P0-1b 架构优化：令牌桶限流从 Redisson {@code RRateLimiter} 改为委托
 * {@link RedisRateLimiter}（ydsz-common-redis 公共能力），统一全项目限流技术栈。
 *
 * <p>令牌桶使用 {@link RedisRateLimiter#tryAcquireTokenBucket(String, int, int)}；
 * 每日 / 每小时频率使用 Redis INCR + EXPIRE，
 * 上限取自用户偏好 {@link MsgPreference#getDailyLimit()} / {@code hourlyLimit}。
 *
 * <p>P2-5: {@link #checkSendLimit} 方法，按 receiver / templateCode / tenant
 * 三个维度分别做令牌桶限流，任一维度超限即拒绝发送。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class RateLimitServiceImpl implements RateLimitService {

    /** 小时频率计数器 key 时间格式 */
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    /** 日频率计数器 key 时间格式 */
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Redis 令牌桶限流器（可选依赖，不可用时降级放行） */
    private final RedisRateLimiter rateLimiter;
    /** Redis 基础服务（用于 INCR/EXPIRE 频率计数） */
    private final RedisService redisService;
    /** 用户偏好服务（读取 hourlyLimit/dailyLimit） */
    private final PreferenceService preferenceService;
    /** 消息模块配置属性 */
    private final MessageProperties messageProperties;

    public RateLimitServiceImpl(ObjectProvider<RedisRateLimiter> rateLimiterProvider,
                                RedisService redisService,
                                PreferenceService preferenceService,
                                MessageProperties messageProperties) {
        this.rateLimiter = rateLimiterProvider.getIfAvailable();
        this.redisService = redisService;
        this.preferenceService = preferenceService;
        this.messageProperties = messageProperties;
        if (this.rateLimiter == null) {
            log.warn("[RateLimit] RedisRateLimiter 不可用，令牌桶限流将降级放行");
        }
    }

    /**
     * {@inheritDoc}
     * <p>委托 {@link RedisRateLimiter#tryAcquireTokenBucket} 令牌桶限流，
     * rateLimiter 不可用或异常时降级放行（返回 true）。
     *
     * @param key     限流 key
     * @param permits 请求令牌数
     * @return true 表示获取令牌成功（允许发送），false 表示被限流
     */
    @Override
    public boolean tryAcquire(String key, int permits) {
        if (key == null || key.isBlank() || permits <= 0) {
            return true;
        }
        if (rateLimiter == null) {
            return true;
        }
        try {
            return rateLimiter.tryAcquireTokenBucket(
                    MessageConstants.RATE_LIMIT_KEY_PREFIX + key, permits, permits);
        } catch (Exception e) {
            log.warn("[RateLimit] tryAcquire 降级放行: key={} err={}", key, e.getMessage(), e);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     * <p>从用户偏好读取 hourlyLimit/dailyLimit，使用 Redis INCR + EXPIRE 计数，
     * 任一维度超限即返回 false。偏好未配置或 enabled=0 时跳过频率检查。
     *
     * @param userId  用户 ID
     * @param channel 通道类型
     * @param bizType 业务类型
     * @return true 表示未超频（允许发送），false 表示频率超限
     */
    @Override
    public boolean checkFrequency(String userId, String channel, String bizType) {
        if (userId == null || userId.isBlank()) {
            return true;
        }
        MsgPreference pref = preferenceService.getByUser(userId, channel, bizType);
        if (pref == null || pref.getEnabled() == null) {
            return true;
        }
        if (pref.getEnabled() == 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (pref.getHourlyLimit() != null && pref.getHourlyLimit() > 0) {
            Long cur = readCounter(MessageConstants.FREQUENCY_HOURLY_PREFIX, userId, channel, bizType,
                    now.format(HOUR_FMT));
            if (cur != null && cur >= pref.getHourlyLimit()) {
                log.info("[RateLimit] 频率超限(小时): user={} channel={} cur={} limit={}",
                        userId, channel, cur, pref.getHourlyLimit());
                return false;
            }
        }
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

    /**
     * {@inheritDoc}
     * <p>同时递增小时和日频率计数器（Redis INCR + EXPIRE）。
     *
     * @param userId  用户 ID
     * @param channel 通道类型
     * @param bizType 业务类型
     */
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

    @Override
    public boolean checkSendLimit(String channel, String receiver, String templateCode, String tenantId) {
        MessageProperties.RateLimitConfig cfg = messageProperties.getRateLimit();
        if (cfg == null) {
            return true;
        }
        if (cfg.isReceiverEnabled() && receiver != null && !receiver.isBlank()) {
            if (!tryAcquire("receiver:" + receiver, cfg.getReceiverPermits())) {
                log.info("[RateLimit] receiver 维度限流: channel={} receiver={} permits={}/s",
                        channel, receiver, cfg.getReceiverPermits());
                return false;
            }
        }
        if (cfg.isTemplateEnabled() && templateCode != null && !templateCode.isBlank()) {
            if (!tryAcquire("template:" + templateCode, cfg.getTemplatePermits())) {
                log.info("[RateLimit] template 维度限流: channel={} template={} permits={}/s",
                        channel, templateCode, cfg.getTemplatePermits());
                return false;
            }
        }
        if (cfg.isTenantEnabled() && tenantId != null && !tenantId.isBlank()) {
            if (!tryAcquire("tenant:" + tenantId, cfg.getTenantPermits())) {
                log.info("[RateLimit] tenant 维度限流: channel={} tenant={} permits={}/s",
                        channel, tenantId, cfg.getTenantPermits());
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean checkSendLimit(String channel, String receiver, String templateCode,
                                  String tenantId, String priority) {
        MessagePriorityEnum priorityEnum = MessagePriorityEnum.fromString(priority);
        if (priorityEnum.canSkipRateLimit()) {
            MessageProperties.RateLimitConfig cfg = messageProperties.getRateLimit();
            if (cfg == null || !cfg.isReceiverEnabled() || receiver == null || receiver.isBlank()) {
                return true;
            }
            return tryAcquire("receiver:" + receiver, cfg.getReceiverPermits());
        }
        return checkSendLimit(channel, receiver, templateCode, tenantId);
    }

    private Long readCounter(String prefix, String userId, String channel, String bizType, String suffix) {
        String key = prefix + userId + ":" + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
                + ":" + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType) + ":" + suffix;
        String val = redisService.get(key, String.class);
        if (val == null || val.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void incrCounter(String prefix, String userId, String channel, String bizType, String suffix, long ttlSeconds) {
        String key = prefix + userId + ":" + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
                + ":" + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType) + ":" + suffix;
        try {
            Long count = redisService.incr(key, 1);
            if (count != null && count == 1L) {
                redisService.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.warn("[RateLimit] 计数失败(降级忽略): key={} err={}", key, e.getMessage(), e);
        }
    }
}
