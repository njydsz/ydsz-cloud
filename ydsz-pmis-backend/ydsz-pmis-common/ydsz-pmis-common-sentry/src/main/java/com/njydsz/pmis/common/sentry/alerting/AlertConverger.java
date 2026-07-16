package com.njydsz.pmis.common.sentry.alerting;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.pmis.common.sentry.domain.AlertEvent;
import com.njydsz.pmis.common.sentry.spi.AlertPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 告警收敛器
 *
 * <p>基于时间窗口聚合 + 去重 + 静默期实现告警降噪。
 *
 * <p>策略：
 * <ul>
 *   <li>时间窗口聚合：同一告警在窗口内仅通知一次</li>
 *   <li>去重：基于 dedupKey 去重</li>
 *   <li>静默期：告警触发后设置静默期，期内不重复通知</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class AlertConverger implements AlertPublisher {

    /** 下游告警发布器 */
    private final AlertPublisher delegate;

    /** 静默期（毫秒） */
    private final long silencePeriodMillis;

    /** 告警计数器（用于统计） */
    private final AtomicInteger totalAlerts = new AtomicInteger(0);
    private final AtomicInteger suppressedAlerts = new AtomicInteger(0);

    /** 静默记录（key=dedupKey, value=上次通知时间） */
    private final ConcurrentHashMap<String, Instant> silenceMap = new ConcurrentHashMap<>();

    /** 聚合窗口内计数（key=dedupKey, value=count） */
    private final ConcurrentHashMap<String, AtomicInteger> windowCounts = new ConcurrentHashMap<>();

    public AlertConverger(AlertPublisher delegate, long silencePeriodMillis) {
        this.delegate = delegate;
        this.silencePeriodMillis = silencePeriodMillis;
        log.info("[Sentry] AlertConverger 初始化: silencePeriod={}ms", silencePeriodMillis);
    }

    @Override
    public boolean publish(AlertEvent event) {
        totalAlerts.incrementAndGet();
        String dedupKey = event.dedupKey();

        // 检查静默期
        if (isSilenced(dedupKey)) {
            suppressedAlerts.incrementAndGet();
            windowCounts.computeIfAbsent(dedupKey, k -> new AtomicInteger(0)).incrementAndGet();
            log.debug("[Sentry] 告警被静默: key={}, suppressed={}", dedupKey, suppressedAlerts.get());
            return false;
        }

        // 发布告警
        boolean published = delegate != null && delegate.publish(event);

        // 设置静默
        if (published) {
            silenceMap.put(dedupKey, Instant.now());
        }

        return published;
    }

    /**
     * 检查是否在静默期内
     */
    private boolean isSilenced(String dedupKey) {
        Instant lastFired = silenceMap.get(dedupKey);
        if (lastFired == null) {
            return false;
        }
        return lastFired.plusMillis(silencePeriodMillis).isAfter(Instant.now());
    }

    /**
     * 清理过期静默记录（定时调度，每 60 秒执行一次）
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredSilence() {
        Instant now = Instant.now();
        silenceMap.entrySet().removeIf(entry ->
                entry.getValue().plusMillis(silencePeriodMillis).isBefore(now));
    }

    /**
     * 获取静默中的告警数量
     */
    public int getActiveSilenceCount() {
        cleanupExpiredSilence();
        return silenceMap.size();
    }

    /**
     * 获取总告警数
     */
    public int getTotalAlerts() {
        return totalAlerts.get();
    }

    /**
     * 获取被抑制的告警数
     */
    public int getSuppressedAlerts() {
        return suppressedAlerts.get();
    }

    /**
     * 获取告警抑制率
     */
    public double getSuppressionRate() {
        int total = totalAlerts.get();
        if (total == 0) {
            return 0;
        }
        return (double) suppressedAlerts.get() / total;
    }

    /**
     * 获取窗口内聚合计数
     */
    public int getWindowCount(String dedupKey) {
        AtomicInteger count = windowCounts.get(dedupKey);
        return count != null ? count.get() : 0;
    }

    /**
     * 重置窗口计数
     */
    public void resetWindowCounts() {
        windowCounts.clear();
    }

    @Override
    public boolean isAvailable() {
        return delegate != null && delegate.isAvailable();
    }

    @Override
    public String getName() {
        return "alert-converger";
    }
}
