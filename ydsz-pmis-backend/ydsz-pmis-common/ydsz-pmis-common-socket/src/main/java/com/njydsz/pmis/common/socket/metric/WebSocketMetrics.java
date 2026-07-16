package com.njydsz.pmis.common.socket.metric;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * WebSocket Micrometer 指标收集器。
 *
 * <p>注册以下指标：
 * <ul>
 *   <li>{@code pmis.websocket.push.total}（Counter）— 推送次数，tag: type, success</li>
 *   <li>{@code pmis.websocket.push.duration}（Timer）— 推送耗时</li>
 *   <li>{@code pmis.websocket.connections.active}（Gauge）— 活跃连接数（由业务侧注册）</li>
 * </ul>
 *
 * <p>当 MeterRegistry 不在 classpath 时降级为空操作（no-op）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class WebSocketMetrics {

    private static final String METRIC_PUSH_TOTAL = "pmis.websocket.push.total";
    private static final String METRIC_PUSH_DURATION = "pmis.websocket.push.duration";

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
     * 记录推送耗时。
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
                .register(meterRegistry)
                .record(duration);
    }
}
