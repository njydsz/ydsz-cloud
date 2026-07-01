package com.njydsz.pmis.common.chaos;

import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChaosService 单元测试 (批次 20 P3-1)
 */
@DisplayName("ChaosService 混沌工程")
class ChaosServiceTest {

    private FeatureFlagService featureFlagService;
    private ChaosService chaosService;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        chaosService = new ChaosService(featureFlagService);
    }

    @Test
    @DisplayName("未注册实验时, maybeInject 返回 NOT_TRIGGERED")
    void notTriggeredWhenUnregistered() {
        ChaosOutcome out = chaosService.maybeInject("UnknownService.method");
        assertThat(out).isEqualTo(ChaosOutcome.NOT_TRIGGERED);
    }

    @Test
    @DisplayName("实验 enabled=false 时, maybeInject 不触发")
    void notTriggeredWhenDisabled() {
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(false)
                .build());
        assertThat(chaosService.maybeInject("X.y")).isEqualTo(ChaosOutcome.NOT_TRIGGERED);
    }

    @Test
    @DisplayName("Feature flag 关闭时, 注入被拦截")
    void blockedByFlag() {
        when(featureFlagService.isEnabled(eq(FeatureFlag.CANARY_DEPLOY))).thenReturn(false);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(true)
                .exceptionClass("java.lang.IllegalStateException")
                .build());
        assertThat(chaosService.maybeInject("X.y")).isEqualTo(ChaosOutcome.BLOCKED_BY_FLAG);
    }

    @Test
    @DisplayName("LATENCY 实验: Feature flag 开启时注入延迟")
    void latencyInjected() throws Exception {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_LATENCY)
                .enabled(true)
                .latencyMs(50L)
                .build());
        long t0 = System.currentTimeMillis();
        ChaosOutcome out = chaosService.maybeInject("X.y");
        long elapsed = System.currentTimeMillis() - t0;
        assertThat(out).isEqualTo(ChaosOutcome.INJECTED);
        // 至少 sleep 了 50ms
        assertThat(elapsed).isGreaterThanOrEqualTo(45L);
    }

    @Test
    @DisplayName("EXCEPTION 实验: 抛出指定异常")
    void exceptionInjected() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(true)
                .exceptionClass("java.lang.IllegalArgumentException")
                .build());
        assertThatThrownBy(() -> chaosService.maybeInject("X.y"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EXCEPTION 未知类时降级为 RuntimeException")
    void exceptionFallback() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(true)
                .exceptionClass("not.existing.Class")
                .build());
        assertThatThrownBy(() -> chaosService.maybeInject("X.y"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("NETWORK_PARTITION 实验: 抛 RuntimeException, cause 为 ConnectException")
    void networkPartitionInjected() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_NETWORK_PARTITION)
                .enabled(true)
                .build());
        assertThatThrownBy(() -> chaosService.maybeInject("X.y"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(java.net.ConnectException.class);
    }

    @Test
    @DisplayName("ERROR_RATE=0.0 时, 概率未命中, 不会抛出")
    void errorRateZeroNeverThrows() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_ERROR_RATE)
                .enabled(true)
                .errorRate(0.0)
                .build());
        // 100 次调用都不应注入
        for (int i = 0; i < 100; i++) {
            assertThat(chaosService.maybeInject("X.y")).isEqualTo(ChaosOutcome.SKIPPED_PROBABILITY);
        }
    }

    @Test
    @DisplayName("ERROR_RATE=1.0 时, 100% 注入")
    void errorRateFullAlwaysThrows() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_ERROR_RATE)
                .enabled(true)
                .errorRate(1.0)
                .build());
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> chaosService.maybeInject("X.y"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("ERROR_RATE=0.5 时, 大约 50% 注入 (200 次抽样)")
    void errorRatePartial() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_ERROR_RATE)
                .enabled(true)
                .errorRate(0.5)
                .build());
        int hit = 0;
        int skip = 0;
        for (int i = 0; i < 200; i++) {
            // 重新调用前需重新注册 (因为 history 在增长)
            try {
                ChaosOutcome out = chaosService.maybeInject("X.y");
                if (out == ChaosOutcome.INJECTED) hit++;
                else if (out == ChaosOutcome.SKIPPED_PROBABILITY) skip++;
            } catch (RuntimeException e) {
                hit++;
            }
        }
        // hit ~ 100, skip ~ 100, ±30 容差
        assertThat(hit).isBetween(70, 130);
        assertThat(skip).isBetween(70, 130);
    }

    @Test
    @DisplayName("unregister 注销实验后不再触发")
    void unregister() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(true)
                .build());
        chaosService.unregister("X.y");
        assertThat(chaosService.maybeInject("X.y")).isEqualTo(ChaosOutcome.NOT_TRIGGERED);
    }

    @Test
    @DisplayName("list 返回所有已注册实验")
    void listExperiments() {
        chaosService.register(ChaosExperiment.builder().target("X.y").type("LATENCY").enabled(true).build());
        chaosService.register(ChaosExperiment.builder().target("X.z").type("EXCEPTION").enabled(true).build());
        List<ChaosExperiment> list = chaosService.list();
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("recentHistory 记录最近事件, clearHistory 清空")
    void history() {
        when(featureFlagService.isEnabled(any())).thenReturn(false);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .enabled(true)
                .build());
        chaosService.maybeInject("X.y");
        chaosService.maybeInject("X.y");
        assertThat(chaosService.recentHistory()).hasSize(2);
        chaosService.clearHistory();
        assertThat(chaosService.recentHistory()).isEmpty();
    }

    @Test
    @DisplayName("register target=null 抛 IllegalArgumentException")
    void registerInvalid() {
        assertThatThrownBy(() -> chaosService.register(ChaosExperiment.builder().type("LATENCY").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知实验类型被忽略, 返回 INJECTED 但不抛错")
    void unknownTypeSafe() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        chaosService.register(ChaosExperiment.builder()
                .target("X.y")
                .type("UNKNOWN_TYPE")
                .enabled(true)
                .build());
        // 不应抛错
        ChaosOutcome out = chaosService.maybeInject("X.y");
        assertThat(out).isEqualTo(ChaosOutcome.INJECTED);
    }
}
