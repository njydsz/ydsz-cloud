package com.njydsz.workflow.server.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WorkflowMetricsHolder} 集成冒烟测试。
 *
 * <p>验证：
 * <ul>
 *   <li>Counter 递增后可通过 registry 读回正确值</li>
 *   <li>Timer 可记录至少一次操作并返回 count >= 1</li>
 *   <li>按 processDefKey 维度区分的多 Counter 互相独立</li>
 * </ul>
 *
 * <p>纯内存 SimpleMeterRegistry，无需 Spring 容器。
 */
class WorkflowMetricsSmokeTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        WorkflowMetricsHolder.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        WorkflowMetricsHolder.bindTo(null);
    }

    @Nested
    @DisplayName("workflow.start_total — 流程启动计数")
    class StartTotalTests {

        @Test
        @DisplayName("递增一次后值为 1")
        void incrementOnce_returnsOne() {
            WorkflowMetricsHolder.incrementStart("project-initiation");

            double value = registry.get("workflow.start_total")
                    .tags("process_def_key", "project-initiation")
                    .counter().count();
            assertThat(value).isEqualTo(1.0);
        }

        @Test
        @DisplayName("不同 processDefKey 的 Counter 互相独立")
        void differentProcessDefKey_areIndependent() {
            WorkflowMetricsHolder.incrementStart("flow-A");
            WorkflowMetricsHolder.incrementStart("flow-A");
            WorkflowMetricsHolder.incrementStart("flow-B");

            double valueA = registry.get("workflow.start_total")
                    .tags("process_def_key", "flow-A")
                    .counter().count();
            double valueB = registry.get("workflow.start_total")
                    .tags("process_def_key", "flow-B")
                    .counter().count();

            assertThat(valueA).isEqualTo(2.0);
            assertThat(valueB).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("workflow.task_complete_total — 任务完成计数")
    class TaskCompleteTotalTests {

        @Test
        @DisplayName("递增后值为正")
        void increment_returnsPositiveValue() {
            WorkflowMetricsHolder.incrementTaskComplete("purchase-approval");
            WorkflowMetricsHolder.incrementTaskComplete("purchase-approval");

            double value = registry.get("workflow.task_complete_total")
                    .tags("process_def_key", "purchase-approval")
                    .counter().count();
            assertThat(value).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("workflow.execution_duration — 流程执行耗时")
    class ExecutionDurationTests {

        @Test
        @DisplayName("记录一次操作后 count >= 1")
        void recordOnce_countAtLeastOne() {
            WorkflowMetricsHolder.recordExecutionDuration("expense-report", 3200L);

            var timer = registry.get("workflow.execution_duration")
                    .tags("process_def_key", "expense-report")
                    .timer();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("负值输入被静默忽略")
        void negativeDuration_isIgnored() {
            WorkflowMetricsHolder.recordExecutionDuration("negative-flow", -100L);

            assertThat(registry.find("workflow.execution_duration")
                    .tags("process_def_key", "negative-flow")
                    .timer()).isNull();
        }
    }

    @Nested
    @DisplayName("workflow.task_timeout_total — 流程卡住/超时计数")
    class TaskTimeoutTotalTests {

        @Test
        @DisplayName("递增后值为正")
        void increment_returnsPositiveValue() {
            WorkflowMetricsHolder.incrementTaskTimeout("slow-flow");

            double value = registry.get("workflow.task_timeout_total")
                    .tags("process_def_key", "slow-flow")
                    .counter().count();
            assertThat(value).isEqualTo(1.0);
        }
    }
}
