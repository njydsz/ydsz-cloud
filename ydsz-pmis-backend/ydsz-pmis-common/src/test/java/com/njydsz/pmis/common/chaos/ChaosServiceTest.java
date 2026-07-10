package com.njydsz.pmis.common.chaos;

import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ChaosService 混沌工程服务单元测试
 *
 * <p>覆盖 register / unregister / list / maybeInject / recentHistory / clearHistory,
 * 包括各故障类型注入、FeatureFlag 二次保护、概率判定等场景.
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChaosService 混沌工程服务测试")
class ChaosServiceTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private ChaosService chaosService;

    // ==================== register ====================

    @Test
    @DisplayName("正常场景：register 注册有效实验")
    void register_有效实验() {
        ChaosExperiment exp = ChaosExperiment.builder()
                .type(ChaosExperiment.TYPE_LATENCY)
                .target("ServiceA.method")
                .createdBy("tester")
                .enabled(true)
                .build();

        chaosService.register(exp);

        assertEquals(1, chaosService.list().size());
    }

    @Test
    @DisplayName("异常场景：register 传入 null 抛 IllegalArgumentException")
    void register_null_抛异常() {
        assertThrows(IllegalArgumentException.class, () -> chaosService.register(null));
    }

    @Test
    @DisplayName("异常场景：register target 为 null 抛 IllegalArgumentException")
    void register_targetNull_抛异常() {
        ChaosExperiment exp = ChaosExperiment.builder()
                .type(ChaosExperiment.TYPE_LATENCY)
                .target(null)
                .enabled(true)
                .build();

        assertThrows(IllegalArgumentException.class, () -> chaosService.register(exp));
    }

    // ==================== unregister ====================

    @Test
    @DisplayName("正常场景：unregister 注销已注册实验")
    void unregister_已注册() {
        ChaosExperiment exp = ChaosExperiment.builder()
                .target("ServiceB.method")
                .type(ChaosExperiment.TYPE_LATENCY)
                .enabled(true)
                .build();
        chaosService.register(exp);

        chaosService.unregister("ServiceB.method");

        assertEquals(0, chaosService.list().size());
    }

    @Test
    @DisplayName("边界场景：unregister 传入 null 不抛异常")
    void unregister_null_无异常() {
        chaosService.unregister(null);
        assertEquals(0, chaosService.list().size());
    }

    @Test
    @DisplayName("边界场景：unregister 不存在的 target 不抛异常")
    void unregister_不存在() {
        chaosService.unregister("non-existent");
        assertEquals(0, chaosService.list().size());
    }

    // ==================== list ====================

    @Test
    @DisplayName("正常场景：list 返回已注册实验的不可变快照")
    void list_返回快照() {
        chaosService.register(ChaosExperiment.builder()
                .target("t1").type(ChaosExperiment.TYPE_LATENCY).enabled(true).build());
        chaosService.register(ChaosExperiment.builder()
                .target("t2").type(ChaosExperiment.TYPE_EXCEPTION).enabled(true).build());

        List<ChaosExperiment> list = chaosService.list();

        assertEquals(2, list.size());
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }

    // ==================== maybeInject ====================

    @Test
    @DisplayName("正常场景：target 未注册返回 NOT_TRIGGERED")
    void maybeInject_未注册_NOT_TRIGGERED() {
        ChaosOutcome result = chaosService.maybeInject("not-registered");

        assertEquals(ChaosOutcome.NOT_TRIGGERED, result);
    }

    @Test
    @DisplayName("正常场景：实验已禁用返回 NOT_TRIGGERED")
    void maybeInject_实验禁用_NOT_TRIGGERED() {
        chaosService.register(ChaosExperiment.builder()
                .target("disabled-target")
                .type(ChaosExperiment.TYPE_LATENCY)
                .enabled(false)
                .build());

        ChaosOutcome result = chaosService.maybeInject("disabled-target");

        assertEquals(ChaosOutcome.NOT_TRIGGERED, result);
    }

    @Test
    @DisplayName("正常场景：FeatureFlag 关闭返回 BLOCKED_BY_FLAG 并记录历史")
    void maybeInject_flag关闭_BLOCKED_BY_FLAG() {
        chaosService.register(ChaosExperiment.builder()
                .target("flag-blocked")
                .type(ChaosExperiment.TYPE_LATENCY)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(false);

        ChaosOutcome result = chaosService.maybeInject("flag-blocked");

        assertEquals(ChaosOutcome.BLOCKED_BY_FLAG, result);
        assertEquals(1, chaosService.recentHistory().size());
    }

    @Test
    @DisplayName("正常场景：flag 开启且 errorRate=null TYPE_LATENCY 返回 INJECTED")
    void maybeInject_latency无概率_INJECTED() {
        chaosService.register(ChaosExperiment.builder()
                .target("latency-test")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        ChaosOutcome result = chaosService.maybeInject("latency-test");

        assertEquals(ChaosOutcome.INJECTED, result);
        assertEquals(1, chaosService.recentHistory().size());
    }

    @Test
    @DisplayName("正常场景：errorRate=1.0 跳过概率判定直接注入")
    void maybeInject_errorRate1_0_直接注入() {
        chaosService.register(ChaosExperiment.builder()
                .target("error-rate-1")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .errorRate(1.0)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        ChaosOutcome result = chaosService.maybeInject("error-rate-1");

        assertEquals(ChaosOutcome.INJECTED, result);
    }

    @Test
    @DisplayName("正常场景：概率未命中返回 SKIPPED_PROBABILITY")
    void maybeInject_概率未命中_SKIPPED() {
        chaosService.register(ChaosExperiment.builder()
                .target("prob-skip")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .errorRate(0.3)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        try (MockedStatic<ThreadLocalRandom> mocked = Mockito.mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom mockRandom = Mockito.mock(ThreadLocalRandom.class);
            mocked.when(ThreadLocalRandom::current).thenReturn(mockRandom);
            when(mockRandom.nextDouble()).thenReturn(0.5);

            ChaosOutcome result = chaosService.maybeInject("prob-skip");

            assertEquals(ChaosOutcome.SKIPPED_PROBABILITY, result);
        }
    }

    @Test
    @DisplayName("正常场景：概率命中返回 INJECTED")
    void maybeInject_概率命中_INJECTED() {
        chaosService.register(ChaosExperiment.builder()
                .target("prob-hit")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .errorRate(0.5)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        try (MockedStatic<ThreadLocalRandom> mocked = Mockito.mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom mockRandom = Mockito.mock(ThreadLocalRandom.class);
            mocked.when(ThreadLocalRandom::current).thenReturn(mockRandom);
            when(mockRandom.nextDouble()).thenReturn(0.3);

            ChaosOutcome result = chaosService.maybeInject("prob-hit");

            assertEquals(ChaosOutcome.INJECTED, result);
        }
    }

    @Test
    @DisplayName("异常场景：TYPE_EXCEPTION 默认抛出 RuntimeException")
    void maybeInject_exception_默认异常() {
        chaosService.register(ChaosExperiment.builder()
                .target("exception-default")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .description("test exception")
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> chaosService.maybeInject("exception-default"));

        assertTrue(ex.getMessage().contains("Chaos injected"));
    }

    @Test
    @DisplayName("异常场景：TYPE_EXCEPTION 自定义异常类")
    void maybeInject_exception_自定义异常() {
        chaosService.register(ChaosExperiment.builder()
                .target("exception-custom")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .exceptionClass("java.lang.IllegalStateException")
                .description("custom")
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> chaosService.maybeInject("exception-custom"));
    }

    @Test
    @DisplayName("异常场景：TYPE_EXCEPTION 不存在的异常类回退为 RuntimeException")
    void maybeInject_exception_类不存在_回退() {
        chaosService.register(ChaosExperiment.builder()
                .target("exception-missing")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .exceptionClass("com.nonexistent.BadException")
                .description("missing")
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> chaosService.maybeInject("exception-missing"));

        assertTrue(ex.getMessage().contains("fallback"));
    }

    @Test
    @DisplayName("异常场景：TYPE_NETWORK_PARTITION 抛出 RuntimeException")
    void maybeInject_networkPartition_抛异常() {
        chaosService.register(ChaosExperiment.builder()
                .target("network-partition")
                .type(ChaosExperiment.TYPE_NETWORK_PARTITION)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> chaosService.maybeInject("network-partition"));

        assertTrue(ex.getMessage().contains("network partition"));
    }

    @Test
    @DisplayName("异常场景：TYPE_RESOURCE_EXHAUSTION 抛出 OutOfMemoryError")
    void maybeInject_resourceExhaustion_抛OOM() {
        chaosService.register(ChaosExperiment.builder()
                .target("oom-test")
                .type(ChaosExperiment.TYPE_RESOURCE_EXHAUSTION)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        assertThrows(OutOfMemoryError.class,
                () -> chaosService.maybeInject("oom-test"));
    }

    @Test
    @DisplayName("异常场景：TYPE_ERROR_RATE 抛出 RuntimeException")
    void maybeInject_errorRate_抛异常() {
        chaosService.register(ChaosExperiment.builder()
                .target("error-rate-type")
                .type(ChaosExperiment.TYPE_ERROR_RATE)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> chaosService.maybeInject("error-rate-type"));
    }

    // ==================== recentHistory / clearHistory ====================

    @Test
    @DisplayName("正常场景：recentHistory 返回不可变快照")
    void recentHistory_返回快照() {
        chaosService.register(ChaosExperiment.builder()
                .target("history-test")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);

        chaosService.maybeInject("history-test");

        List<ChaosService.ChaosEvent> history = chaosService.recentHistory();
        assertEquals(1, history.size());
        assertEquals(ChaosOutcome.INJECTED, history.get(0).getOutcome());
    }

    @Test
    @DisplayName("正常场景：clearHistory 清空历史")
    void clearHistory_清空() {
        chaosService.register(ChaosExperiment.builder()
                .target("clear-test")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(1L)
                .enabled(true)
                .build());
        when(featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)).thenReturn(true);
        chaosService.maybeInject("clear-test");
        assertEquals(1, chaosService.recentHistory().size());

        chaosService.clearHistory();

        assertEquals(0, chaosService.recentHistory().size());
    }

    @Test
    @DisplayName("边界场景：初始状态 recentHistory 为空")
    void recentHistory_初始为空() {
        assertEquals(0, chaosService.recentHistory().size());
    }
}
