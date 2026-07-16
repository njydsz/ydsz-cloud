package com.njydsz.pmis.common.sentry.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InMemoryMetricsCollector 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("InMemoryMetricsCollector 指标读写测试")
class InMemoryMetricsCollectorTest {

    @Test
    @DisplayName("Counter 递增正确")
    void shouldIncrementCounter() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        collector.incrementCounter("test.counter", "test", null, 5);
        collector.incrementCounter("test.counter", "test", null, 3);
        assertThat(collector.getCounterValue("test.counter", null)).isEqualTo(8.0);
    }

    @Test
    @DisplayName("Gauge 设值原子且正确")
    void shouldSetGaugeAtomically() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        collector.setGauge("test.gauge", "test", null, 42.0);
        assertThat(collector.getGaugeValue("test.gauge", null)).isEqualTo(42.0);
        collector.setGauge("test.gauge", "test", null, 99.0);
        assertThat(collector.getGaugeValue("test.gauge", null)).isEqualTo(99.0);
    }

    @Test
    @DisplayName("Timer 记录正确")
    void shouldRecordTimer() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        collector.recordTimer("test.timer", "test", null, Duration.ofMillis(100));
        collector.recordTimer("test.timer", "test", null, Duration.ofMillis(200));
        assertThat(collector.getTimerAvgMillis("test.timer", null)).isEqualTo(150.0);
    }

    @Test
    @DisplayName("带标签的指标独立存储")
    void shouldStoreTagsSeparately() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        Map<String, String> tags1 = Map.of("method", "GET");
        Map<String, String> tags2 = Map.of("method", "POST");
        collector.incrementCounter("test.counter", "test", tags1, 1);
        collector.incrementCounter("test.counter", "test", tags2, 2);
        assertThat(collector.getCounterValue("test.counter", tags1)).isEqualTo(1.0);
        assertThat(collector.getCounterValue("test.counter", tags2)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("始终可用")
    void shouldBeAlwaysAvailable() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        assertThat(collector.isAvailable()).isTrue();
        assertThat(collector.getName()).isEqualTo("in-memory");
    }

    @Test
    @DisplayName("Counter 快照正确")
    void shouldSnapshotCounters() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        collector.incrementCounter("c1", "test", null, 1);
        collector.incrementCounter("c2", "test", null, 2);
        Map<String, Double> snapshot = collector.snapshotCounters();
        assertThat(snapshot).containsEntry("c1", 1.0);
        assertThat(snapshot).containsEntry("c2", 2.0);
    }
}
