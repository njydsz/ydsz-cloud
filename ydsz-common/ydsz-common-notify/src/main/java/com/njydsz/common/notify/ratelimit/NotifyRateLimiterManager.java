package com.njydsz.common.notify.ratelimit;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.notify.config.NotifyProperties;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.redis.service.RedisRateLimiter;

/**
 * 通知渠道限流管理器
 *
 * <p>为每个通知渠道维护独立的限流器，防止单个渠道过载。
 * 支持全局默认限流和渠道级自定义限流配置。
 *
 * <p><b>限流策略：</b>
 * <ul>
 *   <li>全局默认：每个渠道 100次/分钟</li>
 *   <li>可配置：通过 {@link NotifyProperties.RateLimit} 自定义各渠道限流规则</li>
 *   <li>算法：滑动窗口（基于 Redis + Lua 实现，支持多实例部署）</li>
 * </ul>
 *
 * <p>P0-1 架构优化：从纯内存滑动窗口迁移为委托
 * {@link RedisRateLimiter#tryAcquireSlidingWindow} 实现分布式限流，
 * 消除多实例部署下内存限流器不一致问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class NotifyRateLimiterManager {

    private final Map<NotifyChannel, ChannelLimit> channelLimits = new EnumMap<>(NotifyChannel.class);
    private final RedisRateLimiter redisRateLimiter;

    /**
     * 渠道限流参数封装
     */
    private static class ChannelLimit {
        final int maxRequests;
        final Duration window;

        ChannelLimit(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }
    }

    /**
     * 构造限流管理器
     *
     * @param rateLimitConfig 限流配置
     * @param redisRateLimiter Redis 限流器（可选，不可用时降级为不限制）
     */
    public NotifyRateLimiterManager(NotifyProperties.RateLimit rateLimitConfig,
                                    RedisRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
        initializeLimits(rateLimitConfig);
    }

    /**
     * 初始化各渠道的限流参数
     */
    private void initializeLimits(NotifyProperties.RateLimit rateLimitConfig) {
        if (rateLimitConfig == null) {
            return;
        }
        for (NotifyChannel channel : NotifyChannel.values()) {
            int maxRequests = getMaxRequestsForChannel(rateLimitConfig, channel);
            long windowSeconds = getWindowSecondsForChannel(rateLimitConfig, channel);

            channelLimits.put(channel, new ChannelLimit(maxRequests, Duration.ofSeconds(windowSeconds)));

            log.info("[NotifyRateLimiter] 初始化渠道限流参数 | channel={} | maxRequests={} | window={}s",
                    channel, maxRequests, windowSeconds);
        }
    }

    /**
     * 尝试获取指定渠道的发送许可（不限租户，全局共享限流）。
     *
     * @param channel 通知渠道
     * @return true 表示允许发送，false 表示被限流
     */
    public boolean tryAcquire(NotifyChannel channel) {
        return tryAcquire(channel, null);
    }

    /**
     * 尝试获取指定渠道的发送许可（支持多租户隔离）。
     *
     * <p>当 tenantId 不为空时，限流 key 包含租户维度，避免不同租户间互相限流。
     * 当 tenantId 为空时，退化为全局共享限流（向后兼容）。
     *
     * @param channel  通知渠道
     * @param tenantId 租户 ID（可为 null，表示全局共享）
     * @return true 表示允许发送，false 表示被限流
     */
    public boolean tryAcquire(NotifyChannel channel, String tenantId) {
        if (channel == null) {
            return true;
        }
        if (redisRateLimiter == null) {
            log.debug("[NotifyRateLimiter] RedisRateLimiter 不可用，降级放行 | channel={}", channel);
            return true;
        }

        ChannelLimit limit = channelLimits.get(channel);
        if (limit == null) {
            log.warn("[NotifyRateLimiter] 未找到渠道限流参数 | channel={}", channel);
            return true;
        }

        String key = buildKey(channel, tenantId);
        try {
            boolean acquired = redisRateLimiter.tryAcquireSlidingWindow(
                    key, limit.maxRequests, limit.window);
            if (!acquired) {
                log.warn("[NotifyRateLimiter] 渠道限流触发 | channel={} | tenantId={} | maxRequests={} | window={}s",
                        channel, tenantId, limit.maxRequests, limit.window.getSeconds());
            }
            return acquired;
        } catch (Exception e) {
            log.warn("[NotifyRateLimiter] 限流异常，降级放行 | channel={} | tenantId={} | error={}",
                    channel, tenantId, e.getMessage());
            return true;
        }
    }

    /**
     * 获取指定渠道的限流器配置信息
     *
     * @param channel 通知渠道
     * @return 配置描述
     */
    public String getChannelConfigInfo(NotifyChannel channel) {
        ChannelLimit limit = channelLimits.get(channel);
        return limit != null
                ? String.format("maxRequests=%d, window=%ds", limit.maxRequests, limit.window.getSeconds())
                : "未配置";
    }

    /**
     * 构建限流 key（支持多租户隔离）。
     *
     * @param channel  通知渠道
     * @param tenantId 租户 ID（可为 null）
     * @return 限流 key
     */
    private String buildKey(NotifyChannel channel, String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            return "notify:tenant:" + tenantId + ":channel:" + channel.name().toLowerCase();
        }
        return "notify:channel:" + channel.name().toLowerCase();
    }

    private int getMaxRequestsForChannel(NotifyProperties.RateLimit rateLimitConfig, NotifyChannel channel) {
        if (rateLimitConfig.getChannelLimits() != null) {
            NotifyProperties.ChannelRateLimit channelLimit = rateLimitConfig.getChannelLimits().get(channel);
            if (channelLimit != null && channelLimit.getMaxRequests() > 0) {
                return channelLimit.getMaxRequests();
            }
        }
        return rateLimitConfig.getDefaultMaxRequests() > 0
                ? rateLimitConfig.getDefaultMaxRequests()
                : 100;
    }

    private long getWindowSecondsForChannel(NotifyProperties.RateLimit rateLimitConfig, NotifyChannel channel) {
        if (rateLimitConfig.getChannelLimits() != null) {
            NotifyProperties.ChannelRateLimit channelLimit = rateLimitConfig.getChannelLimits().get(channel);
            if (channelLimit != null && channelLimit.getWindowSeconds() > 0) {
                return channelLimit.getWindowSeconds();
            }
        }
        return rateLimitConfig.getDefaultWindowSeconds() > 0
                ? rateLimitConfig.getDefaultWindowSeconds()
                : 60;
    }
}
