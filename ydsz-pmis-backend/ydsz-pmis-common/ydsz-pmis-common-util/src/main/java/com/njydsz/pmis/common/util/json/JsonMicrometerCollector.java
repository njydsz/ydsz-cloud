package com.njydsz.pmis.common.util.json;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * JSON 处理 Micrometer 指标收集器
 *
 * <p>独立的顶级类，通过 {@code @ConditionalOnClass(MeterRegistry.class)} 控制加载，
 * 避免对 Micrometer 的编译期硬依赖。
 *
 * <p>注册的指标（统一使用点分隔命名，与 ydsz-pmis-common 其他模块保持一致）：
 * <ul>
 *   <li>{@code json.serialize.total} - JSON 序列化成功总数（Counter）</li>
 *   <li>{@code json.serialize.failed.total} - JSON 序列化失败总数（Counter）</li>
 *   <li>{@code json.serialize.duration} - JSON 序列化耗时（Timer，单位毫秒）</li>
 *   <li>{@code json.deserialize.total} - JSON 反序列化成功总数（Counter）</li>
 *   <li>{@code json.deserialize.failed.total} - JSON 反序列化失败总数（Counter）</li>
 *   <li>{@code json.deserialize.duration} - JSON 反序列化耗时（Timer，单位毫秒）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
public class JsonMicrometerCollector {

    private static final String METRIC_SERIALIZE_TOTAL = "json.serialize.total";
    private static final String METRIC_SERIALIZE_FAILED_TOTAL = "json.serialize.failed.total";
    private static final String METRIC_SERIALIZE_DURATION = "json.serialize.duration";
    private static final String METRIC_DESERIALIZE_TOTAL = "json.deserialize.total";
    private static final String METRIC_DESERIALIZE_FAILED_TOTAL = "json.deserialize.failed.total";
    private static final String METRIC_DESERIALIZE_DURATION = "json.deserialize.duration";

    private final MeterRegistry registry;

    public JsonMicrometerCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordSerializeSuccess(long timeNanos) {
        Timer.builder(METRIC_SERIALIZE_DURATION)
                .description("JSON serialization duration")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(timeNanos));
        Counter.builder(METRIC_SERIALIZE_TOTAL)
                .description("Total number of successful JSON serializations")
                .register(registry)
                .increment();
    }

    void recordSerializeFail() {
        Counter.builder(METRIC_SERIALIZE_FAILED_TOTAL)
                .description("Total number of failed JSON serializations")
                .register(registry)
                .increment();
    }

    void recordDeserializeSuccess(long timeNanos) {
        Timer.builder(METRIC_DESERIALIZE_DURATION)
                .description("JSON deserialization duration")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(timeNanos));
        Counter.builder(METRIC_DESERIALIZE_TOTAL)
                .description("Total number of successful JSON deserializations")
                .register(registry)
                .increment();
    }

    void recordDeserializeFail() {
        Counter.builder(METRIC_DESERIALIZE_FAILED_TOTAL)
                .description("Total number of failed JSON deserializations")
                .register(registry)
                .increment();
    }
}
