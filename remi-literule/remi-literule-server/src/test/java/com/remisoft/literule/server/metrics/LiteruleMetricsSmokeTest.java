package com.remisoft.literule.server.metrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LiteruleMetricsHolder} 集成冒烟测试。
 *
 * <p>验证：
 * <ul>
 *   <li>Counter 递增后可通过 registry 读回正确值</li>
 *   <li>Timer 可记录至少一次操作并返回 count >= 1</li>
 *   <li>按 ruleId/tag 维度区分的多 Counter 互相独立</li>
 * </ul>
 *
 * <p>纯内存 SimpleMeterRegistry，无需 Spring 容器。
 */
class LiteruleMetricsSmokeTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        LiteruleMetricsHolder.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        LiteruleMetricsHolder.bindTo(null);
    }

    @Nested
    @DisplayName("literule.hit_total — 规则命中计数")
    class HitTotalTests {

        @Test
        @DisplayName("递增一次后值为 1")
        void incrementOnce_returnsOne() {
            LiteruleMetricsHolder.incrementHit("RULE-001", "DEFAULT");

            double value = registry.get("literule.hit_total")
                    .tags("rule_id", "RULE-001", "tag", "DEFAULT")
                    .counter().count();
            assertThat(value).isEqualTo(1.0);
        }

        @Test
        @DisplayName("递增三次后值为 3")
        void incrementThreeTimes_returnsThree() {
            LiteruleMetricsHolder.incrementHit("RULE-002", "APPROVE");
            LiteruleMetricsHolder.incrementHit("RULE-002", "APPROVE");
            LiteruleMetricsHolder.incrementHit("RULE-002", "APPROVE");

            double value = registry.get("literule.hit_total")
                    .tags("rule_id", "RULE-002", "tag", "APPROVE")
                    .counter().count();
            assertThat(value).isEqualTo(3.0);
        }

        @Test
        @DisplayName("不同 ruleId/tag 的 Counter 互相独立")
        void differentRuleId_areIndependent() {
            LiteruleMetricsHolder.incrementHit("RULE-A", "DEFAULT");
            LiteruleMetricsHolder.incrementHit("RULE-A", "DEFAULT");
            LiteruleMetricsHolder.incrementHit("RULE-B", "APPROVE");

            double valueA = registry.get("literule.hit_total")
                    .tags("rule_id", "RULE-A", "tag", "DEFAULT")
                    .counter().count();
            double valueB = registry.get("literule.hit_total")
                    .tags("rule_id", "RULE-B", "tag", "APPROVE")
                    .counter().count();

            assertThat(valueA).isEqualTo(2.0);
            assertThat(valueB).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("literule.evaluation_duration — 规则评估耗时")
    class EvaluationDurationTests {

        @Test
        @DisplayName("记录一次操作后 count >= 1")
        void recordOnce_countAtLeastOne() {
            LiteruleMetricsHolder.recordEvaluationDuration("RULE-001", 150L);

            var timer = registry.get("literule.evaluation_duration")
                    .tags("rule_id", "RULE-001")
                    .timer();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("负值输入被静默忽略，不影响计数")
        void negativeDuration_isIgnored() {
            LiteruleMetricsHolder.recordEvaluationDuration("RULE-NEG", -1L);

            // 查找对应 timer，由于负值被忽略，registry 中不存在该 meter
            assertThat(registry.find("literule.evaluation_duration")
                    .tags("rule_id", "RULE-NEG")
                    .timer()).isNull();
        }
    }

    @Nested
    @DisplayName("literule.error_total — 规则评估失败计数")
    class ErrorTotalTests {

        @Test
        @DisplayName("递增后返回正确值")
        void increment_returnsCorrectValue() {
            LiteruleMetricsHolder.incrementError("RULE-ERR");
            LiteruleMetricsHolder.incrementError("RULE-ERR");

            double value = registry.get("literule.error_total")
                    .tags("rule_id", "RULE-ERR")
                    .counter().count();
            assertThat(value).isEqualTo(2.0);
        }
    }
}
