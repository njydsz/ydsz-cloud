package com.njydsz.common.safe.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.njydsz.common.safe.event.SecurityEventRingBuffer;

/**
 * 安全事件发布器
 *
 * <p>通过 Spring {@link ApplicationEventPublisher} 发布事件，
 * 同时通过 {@link ServiceLoader} 调用所有 SPI 实现的监听器。
 *
 * <p><b>增强特性（v1.2.0）：</b>
 * <ul>
 *   <li>环形缓冲区：保留最近 256 个事件的内存快照，便于运行时回溯</li>
 *   <li>发布耗时统计：记录每次 publish 的耗时（纳秒）用于性能监控</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SecurityEventPublisher implements ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventPublisher.class);

    private final List<SecurityAlertListener> spiListeners;
    private final SecurityEventRingBuffer ringBuffer;
    private ApplicationEventPublisher applicationEventPublisher;

    /** 最近一次发布耗时（纳秒），用于监控 */
    private volatile long lastPublishNanos;

    /**
     * 构造方法
     *
     * <p>使用 {@link SecurityEventRingBuffer} 保留最近事件的内存快照。
     * 注意：RingBuffer 已标记 @Deprecated，计划在 v2.0 中替换为 Sentry/Prometheus 查询。
     */
    @SuppressWarnings("deprecation")
    public SecurityEventPublisher() {
        this.spiListeners = loadSpiListeners();
        this.ringBuffer = new SecurityEventRingBuffer();
    }

    @Override
    public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布安全事件
     *
     * @param event 安全事件
     */
    public void publish(@Nullable SecurityEvent event) {
        if (event == null) {
            return;
        }

        long startNanos = System.nanoTime();

        // 写入环形缓冲区（覆盖最旧数据）
        ringBuffer.offer(event);

        // 发布 Spring 应用事件
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(event);
        }

        // 调用 SPI 监听器
        for (SecurityAlertListener listener : spiListeners) {
            try {
                listener.onSecurityEvent(event);
            } catch (Exception e) {
                log.warn("安全事件监听器处理异常: {}", listener.getClass().getName(), e);
            }
        }

        lastPublishNanos = System.nanoTime() - startNanos;
    }

    /**
     * 获取最近的安全事件快照
     *
     * @return 不可变的事件列表（按时间从旧到新排序）
     */
    public List<SecurityEvent> recentEvents() {
        return ringBuffer.snapshot();
    }

    /**
     * 获取最近 N 个安全事件
     *
     * @param count 获取数量
     * @return 不可变的事件列表
     */
    public List<SecurityEvent> recentEvents(int count) {
        return ringBuffer.recent(count);
    }

    /**
     * 最近一次发布耗时（纳秒）
     *
     * @return 发布耗时（纳秒）
     */
    public long getLastPublishNanos() {
        return lastPublishNanos;
    }

    /**
     * 环形缓冲区当前大小
     *
     * @return 事件数量
     */
    public int getBufferSize() {
        return ringBuffer.size();
    }

    private List<SecurityAlertListener> loadSpiListeners() {
        List<SecurityAlertListener> listeners = new ArrayList<>();
        ServiceLoader<SecurityAlertListener> loader = ServiceLoader.load(SecurityAlertListener.class);
        for (SecurityAlertListener listener : loader) {
            listeners.add(listener);
            log.info("加载安全事件告警监听器: {}", listener.getClass().getName());
        }
        return listeners;
    }
}
