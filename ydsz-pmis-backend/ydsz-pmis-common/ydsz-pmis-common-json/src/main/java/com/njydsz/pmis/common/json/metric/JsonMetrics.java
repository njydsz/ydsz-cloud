package com.njydsz.pmis.common.json.metric;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Json 指标监控配置。
 *
 * <p>当 classpath 存在 Micrometer {@link MeterRegistry} 时自动生效，
 * 提供序列化/反序列化的性能指标收集。
 *
 * <p><b>指标列表：</b>
 * <ul>
 *   <li>{@code pmis.json.serialize.duration} — 序列化耗时（P50/P90/P99）</li>
 *   <li>{@code pmis.json.deserialize.duration} — 反序列化耗时（P50/P90/P99）</li>
 *   <li>{@code pmis.json.serialize.success} — 序列化成功次数</li>
 *   <li>{@code pmis.json.serialize.failure} — 序列化失败次数</li>
 *   <li>{@code pmis.json.deserialize.failure} — 反序列化失败次数</li>
 * </ul>
 *
 * @since 1.3.0
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class JsonMetrics implements JsonMetricsCallback {

    private static final Logger log = LoggerFactory.getLogger(JsonMetrics.class);

    private final MeterRegistry meterRegistry;

    private final Timer serializeTimer;
    private final Timer deserializeTimer;
    private final Counter serializeSuccessCounter;
    private final Counter serializeFailureCounter;
    private final Counter deserializeFailureCounter;

    /**
     * 构造函数，初始化 Micrometer 指标。
     *
     * @param meterRegistry MeterRegistry（可为 null）
     */
    public JsonMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            this.serializeTimer = Timer.builder("pmis.json.serialize.duration")
                    .description("Json serialization duration")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .register(meterRegistry);
            this.deserializeTimer = Timer.builder("pmis.json.deserialize.duration")
                    .description("Json deserialization duration")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .register(meterRegistry);
            this.serializeSuccessCounter = Counter.builder("pmis.json.serialize.success")
                    .description("Json serialization success count")
                    .register(meterRegistry);
            this.serializeFailureCounter = Counter.builder("pmis.json.serialize.failure")
                    .description("Json serialization failure count")
                    .register(meterRegistry);
            this.deserializeFailureCounter = Counter.builder("pmis.json.deserialize.failure")
                    .description("Json deserialization failure count")
                    .register(meterRegistry);
            log.info("[Json] 注册 Micrometer 指标监控");
        } else {
            this.serializeTimer = null;
            this.deserializeTimer = null;
            this.serializeSuccessCounter = null;
            this.serializeFailureCounter = null;
            this.deserializeFailureCounter = null;
            log.debug("[Json] MeterRegistry 不存在，跳过指标监控注册");
        }
    }

    @Override
    public void onSerializeSuccess(long durationNanos) {
        if (serializeTimer != null) {
            serializeTimer.record(Duration.ofNanos(durationNanos));
            serializeSuccessCounter.increment();
        }
    }

    @Override
    public void onSerializeFailure() {
        if (serializeFailureCounter != null) {
            serializeFailureCounter.increment();
        }
    }

    @Override
    public void onDeserializeSuccess(long durationNanos) {
        if (deserializeTimer != null) {
            deserializeTimer.record(Duration.ofNanos(durationNanos));
        }
    }

    @Override
    public void onDeserializeFailure() {
        if (deserializeFailureCounter != null) {
            deserializeFailureCounter.increment();
        }
    }

    /**
     * 是否已启用监控。
     *
     * @return true 如果 MeterRegistry 存在
     */
    public boolean isEnabled() {
        return meterRegistry != null;
    }
}
