package com.njydsz.pmis.agent.engine.trace;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoOpAgentTracer 空操作实现单元测试（P2-3 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>noOp() 返回单例（多次调用返回同一实例）</li>
 *   <li>startAgent 返回非 null TraceContext（避免业务层 NPE）</li>
 *   <li>span/error/endAgent 为空操作（不抛异常）</li>
 *   <li>startAgent 透传 ctx 字段到 TraceContext</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@DisplayName("NoOpAgentTracer 空操作 Tracer")
class NoOpAgentTracerTest {

    // ==================== 单例测试 ====================

    @Nested
    @DisplayName("单例行为")
    class SingletonTest {

        @Test
        @DisplayName("noOp() 多次调用返回同一实例")
        void shouldReturnSameInstance() {
            AgentTracer t1 = AgentTracer.noOp();
            AgentTracer t2 = AgentTracer.noOp();

            assertThat(t1).isSameAs(t2);
            assertThat(t1).isInstanceOf(NoOpAgentTracer.class);
        }

        @Test
        @DisplayName("NoOpAgentTracer.INSTANCE 与 noOp() 返回相同实例")
        void shouldBeSameAsInstanceField() {
            AgentTracer fromNoOp = AgentTracer.noOp();

            assertThat(fromNoOp).isSameAs(NoOpAgentTracer.INSTANCE);
        }
    }

    // ==================== startAgent 测试 ====================

    @Nested
    @DisplayName("startAgent 返回可用 TraceContext")
    class StartAgentTest {

        @Test
        @DisplayName("返回非 null TraceContext，避免业务层 NPE")
        void shouldReturnNonNullTraceContext() {
            AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>());

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx).isNotNull();
        }

        @Test
        @DisplayName("透传 ctx.traceId 到 TraceContext")
        void shouldPassThroughTraceId() {
            AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>(), "trace-noop-001", "provider-001");

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx.getTraceId()).isEqualTo("trace-noop-001");
        }

        @Test
        @DisplayName("透传 ctx.bizType/bizId/bizRef/providerTraceId")
        void shouldPassThroughContextFields() {
            AgentContext ctx = new AgentContext("OPPORTUNITY", "OP001", "OP-REF",
                    "u1", "u", "MANUAL", new HashMap<>(), "trace-1", "provider-1");

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx.getBizType()).isEqualTo("OPPORTUNITY");
            assertThat(traceCtx.getBizId()).isEqualTo("OP001");
            assertThat(traceCtx.getBizRef()).isEqualTo("OP-REF");
            assertThat(traceCtx.getProviderTraceId()).isEqualTo("provider-1");
            // agentType 默认使用 bizType
            assertThat(traceCtx.getAgentType()).isEqualTo("OPPORTUNITY");
        }

        @Test
        @DisplayName("rootSpanId 返回 'noop' 占位符")
        void shouldReturnNoopRootSpanId() {
            AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>());

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx.getRootSpanId()).isEqualTo("noop");
        }

        @Test
        @DisplayName("tenantId 默认为 '1'")
        void shouldReturnDefaultTenantId() {
            AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>());

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx.getTenantId()).isEqualTo("1");
        }

        @Test
        @DisplayName("startMs > 0 可用于后续耗时计算")
        void shouldReturnValidStartMs() {
            AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>());

            TraceContext traceCtx = AgentTracer.noOp().startAgent(ctx);

            assertThat(traceCtx.getStartMs()).isGreaterThan(0);
            assertThat(traceCtx.getStepStartMs()).isGreaterThan(0);
        }
    }

    // ==================== 空操作测试 ====================

    @Nested
    @DisplayName("空操作方法不抛异常")
    class NoOpMethodTest {

        @Test
        @DisplayName("span() 空操作不抛异常")
        void shouldNoOpSpan() {
            AgentTracer tracer = AgentTracer.noOp();
            TraceContext traceCtx = tracer.startAgent(new AgentContext(
                    "PROJECT", "B001", "REF", "u1", "u", "MANUAL", new HashMap<>()));

            // 不抛异常即可
            tracer.span(traceCtx, AgentSpanName.LLM_THOUGHT, 1, "input", "output");
            tracer.span(null, AgentSpanName.LLM_THOUGHT, 1, "input", "output");
            tracer.span(traceCtx, null, 0, null, null);
        }

        @Test
        @DisplayName("error() 空操作不抛异常")
        void shouldNoOpError() {
            AgentTracer tracer = AgentTracer.noOp();
            TraceContext traceCtx = tracer.startAgent(new AgentContext(
                    "PROJECT", "B001", "REF", "u1", "u", "MANUAL", new HashMap<>()));

            // 不抛异常即可
            tracer.error(traceCtx, new RuntimeException("err"));
            tracer.error(null, new RuntimeException("err"));
            tracer.error(traceCtx, null);
        }

        @Test
        @DisplayName("endAgent() 空操作不抛异常")
        void shouldNoOpEndAgent() {
            AgentTracer tracer = AgentTracer.noOp();
            TraceContext traceCtx = tracer.startAgent(new AgentContext(
                    "PROJECT", "B001", "REF", "u1", "u", "MANUAL", new HashMap<>()));

            // 不抛异常即可
            tracer.endAgent(traceCtx, "{\"result\":\"ok\"}", true);
            tracer.endAgent(traceCtx, null, false);
            tracer.endAgent(null, null, false);
        }
    }

    // ==================== TraceContext 辅助方法可用 ====================

    @Test
    @DisplayName("返回的 TraceContext 可调用 markStepStart / stepCostMs")
    void shouldReturnTraceContextWithUsableHelpers() throws InterruptedException {
        AgentContext ctx = new AgentContext("PROJECT", "B001", "REF",
                "u1", "u", "MANUAL", new HashMap<>());
        AgentTracer tracer = AgentTracer.noOp();

        TraceContext traceCtx = tracer.startAgent(ctx);
        Thread.sleep(2);
        long cost = traceCtx.stepCostMs();
        assertThat(cost).isGreaterThanOrEqualTo(0L);

        traceCtx.markStepStart();
        assertThat(traceCtx.stepCostMs()).isLessThan(cost + 100);
    }
}
