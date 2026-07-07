package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AbstractHttpLlmProvider 抽象 HTTP LLM Provider 单元测试
 *
 * <p>实现 TestableHttpLlmProvider 继承 AbstractHttpLlmProvider，构造时设置
 * timeoutMillis/maxRetries/fallbackToMockOnError，chat() 调用 executeWithGuard()。
 *
 * <p>覆盖：成功调用返回内容 / 超时后重试 / 重试 N 次仍失败时降级到 mock /
 * fallbackToMockOnError=false 时抛 RuntimeException / MDC 中 traceId/provider/providerTraceId 正确设置与恢复。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AbstractHttpLlmProvider 抽象 HTTP LLM Provider 测试")
class AbstractHttpLlmProviderTest {

    // ==================== Testable 实现 ====================

    /**
     * 可测试的 HTTP LLM Provider 实现。
     * 通过注入 Callable 控制每次调用的行为（成功/失败/超时）。
     */
    static class TestableHttpLlmProvider extends AbstractHttpLlmProvider {
        private final Callable<String> callable;

        TestableHttpLlmProvider(Callable<String> callable, long timeoutMillis,
                                int maxRetries, boolean fallbackToMockOnError) {
            this.callable = callable;
            this.timeoutMillis = timeoutMillis;
            this.maxRetries = maxRetries;
            this.fallbackToMockOnError = fallbackToMockOnError;
        }

        @Override
        public String name() {
            return "testable-http";
        }

        @Override
        public String chat(String systemPrompt, String userPrompt, AgentContext context) {
            return executeWithGuard(callable, context);
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造带 traceId / providerTraceId 的 AgentContext */
    private AgentContext ctxWithTrace(String traceId, String providerTraceId) {
        AgentContext ctx = new AgentContext();
        ctx.setTraceId(traceId);
        ctx.setProviderTraceId(providerTraceId);
        return ctx;
    }

    // ==================== 成功调用测试 ====================

    @Nested
    @DisplayName("成功调用测试")
    class SuccessTest {

        @Test
        @DisplayName("成功调用返回内容")
        void shouldReturnContentOnSuccess() {
            Callable<String> call = () -> "hello world";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("context=null 时使用默认 traceId - 不抛 NPE")
        void shouldHandleNullContext() {
            Callable<String> call = () -> "ok";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            String result = provider.chat("", "", null);

            assertThat(result).isEqualTo("ok");
        }
    }

    // ==================== 超时与重试测试 ====================

    @Nested
    @DisplayName("超时与重试测试")
    class TimeoutRetryTest {

        @Test
        @DisplayName("超时后重试 - 最终成功")
        void shouldRetryAfterTimeout() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                if (count.getAndIncrement() == 0) {
                    // 第一次调用：sleep 200ms 触发超时（timeout=50ms）
                    Thread.sleep(200);
                    return "should-not-reach";
                }
                // 第二次调用：立即返回
                return "success-after-retry";
            };
            // maxRetries=1 表示最多重试 1 次（共 2 次尝试）
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    50, 1, true);

            String result = provider.chat("", "", new AgentContext());

            assertThat(result).isEqualTo("success-after-retry");
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("异常后重试 - 最终成功")
        void shouldRetryAfterException() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                if (count.getAndIncrement() == 0) {
                    throw new RuntimeException("first attempt fails");
                }
                return "success-on-second";
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 1, true);

            String result = provider.chat("", "", new AgentContext());

            assertThat(result).isEqualTo("success-on-second");
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("重试 N 次仍失败时降级到 mock（fallbackToMockOnError=true）")
        void shouldFallbackToMockAfterRetries() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                count.incrementAndGet();
                throw new RuntimeException("always fails");
            };
            // maxRetries=1 表示 2 次尝试都失败
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 1, true);

            String result = provider.chat("", "", new AgentContext());

            // 降级到 MockLlmProvider 的标准输出
            assertThat(result).contains("NORMAL");
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("fallbackToMockOnError=false 时抛 RuntimeException")
        void shouldThrowWhenFallbackDisabled() {
            Callable<String> call = () -> {
                throw new RuntimeException("intentional failure");
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, false);

            assertThatThrownBy(() -> provider.chat("", "", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("testable-http")
                    .hasMessageContaining("failed");
        }

        @Test
        @DisplayName("maxRetries=0 - 仅尝试 1 次后降级")
        void shouldFallbackAfterSingleAttemptWhenNoRetries() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                count.incrementAndGet();
                throw new RuntimeException("single attempt");
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            String result = provider.chat("", "", new AgentContext());

            assertThat(result).contains("NORMAL");
            assertThat(count.get()).isEqualTo(1);
        }
    }

    // ==================== MDC 上下文测试 ====================

    @Nested
    @DisplayName("MDC 上下文测试")
    class MdcTest {

        @Test
        @DisplayName("MDC 中 traceId/provider/providerTraceId 正确设置")
        void shouldSetMdcDuringCall() {
            AtomicReference<String> traceIdRef = new AtomicReference<>();
            AtomicReference<String> providerRef = new AtomicReference<>();
            AtomicReference<String> providerTraceIdRef = new AtomicReference<>();

            Callable<String> call = () -> {
                // 在 callable 执行期间捕获 MDC（注意：callable 在子线程执行）
                traceIdRef.set(MDC.get("traceId"));
                providerRef.set(MDC.get("provider"));
                providerTraceIdRef.set(MDC.get("providerTraceId"));
                return "ok";
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            AgentContext ctx = ctxWithTrace("test-trace-123", "provider-trace-456");
            provider.chat("", "", ctx);

            // 子线程 MDC 应被正确设置（通过 mdcSnapshot 复制）
            assertThat(traceIdRef.get()).isEqualTo("test-trace-123");
            assertThat(providerRef.get()).isEqualTo("testable-http");
            assertThat(providerTraceIdRef.get()).isEqualTo("provider-trace-456");
        }

        @Test
        @DisplayName("MDC 在调用后正确恢复（P2-1：全部 key 恢复旧值，支持嵌套调用）")
        void shouldRestoreMdcAfterCall() {
            Callable<String> call = () -> "ok";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            // 主线程预先设置 traceId + provider（模拟嵌套调用场景）
            MDC.put("traceId", "previous-trace");
            MDC.put("provider", "previous-provider");

            AgentContext ctx = ctxWithTrace("new-trace", "new-provider-trace");
            provider.chat("", "", ctx);

            // P2-1：调用结束后，主线程 MDC 应恢复到调用前的状态（全部 key 恢复旧值）
            assertThat(MDC.get("traceId")).isEqualTo("previous-trace");
            // P2-1：provider 恢复为旧值（而非 remove），保证嵌套调用安全
            assertThat(MDC.get("provider")).isEqualTo("previous-provider");
            // providerTraceId 调用前未设置，恢复时 remove
            assertThat(MDC.get("providerTraceId")).isNull();

            MDC.clear();
        }

        @Test
        @DisplayName("无 previousTraceId 时调用后 MDC.traceId 被 remove")
        void shouldRemoveTraceIdWhenNoPrevious() {
            Callable<String> call = () -> "ok";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            MDC.clear();
            AgentContext ctx = ctxWithTrace("temp-trace", null);
            provider.chat("", "", ctx);

            // 无 previousTraceId，调用后 traceId 被 remove
            assertThat(MDC.get("traceId")).isNull();
            assertThat(MDC.get("provider")).isNull();
            assertThat(MDC.get("providerTraceId")).isNull();
        }

        @Test
        @DisplayName("context.traceId=null 时使用默认 traceId（agent-开头）")
        void shouldUseDefaultTraceIdWhenContextTraceIdNull() {
            AtomicReference<String> traceIdRef = new AtomicReference<>();
            Callable<String> call = () -> {
                traceIdRef.set(MDC.get("traceId"));
                return "ok";
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            AgentContext ctx = new AgentContext();
            // traceId=null
            provider.chat("", "", ctx);

            // 默认 traceId 应以 "agent-" 开头
            assertThat(traceIdRef.get()).startsWith("agent-");
        }

        @Test
        @DisplayName("context.providerTraceId=null 时 MDC.providerTraceId 为空字符串")
        void shouldUseEmptyStringWhenProviderTraceIdNull() {
            AtomicReference<String> providerTraceIdRef = new AtomicReference<>();
            Callable<String> call = () -> {
                providerTraceIdRef.set(MDC.get("providerTraceId"));
                return "ok";
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            AgentContext ctx = new AgentContext();
            // providerTraceId=null
            provider.chat("", "", ctx);

            assertThat(providerTraceIdRef.get()).isEqualTo("");
        }
    }

    // ==================== P0-4 线程池复用测试 ====================

    @Nested
    @DisplayName("P0-4: 线程池复用与生命周期测试")
    class ExecutorPoolTest {

        @Test
        @DisplayName("多次调用复用同一 sharedExecutor 实例（不每次创建新线程池）")
        void shouldReuseExecutorAcrossCalls() throws Exception {
            Callable<String> call = () -> "ok";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);
            try {
                java.lang.reflect.Field field =
                        AbstractHttpLlmProvider.class.getDeclaredField("sharedExecutor");
                field.setAccessible(true);
                Object executorBefore = field.get(provider);

                // 多次调用 chat()
                for (int i = 0; i < 3; i++) {
                    assertThat(provider.chat("", "", new AgentContext())).isEqualTo("ok");
                }

                // 验证 sharedExecutor 引用未变（即未每次创建新线程池）
                Object executorAfter = field.get(provider);
                assertThat(executorAfter).isSameAs(executorBefore);
            } finally {
                provider.destroy();
            }
        }

        @Test
        @DisplayName("destroy() 后再次调用应抛异常（线程池已关闭）")
        void shouldRejectAfterDestroy() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                count.incrementAndGet();
                return "ok";
            };
            // fallback=false, maxRetries=0：销毁后再调用直接抛 RuntimeException
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, false);

            // 销毁前正常调用成功
            assertThat(provider.chat("", "", new AgentContext())).isEqualTo("ok");
            assertThat(count.get()).isEqualTo(1);

            // 销毁线程池
            provider.destroy();

            // 再次调用应抛 RuntimeException（线程池已关闭，submit 抛 RejectedExecutionException
            // 被 executeWithGuard 捕获后包装为 RuntimeException）
            assertThatThrownBy(() -> provider.chat("", "", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("testable-http")
                    .hasMessageContaining("failed");
        }

        @Test
        @DisplayName("多次 destroy() 不抛异常（幂等）")
        void shouldBeIdempotentDestroy() {
            Callable<String> call = () -> "ok";
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);

            provider.destroy();
            // 再次 destroy 不抛异常
            provider.destroy();
            provider.destroy();
        }

        @Test
        @DisplayName("共享线程池下多次调用都能成功执行")
        void shouldExecuteAllTasksWithSharedExecutor() {
            AtomicInteger count = new AtomicInteger(0);
            Callable<String> call = () -> {
                count.incrementAndGet();
                return "ok";
            };
            TestableHttpLlmProvider provider = new TestableHttpLlmProvider(call,
                    5000, 0, true);
            try {
                for (int i = 0; i < 5; i++) {
                    assertThat(provider.chat("", "", new AgentContext())).isEqualTo("ok");
                }
                assertThat(count.get()).isEqualTo(5);
            } finally {
                provider.destroy();
            }
        }
    }
}
