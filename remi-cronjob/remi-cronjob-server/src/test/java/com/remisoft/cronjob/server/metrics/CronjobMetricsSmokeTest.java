package com.remisoft.cronjob.server.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CronjobMetricsHolder} 集成冒烟测试。
 *
 * <p>验证：
 * <ul>
 *   <li>Counter 递增后可通过 registry 读回正确值</li>
 *   <li>Timer 可记录至少一次操作并返回 count >= 1</li>
 *   <li>分片成功/失败 Counter 按 jobName + shardIndex 正确区分</li>
 * </ul>
 *
 * <p>纯内存 SimpleMeterRegistry，无需 Spring 容器。
 */
class CronjobMetricsSmokeTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        CronjobMetricsHolder.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        CronjobMetricsHolder.bindTo(null);
    }

    @Nested
    @DisplayName("cronjob.execution_total - task execution count")
    class ExecutionTotalTests {

        @Test
        @DisplayName("increment once returns 1")
        void incrementOnce_returnsOne() {
            CronjobMetricsHolder.incrementExecution("order-sync");

            double value = registry.get("cronjob.execution_total")
                    .tags("job_name", "order-sync")
                    .counter().count();
            assertThat(value).isEqualTo(1.0);
        }

        @Test
        @DisplayName("different jobName counters are independent")
        void differentJobName_areIndependent() {
            CronjobMetricsHolder.incrementExecution("job-A");
            CronjobMetricsHolder.incrementExecution("job-A");
            CronjobMetricsHolder.incrementExecution("job-B");

            double valueA = registry.get("cronjob.execution_total")
                    .tags("job_name", "job-A")
                    .counter().count();
            double valueB = registry.get("cronjob.execution_total")
                    .tags("job_name", "job-B")
                    .counter().count();

            assertThat(valueA).isEqualTo(2.0);
            assertThat(valueB).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("cronjob.execution_duration - task execution duration")
    class ExecutionDurationTests {

        @Test
        @DisplayName("record once then count >= 1")
        void recordOnce_countAtLeastOne() {
            CronjobMetricsHolder.recordExecutionDuration("report-daily", 500L);

            var timer = registry.get("cronjob.execution_duration")
                    .tags("job_name", "report-daily")
                    .timer();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("negative value is silently ignored")
        void negativeDuration_isIgnored() {
            CronjobMetricsHolder.recordExecutionDuration("negative-job", -1L);

            assertThat(registry.find("cronjob.execution_duration")
                    .tags("job_name", "negative-job")
                    .timer()).isNull();
        }
    }

    @Nested
    @DisplayName("cronjob.shard_success_total / shard_failure_total - shard counts")
    class ShardTests {

        @Test
        @DisplayName("shard success counter increments")
        void shardSuccess_increment() {
            CronjobMetricsHolder.incrementShardSuccess("heavy-compute", 0);
            CronjobMetricsHolder.incrementShardSuccess("heavy-compute", 0);

            double value = registry.get("cronjob.shard_success_total")
                    .tags("job_name", "heavy-compute", "shard_index", "0")
                    .counter().count();
            assertThat(value).isEqualTo(2.0);
        }

        @Test
        @DisplayName("shard failure counter increments")
        void shardFailure_increment() {
            CronjobMetricsHolder.incrementShardFailure("heavy-compute", 1);

            double value = registry.get("cronjob.shard_failure_total")
                    .tags("job_name", "heavy-compute", "shard_index", "1")
                    .counter().count();
            assertThat(value).isEqualTo(1.0);
        }

        @Test
        @DisplayName("counters with different shard indexes are independent")
        void differentShardIndex_areIndependent() {
            CronjobMetricsHolder.incrementShardSuccess("shard-job", 0);
            CronjobMetricsHolder.incrementShardSuccess("shard-job", 1);
            CronjobMetricsHolder.incrementShardFailure("shard-job", 2);

            double shard0 = registry.get("cronjob.shard_success_total")
                    .tags("job_name", "shard-job", "shard_index", "0")
                    .counter().count();
            double shard1 = registry.get("cronjob.shard_success_total")
                    .tags("job_name", "shard-job", "shard_index", "1")
                    .counter().count();
            double shard2Fail = registry.get("cronjob.shard_failure_total")
                    .tags("job_name", "shard-job", "shard_index", "2")
                    .counter().count();

            assertThat(shard0).isEqualTo(1.0);
            assertThat(shard1).isEqualTo(1.0);
            assertThat(shard2Fail).isEqualTo(1.0);
        }
    }
}
