package com.njydsz.pmis.literule.model;

import com.njydsz.pmis.literule.api.RuleContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelInputRegistry 单元测试（P3-1 规则+模型融合）
 *
 * <p>测试注册中心的注册/注销、聚合查询、超时控制、异常隔离、降级策略、
 * 并发安全性等核心能力，目标覆盖率 100%。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("ModelInputRegistry 单元测试")
class ModelInputRegistryTest {

    private ModelInputRegistry registry;

    @BeforeEach
    void setUp() {
        // 默认降级开启，超时 100ms
        registry = new ModelInputRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 mock provider，返回固定输出
     */
    private ModelInputProvider mockProvider(String modelId, Map<String, Object> output) {
        ModelInputProvider provider = Mockito.mock(ModelInputProvider.class);
        when(provider.getModelId()).thenReturn(modelId);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getModelOutput(any())).thenReturn(output);
        return provider;
    }

    /**
     * 构造默认上下文
     */
    private RuleContext defaultContext() {
        return RuleContext.of(new HashMap<>());
    }

    // ==================== 注册与注销 ====================

    @Nested
    @DisplayName("注册与注销")
    class RegisterTest {

        @Test
        @DisplayName("register null provider - 空操作")
        void shouldNotRegisterNullProvider() {
            registry.register(null);
            assertThat(registry.size()).isEqualTo(0);
            assertThat(registry.hasProviders()).isFalse();
        }

        @Test
        @DisplayName("注册有效 provider - size 增加")
        void shouldRegisterValidProvider() {
            ModelInputProvider provider = mockProvider("model-a", Map.of("score", 0.5));

            registry.register(provider);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.hasProviders()).isTrue();
        }

        @Test
        @DisplayName("注册同 modelId provider - 热更新覆盖")
        void shouldReplaceProviderWithSameModelId() {
            ModelInputProvider p1 = mockProvider("model-a", Map.of("score", 0.5));
            ModelInputProvider p2 = mockProvider("model-a", Map.of("score", 0.9));

            registry.register(p1);
            registry.register(p2);

            assertThat(registry.size()).isEqualTo(1);
            Map<String, Object> outputs = registry.getModelOutputs("model-a", defaultContext());
            assertThat(outputs).containsEntry("score", 0.9);
        }

        @Test
        @DisplayName("注销 provider 实例 - 移除")
        void shouldUnregisterProviderInstance() {
            ModelInputProvider provider = mockProvider("model-a", Map.of("score", 0.5));
            registry.register(provider);

            registry.unregister(provider);

            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("注销 null provider - 空操作")
        void shouldNotUnregisterNullProvider() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));

            registry.unregister((ModelInputProvider) null);

            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("按 modelId 注销 - 移除对应 provider")
        void shouldUnregisterByModelId() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));
            registry.register(mockProvider("model-b", Map.of("score", 0.6)));

            registry.unregister("model-a");

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.getModelOutputs("model-a", defaultContext())).isEmpty();
            assertThat(registry.getModelOutputs("model-b", defaultContext()))
                    .containsEntry("score", 0.6);
        }

        @Test
        @DisplayName("按 null modelId 注销 - 空操作")
        void shouldNotUnregisterWhenModelIdNull() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));

            registry.unregister((String) null);

            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("注销不存在的 provider - 无副作用")
        void shouldNotFailWhenUnregisteringNonExistent() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));

            registry.unregister("non-existent");

            assertThat(registry.size()).isEqualTo(1);
        }
    }

    // ==================== collectAllModelOutputs 聚合 ====================

    @Nested
    @DisplayName("collectAllModelOutputs 聚合")
    class CollectAllTest {

        @Test
        @DisplayName("空注册表 - 返回空 Map")
        void shouldReturnEmptyMapForEmptyRegistry() {
            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("单个 provider - 返回带 model. 前缀的输出")
        void shouldReturnPrefixedKeysForSingleProvider() {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("riskScore", 0.9);
            output.put("fraudProbability", 0.05);
            registry.register(mockProvider("risk-model", output));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("model.riskScore", 0.9);
            assertThat(result).containsEntry("model.fraudProbability", 0.05);
        }

        @Test
        @DisplayName("多个 provider - 聚合所有输出")
        void shouldAggregateOutputsFromMultipleProviders() {
            registry.register(mockProvider("model-a", Map.of("riskScore", 0.9)));
            registry.register(mockProvider("model-b", Map.of("fraudProbability", 0.05)));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("model.riskScore", 0.9);
            assertThat(result).containsEntry("model.fraudProbability", 0.05);
        }

        @Test
        @DisplayName("多 provider 同名字段 - 后注册者覆盖")
        void shouldOverrideSameFieldNameFromLaterProvider() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));
            registry.register(mockProvider("model-b", Map.of("score", 0.9)));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).containsEntry("model.score", 0.9);
        }

        @Test
        @DisplayName("provider 返回 null - 视为无输出，不影响其他 provider")
        void shouldHandleNullOutputFromProvider() {
            ModelInputProvider nullProvider = Mockito.mock(ModelInputProvider.class);
            when(nullProvider.getModelId()).thenReturn("null-model");
            when(nullProvider.isEnabled()).thenReturn(true);
            when(nullProvider.getModelOutput(any())).thenReturn(null);
            registry.register(nullProvider);
            registry.register(mockProvider("model-b", Map.of("score", 0.9)));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).hasSize(1);
            assertThat(result).containsEntry("model.score", 0.9);
        }

        @Test
        @DisplayName("provider 返回空 Map - 视为无输出")
        void shouldHandleEmptyOutputFromProvider() {
            ModelInputProvider emptyProvider = mockProvider("empty-model", Collections.emptyMap());
            registry.register(emptyProvider);
            registry.register(mockProvider("model-b", Map.of("score", 0.9)));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).hasSize(1);
            assertThat(result).containsEntry("model.score", 0.9);
        }
    }

    // ==================== getModelOutputs 定点查询 ====================

    @Nested
    @DisplayName("getModelOutputs 定点查询")
    class GetByModelIdTest {

        @Test
        @DisplayName("按 modelId 查询 - 返回原始输出（无前缀）")
        void shouldReturnRawOutputWithoutPrefix() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.9)));

            Map<String, Object> result = registry.getModelOutputs("risk-model", defaultContext());

            assertThat(result).containsEntry("riskScore", 0.9);
            assertThat(result).doesNotContainKey("model.riskScore");
        }

        @Test
        @DisplayName("查询不存在的 modelId - 返回空 Map")
        void shouldReturnEmptyForNonExistentModelId() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));

            Map<String, Object> result = registry.getModelOutputs("non-existent", defaultContext());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("查询 null modelId - 返回空 Map")
        void shouldReturnEmptyForNullModelId() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));

            Map<String, Object> result = registry.getModelOutputs(null, defaultContext());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("查询已禁用的 provider - 返回空 Map")
        void shouldReturnEmptyForDisabledProvider() {
            ModelInputProvider disabled = Mockito.mock(ModelInputProvider.class);
            when(disabled.getModelId()).thenReturn("disabled-model");
            when(disabled.isEnabled()).thenReturn(false);
            registry.register(disabled);

            Map<String, Object> result = registry.getModelOutputs("disabled-model", defaultContext());

            assertThat(result).isEmpty();
            verify(disabled, never()).getModelOutput(any());
        }
    }

    // ==================== isEnabled 过滤 ====================

    @Nested
    @DisplayName("isEnabled 过滤")
    class EnabledFilterTest {

        @Test
        @DisplayName("isEnabled=false 的 provider - collectAllModelOutputs 不调用")
        void shouldSkipDisabledProviderInCollectAll() {
            ModelInputProvider disabled = Mockito.mock(ModelInputProvider.class);
            when(disabled.getModelId()).thenReturn("disabled-model");
            when(disabled.isEnabled()).thenReturn(false);
            registry.register(disabled);
            registry.register(mockProvider("enabled-model", Map.of("score", 0.9)));

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).hasSize(1);
            assertThat(result).containsEntry("model.score", 0.9);
            verify(disabled, never()).getModelOutput(any());
        }

        @Test
        @DisplayName("所有 provider 都禁用 - 返回空 Map")
        void shouldReturnEmptyWhenAllDisabled() {
            ModelInputProvider d1 = Mockito.mock(ModelInputProvider.class);
            when(d1.getModelId()).thenReturn("d1");
            when(d1.isEnabled()).thenReturn(false);
            ModelInputProvider d2 = Mockito.mock(ModelInputProvider.class);
            when(d2.getModelId()).thenReturn("d2");
            when(d2.isEnabled()).thenReturn(false);
            registry.register(d1);
            registry.register(d2);

            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());

            assertThat(result).isEmpty();
            verify(d1, never()).getModelOutput(any());
            verify(d2, never()).getModelOutput(any());
        }
    }

    // ==================== 异常隔离 ====================

    @Nested
    @DisplayName("异常隔离")
    class ExceptionIsolationTest {

        @Test
        @DisplayName("provider 抛异常（fallbackOnError=true）- 不影响其他 provider")
        void shouldIsolateProviderExceptionWhenFallbackEnabled() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));
            registry.register(badProvider);
            registry.register(mockProvider("good-model", Map.of("score", 0.9)));

            ModelInputRegistry lenientRegistry = new ModelInputRegistry(100L, true);
            try {
                lenientRegistry.register(badProvider);
                lenientRegistry.register(mockProvider("good-model", Map.of("score", 0.9)));

                Map<String, Object> result = lenientRegistry.collectAllModelOutputs(defaultContext());

                // bad provider 的输出缺失，good provider 正常返回
                assertThat(result).hasSize(1);
                assertThat(result).containsEntry("model.score", 0.9);
            } finally {
                lenientRegistry.destroy();
            }
        }

        @Test
        @DisplayName("provider 抛异常（fallbackOnError=false）- 抛出 ModelInvocationException")
        void shouldThrowWhenFallbackDisabled() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));

            ModelInputRegistry strictRegistry = new ModelInputRegistry(100L, false);
            try {
                strictRegistry.register(badProvider);

                assertThatThrownBy(() -> strictRegistry.collectAllModelOutputs(defaultContext()))
                        .isInstanceOf(ModelInvocationException.class)
                        .hasMessageContaining("bad-model");
            } finally {
                strictRegistry.destroy();
            }
        }

        @Test
        @DisplayName("provider 抛异常（fallbackOnError=false）- 中断后续 provider 调用")
        void shouldStopCallingSubsequentProvidersWhenFallbackDisabled() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));
            ModelInputProvider goodProvider = mockProvider("good-model", Map.of("score", 0.9));

            ModelInputRegistry strictRegistry = new ModelInputRegistry(100L, false);
            try {
                strictRegistry.register(badProvider);
                strictRegistry.register(goodProvider);

                assertThatThrownBy(() -> strictRegistry.collectAllModelOutputs(defaultContext()))
                        .isInstanceOf(ModelInvocationException.class);

                // good provider 不应被调用（中断在 bad provider）
                verify(goodProvider, never()).getModelOutput(any());
            } finally {
                strictRegistry.destroy();
            }
        }
    }

    // ==================== 超时控制 ====================

    @Nested
    @DisplayName("超时控制")
    class TimeoutTest {

        @Test
        @DisplayName("provider 慢于 timeoutMs - 超时返回空 Map（fallbackOnError=true）")
        void shouldReturnEmptyOnTimeoutWhenFallbackEnabled() throws InterruptedException {
            ModelInputProvider slowProvider = Mockito.mock(ModelInputProvider.class);
            when(slowProvider.getModelId()).thenReturn("slow-model");
            when(slowProvider.isEnabled()).thenReturn(true);
            // 模拟慢调用：sleep 500ms
            when(slowProvider.getModelOutput(any())).thenAnswer(inv -> {
                Thread.sleep(500);
                return Map.of("score", 0.9);
            });

            // 超时 50ms
            ModelInputRegistry timeoutRegistry = new ModelInputRegistry(50L, true);
            try {
                timeoutRegistry.register(slowProvider);

                long start = System.currentTimeMillis();
                Map<String, Object> result = timeoutRegistry.collectAllModelOutputs(defaultContext());
                long elapsed = System.currentTimeMillis() - start;

                assertThat(result).isEmpty();
                // 应在合理时间内返回（不超过 1 秒，避免阻塞测试）
                assertThat(elapsed).isLessThan(1000L);
            } finally {
                timeoutRegistry.destroy();
            }
        }

        @Test
        @DisplayName("provider 慢于 timeoutMs - 抛 ModelInvocationException（fallbackOnError=false）")
        void shouldThrowOnTimeoutWhenFallbackDisabled() {
            ModelInputProvider slowProvider = Mockito.mock(ModelInputProvider.class);
            when(slowProvider.getModelId()).thenReturn("slow-model");
            when(slowProvider.isEnabled()).thenReturn(true);
            when(slowProvider.getModelOutput(any())).thenAnswer(inv -> {
                Thread.sleep(500);
                return Map.of("score", 0.9);
            });

            ModelInputRegistry timeoutRegistry = new ModelInputRegistry(50L, false);
            try {
                timeoutRegistry.register(slowProvider);

                assertThatThrownBy(() -> timeoutRegistry.collectAllModelOutputs(defaultContext()))
                        .isInstanceOf(ModelInvocationException.class)
                        .hasMessageContaining("超时");
            } finally {
                timeoutRegistry.destroy();
            }
        }

        @Test
        @DisplayName("timeoutMs <= 0 - 不限制超时")
        void shouldNotTimeoutWhenTimeoutMsNonPositive() throws Exception {
            ModelInputProvider provider = mockProvider("model-a", Map.of("score", 0.9));

            // timeoutMs = 0 表示不限制
            ModelInputRegistry noTimeoutRegistry = new ModelInputRegistry(0L, true);
            try {
                noTimeoutRegistry.register(provider);

                Map<String, Object> result = noTimeoutRegistry.collectAllModelOutputs(defaultContext());

                assertThat(result).containsEntry("model.score", 0.9);
            } finally {
                noTimeoutRegistry.destroy();
            }
        }

        @Test
        @DisplayName("超时的 provider 不影响其他 provider（fallbackOnError=true）")
        void shouldNotAffectOtherProvidersOnTimeout() throws InterruptedException {
            ModelInputProvider slowProvider = Mockito.mock(ModelInputProvider.class);
            when(slowProvider.getModelId()).thenReturn("slow-model");
            when(slowProvider.isEnabled()).thenReturn(true);
            when(slowProvider.getModelOutput(any())).thenAnswer(inv -> {
                Thread.sleep(500);
                return Map.of("slowScore", 0.1);
            });
            ModelInputProvider fastProvider = mockProvider("fast-model", Map.of("fastScore", 0.9));

            ModelInputRegistry timeoutRegistry = new ModelInputRegistry(50L, true);
            try {
                timeoutRegistry.register(slowProvider);
                timeoutRegistry.register(fastProvider);

                Map<String, Object> result = timeoutRegistry.collectAllModelOutputs(defaultContext());

                // slow 超时返回空，fast 正常返回
                assertThat(result).hasSize(1);
                assertThat(result).containsEntry("model.fastScore", 0.9);
                assertThat(result).doesNotContainKey("model.slowScore");
            } finally {
                timeoutRegistry.destroy();
            }
        }
    }

    // ==================== 并发安全性 ====================

    @Nested
    @DisplayName("并发安全性")
    class ConcurrencyTest {

        @Test
        @DisplayName("多线程并发 collectAllModelOutputs - 安全且结果一致")
        void shouldHandleConcurrentCollectAll() throws InterruptedException {
            registry.register(mockProvider("model-a", Map.of("score", 0.9)));
            registry.register(mockProvider("model-b", Map.of("level", 5)));

            int threadCount = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            try {
                for (int i = 0; i < threadCount; i++) {
                    pool.submit(() -> {
                        try {
                            Map<String, Object> result = registry.collectAllModelOutputs(defaultContext());
                            if (result.size() == 2
                                    && result.containsKey("model.score")
                                    && result.containsKey("model.level")) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("并发注册与查询 - 无异常")
        void shouldHandleConcurrentRegisterAndQuery() throws InterruptedException {
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount * 2);

            try {
                // 一半线程注册 provider
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    pool.submit(() -> {
                        try {
                            registry.register(mockProvider("model-" + idx, Map.of("score", idx)));
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                // 一半线程查询
                for (int i = 0; i < threadCount; i++) {
                    pool.submit(() -> {
                        try {
                            registry.collectAllModelOutputs(defaultContext());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            // 最终所有 provider 都应注册成功
            assertThat(registry.size()).isEqualTo(threadCount);
        }
    }

    // ==================== destroy 资源释放 ====================

    @Nested
    @DisplayName("资源释放")
    class DestroyTest {

        @Test
        @DisplayName("destroy - 关闭内部线程池")
        void shouldShutdownExecutorOnDestroy() {
            ModelInputRegistry r = new ModelInputRegistry();
            r.register(mockProvider("model-a", Map.of("score", 0.9)));

            r.destroy();

            // 多次调用 destroy 不抛异常
            r.destroy();
        }

        @Test
        @DisplayName("destroy - 外部线程池不主动关闭")
        void shouldNotShutdownExternalExecutor() {
            ExecutorService external = Executors.newCachedThreadPool();
            try {
                ModelInputRegistry r = new ModelInputRegistry(100L, true, external);
                r.register(mockProvider("model-a", Map.of("score", 0.9)));

                r.destroy();

                // 外部线程池不应被关闭
                assertThat(external.isShutdown()).isFalse();
            } finally {
                external.shutdownNow();
            }
        }
    }

    // ==================== Getter 配置 ====================

    @Nested
    @DisplayName("Getter 配置")
    class GetterTest {

        @Test
        @DisplayName("getTimeoutMs - 返回配置的超时")
        void shouldReturnTimeoutMs() {
            ModelInputRegistry r = new ModelInputRegistry(200L, true);
            try {
                assertThat(r.getTimeoutMs()).isEqualTo(200L);
            } finally {
                r.destroy();
            }
        }

        @Test
        @DisplayName("isFallbackOnError - 返回降级配置")
        void shouldReturnFallbackOnError() {
            ModelInputRegistry r1 = new ModelInputRegistry(100L, true);
            ModelInputRegistry r2 = new ModelInputRegistry(100L, false);
            try {
                assertThat(r1.isFallbackOnError()).isTrue();
                assertThat(r2.isFallbackOnError()).isFalse();
            } finally {
                r1.destroy();
                r2.destroy();
            }
        }

        @Test
        @DisplayName("默认构造 - 超时 100ms，降级开启")
        void shouldUseDefaultsForNoArgConstructor() {
            ModelInputRegistry r = new ModelInputRegistry();
            try {
                assertThat(r.getTimeoutMs()).isEqualTo(ModelInputRegistry.DEFAULT_TIMEOUT_MS);
                assertThat(r.isFallbackOnError()).isTrue();
            } finally {
                r.destroy();
            }
        }
    }
}
