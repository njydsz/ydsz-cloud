package com.njydsz.pmis.common.util.json;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonMetrics 单元测试
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
@DisplayName("JsonMetrics - JSON 处理指标收集器测试")
class JsonMetricsTest {

    @Test
    @DisplayName("内部计数器记录序列化/反序列化成功与失败")
    void shouldRecordInternalCounters() {
        JsonMetrics metrics = new JsonMetrics();

        metrics.recordSerializeSuccess(1_000_000L);
        metrics.recordSerializeSuccess(2_000_000L);
        metrics.recordSerializeFail();
        metrics.recordDeserializeSuccess(500_000L);
        metrics.recordDeserializeFail();
        metrics.recordDeserializeFail();

        assertEquals(2, metrics.getSerializeSuccessCount());
        assertEquals(1, metrics.getSerializeFailCount());
        assertEquals(1, metrics.getDeserializeSuccessCount());
        assertEquals(2, metrics.getDeserializeFailCount());
        assertEquals(3_000_000L, metrics.getTotalSerializeTimeNanos());
        assertEquals(500_000L, metrics.getTotalDeserializeTimeNanos());
        assertEquals(1.5, metrics.getAverageSerializeTimeMillis(), 0.0001);
        assertEquals(0.5, metrics.getAverageDeserializeTimeMillis(), 0.0001);
    }

    @Test
    @DisplayName("绑定 Micrometer 后注册 Counter 与 Timer 指标")
    void shouldRegisterMicrometerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JsonMetrics metrics = new JsonMetrics();
        metrics.bindMeterRegistry(registry);

        metrics.recordSerializeSuccess(1_000_000L);
        metrics.recordSerializeFail();
        metrics.recordDeserializeSuccess(500_000L);
        metrics.recordDeserializeFail();

        assertEquals(1, registry.counter("json.serialize.total").count());
        assertEquals(1, registry.counter("json.serialize.failed.total").count());
        assertEquals(1, registry.counter("json.deserialize.total").count());
        assertEquals(1, registry.counter("json.deserialize.failed.total").count());

        assertNotNull(registry.find("json.serialize.duration").timer());
        assertNotNull(registry.find("json.deserialize.duration").timer());
        assertEquals(1, registry.timer("json.serialize.duration").count());
        assertEquals(1, registry.timer("json.deserialize.duration").count());
    }

    @Test
    @DisplayName("toString 输出包含计数与平均耗时")
    void shouldOutputMeaningfulToString() {
        JsonMetrics metrics = new JsonMetrics();
        metrics.recordSerializeSuccess(1_000_000L);
        metrics.recordDeserializeFail();

        String text = metrics.toString();
        assertTrue(text.contains("serializeSuccess=1"));
        assertTrue(text.contains("deserializeFail=1"));
        assertTrue(text.contains("avgSerialize="));
    }
}
