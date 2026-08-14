package com.njydsz.common.exception.alert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 基于内存滑动窗口的异常告警策略。
 *
 * <p>对每个异常码维护一个时间戳队列，自动清理超出窗口的旧记录。
 * 当队列长度 ≥ {@link #threshold} 时（且本次未触发过告警）发布
 * {@link ExceptionAlertEvent}，防止告警风暴。
 *
 * <p><b>默认配置：</b>
 * <ul>
 *   <li>窗口：5 分钟</li>
 *   <li>阈值：10 次</li>
 * </ul>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * @Bean
 * public ExceptionAlertPolicy customAlertPolicy(ApplicationEventPublisher publisher) {
 *     return new SlidingWindowAlertPolicy(publisher, Duration.ofMinutes(3), 5);
 * }
 * }</pre>
 *
 * <p><b>告警风暴抑制：</b>触发告警后需调用 {@link #reset(String)} 清零才能再次触发，
 * 避免同类异常持续刷屏。
 *
 * @author ydsz-team
 * @since 2.4.0
 */
@Slf4j
public class SlidingWindowAlertPolicy implements ExceptionAlertPolicy {

    /** 默认滑动窗口：5 分钟 */
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
    /** 默认告警阈值：窗口内 ≥ 10 次 */
    public static final int DEFAULT_THRESHOLD = 10;

    private final ApplicationEventPublisher eventPublisher;
    private final Duration window;
    private final int threshold;

    /**
     * 各错误码的时间戳滑动窗口：code → 时间戳队列
     */
    private final Map<String, Queue<LocalDateTime>> windows = new ConcurrentHashMap<>();

    /**
     * 已触发告警的错误码集合（需 reset 后才能再次触发）
     */
    private final Map<String, Boolean> alerted = new ConcurrentHashMap<>();

    /**
     * 构造滑动窗口告警策略。
     *
     * @param eventPublisher Spring 事件发布者（用于发布 ExceptionAlertEvent）
     * @param window        滑动窗口时长
     * @param threshold     告警阈值（窗口内触发次数 ≥ 此值时告警）
     */
    public SlidingWindowAlertPolicy(ApplicationEventPublisher eventPublisher,
                                     Duration window, int threshold) {
        this.eventPublisher = eventPublisher;
        this.window = window;
        this.threshold = threshold;
    }

    /**
     * 使用默认配置构造滑动窗口告警策略。
     *
     * @param eventPublisher Spring 事件发布者
     */
    public SlidingWindowAlertPolicy(ApplicationEventPublisher eventPublisher) {
        this(eventPublisher, DEFAULT_WINDOW, DEFAULT_THRESHOLD);
    }

    @Override
    public void record(String errorCode, String message) {
        if (errorCode == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Queue<LocalDateTime> queue = windows.computeIfAbsent(
                errorCode, k -> new ConcurrentLinkedQueue<>());

        // 清理超出窗口的旧记录
        LocalDateTime windowStart = now.minus(window);
        while (!queue.isEmpty() && queue.peek().isBefore(windowStart)) {
            queue.poll();
        }

        // 添加本次触发记录
        queue.offer(now);

        // 超阈值且未告警过 → 发布事件
        if (queue.size() >= threshold && alerted.putIfAbsent(errorCode, Boolean.TRUE) == null) {
            publishAlert(errorCode, queue, now, message);
        }
    }

    /**
     * 发布告警事件
     */
    private void publishAlert(String errorCode, Queue<LocalDateTime> queue,
                               LocalDateTime now, String message) {
        LocalDateTime firstSeen = queue.peek();
        String sampleMsg = message != null && message.length() > 200
                ? message.substring(0, 200) : message;

        ExceptionAlertEvent event = new ExceptionAlertEvent(
                errorCode, queue.size(), threshold,
                (int) window.toMinutes(), firstSeen, now, sampleMsg);

        log.warn("[ExceptionAlertPolicy] 异常码触发频率超限: {}", event);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }

    @Override
    public void reset(String errorCode) {
        if (errorCode == null) {
            return;
        }
        windows.remove(errorCode);
        alerted.remove(errorCode);
        log.info("[ExceptionAlertPolicy] 已重置告警计数: code={}", errorCode);
    }

    @Override
    public Map<String, Integer> activeAlerts() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(window);

        windows.forEach((code, queue) -> {
            // 清理过期记录后统计
            queue.removeIf(ts -> ts.isBefore(windowStart));
            if (!queue.isEmpty()) {
                result.put(code, queue.size());
            }
        });
        return result;
    }
}
