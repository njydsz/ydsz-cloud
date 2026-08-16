package com.njydsz.common.socket.metric;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * WebSocket Micrometer 指标收集器（P1-4）。
 *
 * <p>注册以下指标：
 * <ul>
 *   <li>{@code ydsz.websocket.push.total}（Counter）— 推送次数，tag: type, success</li>
 *   <li>{@code ydsz.websocket.push.duration}（Timer）— 推送耗时（含 P99 百分位）</li>
 *   <li>{@code ydsz.websocket.connections.active}（Gauge）— 活跃连接数</li>
 *   <li>{@code ydsz.websocket.retry.queue.size}（Gauge）— 重试队列积压</li>
 *   <li>{@code ydsz.websocket.deadletter.queue.size}（Gauge）— 死信队列积压</li>
 *   <li>{@code ydsz.websocket.offline.cache.size}（Gauge）— 离线消息缓存量</li>
 *   <li>{@code ydsz.websocket.ack.pending}（Gauge）— 待确认 ACK 数量</li>
 *   <li>{@code ydsz.websocket.slow_connections.total}（Gauge）— 慢连接数</li>
 *   <li>{@code ydsz.websocket.circuitbreaker.state}（Gauge）— 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）</li>
 * </ul>
 *
 * <p>当 MeterRegistry 不在 classpath 时降级为空操作（no-op）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WebSocketMetrics {

    private static final String METRIC_PUSH_TOTAL = "ydsz.websocket.push.total";
    private static final String METRIC_PUSH_DURATION = "ydsz.websocket.push.duration";
    private static final String METRIC_CONNECTIONS_ACTIVE = "ydsz.websocket.connections.active";
    private static final String METRIC_RETRY_QUEUE_SIZE = "ydsz.websocket.retry.queue.size";
    private static final String METRIC_DEADLETTER_QUEUE_SIZE = "ydsz.websocket.deadletter.queue.size";
    private static final String METRIC_OFFLINE_CACHE_SIZE = "ydsz.websocket.offline.cache.size";
    private static final String METRIC_ACK_PENDING = "ydsz.websocket.ack.pending";
    private static final String METRIC_SLOW_CONNECTIONS = "ydsz.websocket.slow_connections.total";
    private static final String METRIC_CIRCUIT_BREAKER_STATE = "ydsz.websocket.circuitbreaker.state";

    private final MeterRegistry meterRegistry;

    /**
     * 构造 WebSocketMetrics。
     *
     * @param meterRegistry MeterRegistry（可为 null，降级为 no-op）
     */
    public WebSocketMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 注册 Gauge 指标（必须在相关 Bean 创建后调用）。
     *
     * @param activeConnectionsSupplier 活跃连接数供应器
     * @param retryQueueSizeSupplier    重试队列大小供应器
     * @param deadLetterQueueSizeSupplier 死信队列大小供应器
     * @param offlineCacheSizeSupplier  离线缓存大小供应器
     * @param ackPendingSupplier        待确认 ACK 数量供应器
     * @param slowConnectionsSupplier   慢连接数供应器
     * @param circuitBreakerStateSupplier 熔断器状态供应器
     */
    public void registerGauges(
            Supplier<Long> activeConnectionsSupplier,
            Supplier<Long> retryQueueSizeSupplier,
            Supplier<Long> deadLetterQueueSizeSupplier,
            Supplier<Long> offlineCacheSizeSupplier,
            Supplier<Long> ackPendingSupplier,
            Supplier<Long> slowConnectionsSupplier,
            Supplier<Integer> circuitBreakerStateSupplier) {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder(METRIC_CONNECTIONS_ACTIVE, activeConnectionsSupplier, Supplier::get)
                .description("当前活跃 WebSocket 连接数")
                .register(meterRegistry);
        Gauge.builder(METRIC_RETRY_QUEUE_SIZE, retryQueueSizeSupplier, Supplier::get)
                .description("重试队列积压消息数")
                .register(meterRegistry);
        Gauge.builder(METRIC_DEADLETTER_QUEUE_SIZE, deadLetterQueueSizeSupplier, Supplier::get)
                .description("死信队列积压消息数")
                .register(meterRegistry);
        Gauge.builder(METRIC_OFFLINE_CACHE_SIZE, offlineCacheSizeSupplier, Supplier::get)
                .description("离线消息缓存量")
                .register(meterRegistry);
        Gauge.builder(METRIC_ACK_PENDING, ackPendingSupplier, Supplier::get)
                .description("待确认 ACK 消息数")
                .register(meterRegistry);
        Gauge.builder(METRIC_SLOW_CONNECTIONS, slowConnectionsSupplier, Supplier::get)
                .description("慢连接数量")
                .register(meterRegistry);
        Gauge.builder(METRIC_CIRCUIT_BREAKER_STATE, circuitBreakerStateSupplier, Supplier::get)
                .description("熔断器状态: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .register(meterRegistry);
    }

    /**
     * 记录一次推送结果。
     *
     * @param pushType 推送类型（USER / BROADCAST / TOPIC）
     * @param success  是否成功
     */
    public void recordPush(String pushType, boolean success) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_PUSH_TOTAL)
                .tags(Tags.of("type", pushType, "result", success ? "success" : "failure"))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录推送耗时（含 P99 百分位）。
     *
     * @param pushType 推送类型
     * @param duration 耗时
     */
    public void recordPushDuration(String pushType, Duration duration) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_PUSH_DURATION)
                .tags(Tags.of("type", pushType))
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * 获取底层 MeterRegistry（供高级用户使用）。
     *
     * @return MeterRegistry 实例，可能为 null
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
