package com.njydsz.pmis.common.safe.alert;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.pmis.common.safe.ip.IpAccessService;

/**
 * 安全事件自动响应聚合器
 *
 * <p>监听 {@link SecurityEvent} 事件，基于滑动窗口统计同一 IP 的安全事件频率。
 * 当同一 IP 在指定时间窗口内触发超过阈值数量的安全事件时，自动触发 IP 封禁。
 *
 * <p><b>自动封禁逻辑：</b>
 * <ul>
 *   <li>每个 IP 维护一个事件时间戳队列（滑动窗口）</li>
 *   <li>统计窗口内的事件数量，超过阈值时触发自动封禁</li>
 *   <li>封禁时长根据事件严重级别递增：LOW=30min, MEDIUM=1h, HIGH=2h, CRITICAL=6h</li>
 *   <li>已被封禁的 IP 的事件继续累计，触发升级封禁</li>
 *   <li>定时清理过期的事件时间戳，避免内存泄漏</li>
 * </ul>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     auto-block:
 *       enabled: true
 *       threshold: 10
 *       window-seconds: 60
 * }</pre>
 *
 * @since 1.0.0
 * @see SecurityEvent
 * @see IpAccessService
 */
public class SecurityEventAggregator {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventAggregator.class);

    private final IpAccessService ipAccessService;
    private final boolean enabled;
    private final int threshold;
    private final long windowSeconds;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> ipEventTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> blockedIpMap = new ConcurrentHashMap<>();
    private final AtomicLong autoBlockedCount = new AtomicLong(0);

    /**
     * @param ipAccessService IP 访问控制服务（可为 null，未启用 IP 访问控制时降级为仅日志）
     * @param enabled          是否启用自动封禁
     * @param threshold        触发自动封禁的事件数量阈值
     * @param windowSeconds    滑动窗口大小（秒）
     */
    public SecurityEventAggregator(IpAccessService ipAccessService,
                                    boolean enabled,
                                    int threshold,
                                    long windowSeconds) {
        this.ipAccessService = ipAccessService;
        this.enabled = enabled;
        this.threshold = threshold;
        this.windowSeconds = windowSeconds;

        log.info("安全事件自动响应聚合器初始化: enabled={}, threshold={}, window={}s",
                enabled, threshold, windowSeconds);
    }

    /**
     * 监听安全事件，执行自动封禁检查
     *
     * @param event 安全事件
     */
    @EventListener
    public void onSecurityEvent(SecurityEvent event) {
        if (!enabled) {
            return;
        }

        String sourceIp = event.getSourceIp();
        if (sourceIp == null || sourceIp.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000;

        ConcurrentLinkedDeque<Long> timestamps = ipEventTimestamps.computeIfAbsent(
                sourceIp, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);

            int count = timestamps.size();
            if (count >= threshold) {
                triggerAutoBlock(sourceIp, event.getSeverity(), count);
                timestamps.clear();
            }
        }
    }

    /**
     * 触发自动封禁
     *
     * @param ip       来源 IP
     * @param severity 最后一个事件的严重级别
     * @param count    窗口内事件数量
     */
    private void triggerAutoBlock(String ip, SecurityEvent.Severity severity, int count) {
        long blockSeconds = calculateBlockDuration(severity);

        Long previousBlock = blockedIpMap.get(ip);
        if (previousBlock != null && previousBlock > System.currentTimeMillis()) {
            blockSeconds *= 2;
        }

        blockedIpMap.put(ip, System.currentTimeMillis() + blockSeconds * 1000);
        autoBlockedCount.incrementAndGet();

        log.warn("【安全事件自动响应】IP {} 在 {} 秒内触发 {} 次安全事件（严重级别: {}），自动封禁 {} 秒",
                ip, windowSeconds, count, severity, blockSeconds);

        if (ipAccessService != null) {
            try {
                ipAccessService.block(ip, blockSeconds);
            } catch (Exception e) {
                log.error("【安全事件自动响应】IP 自动封禁失败: ip={}, error={}", ip, e.getMessage());
            }
        }
    }

    /**
     * 根据严重级别计算封禁时长（秒）
     */
    private long calculateBlockDuration(SecurityEvent.Severity severity) {
        return switch (severity) {
            case LOW -> 1800;
            case MEDIUM -> 3600;
            case HIGH -> 7200;
            case CRITICAL -> 21600;
        };
    }

    /**
     * 定时清理过期的事件时间戳和已过期封禁记录
     *
     * <p>每 60 秒执行一次，清理超过滑动窗口的事件时间戳，
     * 避免内存泄漏。同时清理已过期的封禁记录。
     */
    @Scheduled(fixedRateString = "${ydsz.safe.auto-block.clean-interval:60000}",
            initialDelayString = "${ydsz.safe.auto-block.clean-initial-delay:60000}")
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000;

        int cleanedEntries = 0;
        for (var entry : ipEventTimestamps.entrySet()) {
            String ip = entry.getKey();
            ConcurrentLinkedDeque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    ipEventTimestamps.remove(ip);
                    cleanedEntries++;
                }
            }
        }

        blockedIpMap.entrySet().removeIf(entry -> entry.getValue() < now);

        if (cleanedEntries > 0 && log.isDebugEnabled()) {
            log.debug("【安全事件自动响应】清理过期事件记录: 清理IP={}, 活跃IP={}, 累计封禁={}",
                    cleanedEntries, ipEventTimestamps.size(), autoBlockedCount.get());
        }
    }

    /**
     * 获取累计自动封禁 IP 数量
     *
     * @return 累计封禁次数
     */
    public long getAutoBlockedCount() {
        return autoBlockedCount.get();
    }

    /**
     * 获取当前活跃 IP 数量（有事件记录但未被清理的 IP）
     *
     * @return 活跃 IP 数量
     */
    public int getActiveIpCount() {
        return ipEventTimestamps.size();
    }
}
