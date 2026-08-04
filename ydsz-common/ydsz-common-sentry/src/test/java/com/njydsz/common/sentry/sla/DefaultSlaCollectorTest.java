package com.njydsz.common.sentry.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.sentry.domain.SlaDefinition;
import com.njydsz.common.sentry.metrics.InMemoryMetricsCollector;

/**
 * DefaultSlaCollector 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DefaultSlaCollector SLA 采集测试")
class DefaultSlaCollectorTest {

    private InMemoryMetricsCollector metrics;
    private DefaultSlaCollector slaCollector;

    @BeforeEach
    void setUp() {
        metrics = new InMemoryMetricsCollector();
        slaCollector = new DefaultSlaCollector(metrics);
    }

    @Test
    @DisplayName("注册 SLA 定义")
    void shouldRegisterSlaDefinition() {
        SlaDefinition def = new SlaDefinition();
        def.setName("test_sla");
        def.setThresholdMillis(500);
        slaCollector.register(def);
        assertThat(slaCollector.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("正常执行不记录 SLA 违反")
    void shouldNotRecordViolationOnNormalExecution() {
        SlaDefinition def = new SlaDefinition();
        def.setName("test_sla");
        def.setThresholdMillis(500);
        slaCollector.register(def);
        slaCollector.recordTotal("test_sla", 100, true);
        assertThat(metrics.getCounterValue("ydsz.sla.violation", Map.of("sla", "test_sla")))
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("超时执行记录 SLA 违反")
    void shouldRecordViolationOnTimeout() {
        SlaDefinition def = new SlaDefinition();
        def.setName("test_sla");
        def.setThresholdMillis(500);
        slaCollector.register(def);
        slaCollector.recordTotal("test_sla", 600, true);
        assertThat(metrics.getCounterValue("ydsz.sla.violation", Map.of("sla", "test_sla")))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("失败执行记录失败次数")
    void shouldRecordFailure() {
        SlaDefinition def = new SlaDefinition();
        def.setName("test_sla");
        def.setThresholdMillis(500);
        slaCollector.register(def);
        slaCollector.recordTotal("test_sla", 100, false);
        assertThat(metrics.getCounterValue("ydsz.sla.total.failed",
                Map.of("sla", "test_sla", "success", "false")))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("null name 不抛异常")
    void shouldHandleNullNameGracefully() {
        assertThatNoException().isThrownBy(() -> slaCollector.recordTotal(null, 100, true));
        assertThatNoException().isThrownBy(() -> slaCollector.record(null, "step", 100, true));
    }

    @Test
    @DisplayName("步骤记录正确")
    void shouldRecordStep() {
        SlaDefinition def = new SlaDefinition();
        def.setName("test_sla");
        def.setThresholdMillis(1000);
        def.addStep("db_query", 200);
        slaCollector.register(def);
        slaCollector.record("test_sla", "db_query", 150, true);
        assertThat(metrics.getTimerAvgMillis("ydsz.sla.step.duration",
                Map.of("sla", "test_sla", "step", "db_query", "success", "true")))
                .isEqualTo(150.0);
    }
}
