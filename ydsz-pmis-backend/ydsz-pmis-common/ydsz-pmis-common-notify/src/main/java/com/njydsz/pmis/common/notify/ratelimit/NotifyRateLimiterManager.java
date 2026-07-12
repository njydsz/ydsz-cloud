package com.njydsz.pmis.common.notify.ratelimit;

import com.njydsz.pmis.common.notify.config.NotifyProperties;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li>动态调整：支持运行时修改限流参数</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 2026-06-16
 */
@Slf4j
public class NotifyRateLimiterManager {

    private final Map<NotifyChannel, SlidingWindowRateLimiter> channelLimiters = new ConcurrentHashMap<>();
    private final NotifyProperties.RateLimit rateLimitConfig;

    /**
     * 默认限流配置：100次/分钟
     */
    private static final int DEFAULT_MAX_REQUESTS = 100;
    private static final long DEFAULT_WINDOW_MILLIS = 60_000L;

    public NotifyRateLimiterManager(NotifyProperties.RateLimit rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
        initializeLimiters();
    }

    /**
     * 初始化各渠道的限流器
     */
    private void initializeLimiters() {
        for (NotifyChannel channel : NotifyChannel.values()) {
            int maxRequests = getMaxRequestsForChannel(channel);
            long windowMillis = getWindowMillisForChannel(channel);

            SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(maxRequests, windowMillis);
            channelLimiters.put(channel, limiter);

            log.info("[NotifyRateLimiter] 初始化渠道限流器 | channel={} | maxRequests={} | window={}ms",
                    channel, maxRequests, windowMillis);
        }
    }

    /**
     * 尝试获取指定渠道的发送许可
     *
     * @param channel 通知渠道
     * @return true 表示允许发送，false 表示被限流
     */
    public boolean tryAcquire(NotifyChannel channel) {
        SlidingWindowRateLimiter limiter = channelLimiters.get(channel);
        if (limiter == null) {
            log.warn("[NotifyRateLimiter] 未找到渠道限流器，使用默认配置 | channel={}", channel);
            limiter = channelLimiters.computeIfAbsent(channel, k ->
                    new SlidingWindowRateLimiter(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_MILLIS));
        }

        boolean acquired = limiter.tryAcquire();
        if (!acquired) {
            log.warn("[NotifyRateLimiter] 渠道限流触发 | channel={} | currentRequests={} | config={}",
                    channel, limiter.getCurrentRequestCount(), limiter.getConfigInfo());
        }

        return acquired;
    }

    /**
     * 获取指定渠道的当前请求数
     *
     * @param channel 通知渠道
     * @return 当前窗口内的请求数
     */
    public int getCurrentRequestCount(NotifyChannel channel) {
        SlidingWindowRateLimiter limiter = channelLimiters.get(channel);
        return limiter != null ? limiter.getCurrentRequestCount() : 0;
    }

    /**
     * 获取指定渠道的限流器配置信息
     *
     * @param channel 通知渠道
     * @return 配置描述
     */
    public String getChannelConfigInfo(NotifyChannel channel) {
        SlidingWindowRateLimiter limiter = channelLimiters.get(channel);
        return limiter != null ? limiter.getConfigInfo() : "未配置";
    }

    /**
     * 获取渠道的最大请求数配置
     */
    private int getMaxRequestsForChannel(NotifyChannel channel) {
        if (rateLimitConfig == null || rateLimitConfig.getChannelLimits() == null) {
            return DEFAULT_MAX_REQUESTS;
        }

        NotifyProperties.ChannelRateLimit channelLimit = rateLimitConfig.getChannelLimits().get(channel);
        if (channelLimit != null && channelLimit.getMaxRequests() > 0) {
            return channelLimit.getMaxRequests();
        }

        return rateLimitConfig.getDefaultMaxRequests() > 0
                ? rateLimitConfig.getDefaultMaxRequests()
                : DEFAULT_MAX_REQUESTS;
    }

    /**
     * 获取渠道的窗口大小配置（毫秒）
     */
    private long getWindowMillisForChannel(NotifyChannel channel) {
        if (rateLimitConfig == null || rateLimitConfig.getChannelLimits() == null) {
            return DEFAULT_WINDOW_MILLIS;
        }

        NotifyProperties.ChannelRateLimit channelLimit = rateLimitConfig.getChannelLimits().get(channel);
        if (channelLimit != null && channelLimit.getWindowSeconds() > 0) {
            return channelLimit.getWindowSeconds() * 1000L;
        }

        return rateLimitConfig.getDefaultWindowSeconds() > 0
                ? rateLimitConfig.getDefaultWindowSeconds() * 1000L
                : DEFAULT_WINDOW_MILLIS;
    }
}
