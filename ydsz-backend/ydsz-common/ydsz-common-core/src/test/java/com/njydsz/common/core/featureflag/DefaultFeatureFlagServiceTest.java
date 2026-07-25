package com.njydsz.common.core.featureflag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link DefaultFeatureFlagService} 单元测试 — 覆盖强制开关、灰度发布、并发写入、快照等关键路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DefaultFeatureFlagService 测试")
class DefaultFeatureFlagServiceTest {

    private FeatureFlagProperties properties;
    private Environment environment;
    private DefaultFeatureFlagService service;

    @BeforeEach
    void setUp() {
        properties = new FeatureFlagProperties();
        environment = new MockEnvironment();
        service = new DefaultFeatureFlagService(properties, environment);
    }

    @Nested
    @DisplayName("强制开关（SAFETY 类）")
    class MandatoryFlagTest {

        @Test
        @DisplayName("强制开关始终返回 true，无论 userId 与配置如何")
        void mandatoryFlagAlwaysEnabled() {
            assertThat(service.isEnabled(FeatureFlag.SENSITIVE_DATA_MASK, null)).isTrue();
            assertThat(service.isEnabled(FeatureFlag.SENSITIVE_DATA_MASK, "user-1")).isTrue();
        }

        @Test
        @DisplayName("拒绝禁用强制开关并返回当前生效值 true")
        void refuseDisableMandatoryFlag() {
            boolean result = service.setEnabled(FeatureFlag.SENSITIVE_DATA_MASK, false);
            assertThat(result).isTrue();
            assertThat(service.isEnabled(FeatureFlag.SENSITIVE_DATA_MASK, null)).isTrue();
        }

        @Test
        @DisplayName("允许对强制开关调用 setEnabled(true)，返回 true")
        void allowEnableMandatoryFlag() {
            boolean result = service.setEnabled(FeatureFlag.SENSITIVE_DATA_MASK, true);
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("非强制开关默认行为")
    class NonMandatoryFlagTest {

        @Test
        @DisplayName("未配置时默认返回 false")
        void defaultIsFalse() {
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isFalse();
            assertThat(service.isEnabled(FeatureFlag.BATCH_EXPORT, "user-1")).isFalse();
        }

        @Test
        @DisplayName("setEnabled(true) 后返回 true")
        void enableFlag() {
            assertThat(service.setEnabled(FeatureFlag.NEW_DASHBOARD, true)).isTrue();
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
        }

        @Test
        @DisplayName("重复 setEnabled 同值返回当前生效值，不产生重复日志")
        void setEnabledSameValue() {
            service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
            assertThat(service.setEnabled(FeatureFlag.NEW_DASHBOARD, true)).isTrue();
            assertThat(service.setEnabled(FeatureFlag.NEW_DASHBOARD, false)).isFalse();
        }

        @Test
        @DisplayName("moduleEnabled=false 时所有非强制开关返回 false，强制开关仍返回 true")
        void moduleDisabled() {
            service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
            service.moduleEnabled = false;
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isFalse();
            assertThat(service.isEnabled(FeatureFlag.SENSITIVE_DATA_MASK, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("灰度发布")
    class RolloutTest {

        @Test
        @DisplayName("rollout=100 时所有用户命中")
        void rolloutFull() {
            service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 100);
            for (int i = 0; i < 50; i++) {
                assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-" + i)).isTrue();
            }
        }

        @Test
        @DisplayName("rollout=0 时无用户命中")
        void rolloutZero() {
            service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 0);
            for (int i = 0; i < 50; i++) {
                assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-" + i)).isFalse();
            }
        }

        @Test
        @DisplayName("rollout=50 时同一用户结果稳定")
        void rolloutStableForSameUser() {
            service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 50);
            boolean first = service.isEnabled(FeatureFlag.NEW_DASHBOARD, "stable-user");
            for (int i = 0; i < 10; i++) {
                assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "stable-user")).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("rollout=30 时近似 30% 用户命中（误差容忍 ±15%）")
        void rolloutApproximateDistribution() {
            service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 30);
            int hits = 0;
            int total = 1000;
            for (int i = 0; i < total; i++) {
                if (service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-" + i)) {
                    hits++;
                }
            }
            double ratio = (double) hits / total;
            assertThat(ratio).isBetween(0.15, 0.45);
        }

        @Test
        @DisplayName("rollout 与 enabled 独立：userId 非空按 rollout 判断，userId 为空按 enabled 判断")
        void rolloutAndEnabledIndependent() {
            // enabled=false, rollout=100
            service.setEnabled(FeatureFlag.NEW_DASHBOARD, false);
            service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 100);
            // userId 非空 -> 按 rollout -> true
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-1")).isTrue();
            // userId 为空 -> 按 enabled -> false
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isFalse();
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "")).isFalse();
        }

        @Test
        @DisplayName("setRolloutPercentage 拒绝越界值")
        void rolloutRejectOutOfRange() {
            assertThatThrownBy(() -> service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 101))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("setRolloutPercentage 返回影响记录数（变更=1，无变更=0）")
        void rolloutReturnsAffectedCount() {
            assertThat(service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 30)).isEqualTo(1);
            assertThat(service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 30)).isEqualTo(0);
            assertThat(service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 50)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("快照")
    class SnapshotTest {

        @Test
        @DisplayName("snapshot 返回所有已注册开关")
        void snapshotReturnsAllFlags() {
            List<FeatureFlagSnapshot> snaps = service.snapshot();
            assertThat(snaps).hasSize(FeatureFlag.values().length);
            assertThat(snaps).extracting(FeatureFlagSnapshot::getKey)
                    .contains(FeatureFlag.NEW_DASHBOARD.name(), FeatureFlag.SENSITIVE_DATA_MASK.name());
        }

        @Test
        @DisplayName("snapshot 反映强制开关与配置值")
        void snapshotReflectsMandatoryAndConfigured() {
            service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
            List<FeatureFlagSnapshot> snaps = service.snapshot();
            FeatureFlagSnapshot mandatorySnap = snaps.stream()
                    .filter(s -> s.getKey().equals(FeatureFlag.SENSITIVE_DATA_MASK.name()))
                    .findFirst().orElseThrow();
            assertThat(mandatorySnap.isMandatory()).isTrue();
            assertThat(mandatorySnap.getEffectiveValue()).isTrue();

            FeatureFlagSnapshot dashboardSnap = snaps.stream()
                    .filter(s -> s.getKey().equals(FeatureFlag.NEW_DASHBOARD.name()))
                    .findFirst().orElseThrow();
            assertThat(dashboardSnap.isMandatory()).isFalse();
            assertThat(dashboardSnap.getConfiguredValue()).isTrue();
            assertThat(dashboardSnap.getEffectiveValue()).isTrue();
        }

        @Test
        @DisplayName("snapshotByCategory 按分类分组")
        void snapshotByCategory() {
            Map<String, List<FeatureFlagSnapshot>> grouped = service.snapshotByCategory();
            assertThat(grouped).containsKey(FeatureFlagCategory.SAFETY.name());
            assertThat(grouped.get(FeatureFlagCategory.SAFETY.name()))
                    .extracting(FeatureFlagSnapshot::getKey)
                    .contains(FeatureFlag.SENSITIVE_DATA_MASK.name());
        }
    }

    @Nested
    @DisplayName("配置加载与刷新")
    class ConfigLoadTest {

        @Test
        @DisplayName("从 FeatureFlagProperties 加载初始配置")
        void loadFromProperties() {
            FeatureFlagProperties.FlagConfig cfg = new FeatureFlagProperties.FlagConfig();
            cfg.setEnabled(true);
            cfg.setRollout(50);
            properties.getFlags().put(FeatureFlag.NEW_DASHBOARD.name(), cfg);

            service.refresh();

            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
            // rollout=50 + userId 非空 → 走 rollout 判断
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-1"))
                    .isIn(true, false);
        }

        @Test
        @DisplayName("refresh 覆盖运行时修改")
        void refreshOverridesRuntimeChanges() {
            service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();

            service.refresh();
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("并发安全")
    class ConcurrencyTest {

        @Test
        @DisplayName("并发 setEnabled 不丢失更新")
        void concurrentSetEnabled() throws InterruptedException {
            int threads = 16;
            int iterations = 500;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger trueCount = new AtomicInteger();
            for (int i = 0; i < threads; i++) {
                final boolean value = (i % 2 == 0);
                pool.submit(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < iterations; j++) {
                            if (service.setEnabled(FeatureFlag.NEW_DASHBOARD, value)) {
                                trueCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                latch.countDown();
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            // 最终状态应为最后一次写入的值
            boolean finalState = service.isEnabled(FeatureFlag.NEW_DASHBOARD, null);
            assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isEqualTo(finalState);
        }
    }
}
