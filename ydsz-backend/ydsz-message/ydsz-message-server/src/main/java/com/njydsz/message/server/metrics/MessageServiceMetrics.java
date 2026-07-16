package com.njydsz.message.server.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * P3-22~25: 消息服务可观测性指标。
 *
 * <p>基于 Micrometer 实现全面的消息服务指标埋点，覆盖：
 * <ul>
 *   <li>P3-22: 发送延迟分位数（P50/P90/P99）— 按通道分维度</li>
 *   <li>P3-23: 消费延迟（消息入库 → 消费完成的时间差）</li>
 *   <li>P3-24: 通道成功率仪表盘 — 按通道/模板/租户维度</li>
 *   <li>P3-25: 异常分类统计 — 按异常类型/通道维度</li>
 * </ul>
 *
 * <p>指标命名规范：
 * <ul>
 *   <li>{@code ydsz.message.send.duration} — 发送耗时（Timer）</li>
 *   <li>{@code ydsz.message.consume.delay} — 消费延迟（Timer）</li>
 *   <li>{@code ydsz.message.send.total} — 发送总数（Counter）</li>
 *   <li>{@code ydsz.message.send.success} — 发送成功数（Counter）</li>
 *   <li>{@code ydsz.message.send.failure} — 发送失败数（Counter）</li>
 *   <li>{@code ydsz.message.exception} — 异常计数（Counter）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageServiceMetrics {

    private final MeterRegistry meterRegistry;

    /** Timer 缓存（避免重复创建） */
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /** Counter 缓存 */
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public MessageServiceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[Metrics] 消息服务指标初始化完成");
    }

    /**
     * P3-22: 记录发送耗时。
     *
     * @param channel 通道类型
     * @param duration 发送耗时
     */
    public void recordSendDuration(String channel, Duration duration) {
        Timer timer = timerCache.computeIfAbsent(
                "send:" + channel,
                k -> Timer.builder("ydsz.message.send.duration")
                        .tags("channel", channel)
                        .description("消息发送耗时")
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .register(meterRegistry)
        );
        timer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * P3-23: 记录消费延迟（从消息创建到消费完成）。
     *
     * @param channel  通道类型
     * @param delayMillis 延迟毫秒数
     */
    public void recordConsumeDelay(String channel, long delayMillis) {
        Timer timer = timerCache.computeIfAbsent(
                "consume:" + channel,
                k -> Timer.builder("ydsz.message.consume.delay")
                        .tags("channel", channel)
                        .description("消息消费延迟")
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .register(meterRegistry)
        );
        timer.record(delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * P3-24: 记录发送成功。
     *
     * @param channel     通道类型
     * @param templateCode 模板编码
     * @param tenantId    租户 ID
     */
    public void recordSendSuccess(String channel, String templateCode, String tenantId) {
        incrementCounter("ydsz.message.send.total",
                "channel", channel, "result", "success",
                "template", templateCode != null ? templateCode : "none",
                "tenant", tenantId != null ? tenantId : "default");
        incrementCounter("ydsz.message.send.success",
                "channel", channel);
    }

    /**
     * P3-24: 记录发送失败。
     *
     * @param channel     通道类型
     * @param templateCode 模板编码
     * @param tenantId    租户 ID
     * @param errorType   错误类型
     */
    public void recordSendFailure(String channel, String templateCode, String tenantId, String errorType) {
        incrementCounter("ydsz.message.send.total",
                "channel", channel, "result", "failure",
                "template", templateCode != null ? templateCode : "none",
                "tenant", tenantId != null ? tenantId : "default");
        incrementCounter("ydsz.message.send.failure",
                "channel", channel, "error_type", errorType != null ? errorType : "unknown");
    }

    /**
     * P3-25: 记录异常。
     *
     * @param channel    通道类型
     * @param exceptionType 异常类型
     */
    public void recordException(String channel, String exceptionType) {
        incrementCounter("ydsz.message.exception",
                "channel", channel,
                "exception", exceptionType != null ? exceptionType : "unknown");
    }

    /**
     * 递增计数器（带缓存）。
     *
     * @param name 指标名
     * @param tags 标签键值对
     */
    private void incrementCounter(String name, String... tags) {
        String cacheKey = name + ":" + String.join(":", tags);
        Counter counter = counterCache.computeIfAbsent(cacheKey,
                k -> Counter.builder(name)
                        .tags(Tags.of(tags))
                        .register(meterRegistry)
        );
        counter.increment();
    }
}
