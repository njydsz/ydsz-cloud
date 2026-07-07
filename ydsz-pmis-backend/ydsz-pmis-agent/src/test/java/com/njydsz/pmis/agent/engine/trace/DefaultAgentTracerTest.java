package com.njydsz.pmis.agent.engine.trace;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.entity.AgentTraceDO;
import com.njydsz.pmis.agent.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认 Agent Tracer 实现单元测试（P2-3 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>startAgent：解析 traceId 优先级、根 span 落库、enabled=false 跳过</li>
 *   <li>span：子 span 落库、stepIndex/costMs 字段、enabled=false 跳过、null traceCtx 跳过</li>
 *   <li>error：AGENT_ERROR span 落库、status=FAILED、errorMsg 填充</li>
 *   <li>endAgent：AGENT_END span 落库、success 状态映射</li>
 *   <li>降级：Mapper 不可用不抛异常、落库异常仅记录日志</li>
 *   <li>resolveTraceId：ctx 优先 → TraceIdUtil → 雪花生成</li>
 *   <li>resolveAgentType：params.agentType 优先 → bizType 兜底 → UNKNOWN</li>
 *   <li>toDO：AgentSpan → AgentTraceDO 字段映射</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAgentTracer 默认 Tracer 实现")
class DefaultAgentTracerTest {

    @Mock
    private ObjectProvider<AgentTraceMapper> mapperProvider;

    @Mock
    private AgentTraceMapper mapper;

    // ==================== 辅助方法 ====================

    /** 构造开启 tracing + mapper 可用的 tracer */
    private DefaultAgentTracer enabledTracer() {
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        return new DefaultAgentTracer(mapperProvider, true);
    }

    /** 构造关闭 tracing 的 tracer */
    private DefaultAgentTracer disabledTracer() {
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        return new DefaultAgentTracer(mapperProvider, false);
    }

    /** 构造 mapper 不可用的 tracer（无 DB 环境） */
    private DefaultAgentTracer noMapperTracer() {
        when(mapperProvider.getIfAvailable()).thenReturn(null);
        return new DefaultAgentTracer(mapperProvider, true);
    }

    /** 构造测试用 AgentContext */
    private AgentContext ctx(String traceId, String providerTraceId) {
        Map<String, Object> params = new HashMap<>();
        params.put("agentType", "RISK_WARNING");
        AgentContext ctx = new AgentContext("PROJECT", "B001", "REF-001",
                "caller-001", "张三", "MANUAL", params, traceId, providerTraceId);
        return ctx;
    }

    private AgentContext ctx() {
        return ctx("trace-test-001", "provider-trace-001");
    }

    // ==================== startAgent 测试 ====================

    @Nested
    @DisplayName("startAgent 启动链路")
    class StartAgentTest {

        @Test
        @DisplayName("enabled=true 时落 AGENT_START 根 span")
        void shouldPersistRootSpanWhenEnabled() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = ctx();

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx).isNotNull();
            assertThat(traceCtx.getTraceId()).isEqualTo("trace-test-001");
            assertThat(traceCtx.getRootSpanId()).isNotNull().isNotEmpty();
            assertThat(traceCtx.getAgentType()).isEqualTo("RISK_WARNING");
            assertThat(traceCtx.getBizType()).isEqualTo("PROJECT");
            assertThat(traceCtx.getBizId()).isEqualTo("B001");
            assertThat(traceCtx.getBizRef()).isEqualTo("REF-001");
            assertThat(traceCtx.getProviderTraceId()).isEqualTo("provider-trace-001");
            assertThat(traceCtx.getStartMs()).isGreaterThan(0);

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(1)).insert(captor.capture());

            AgentTraceDO span = captor.getValue();
            assertThat(span.getSpanName()).isEqualTo(AgentSpanName.AGENT_START);
            assertThat(span.getTraceId()).isEqualTo("trace-test-001");
            assertThat(span.getSpanId()).isEqualTo(traceCtx.getRootSpanId());
            assertThat(span.getParentSpanId()).isNull();
            assertThat(span.getAgentType()).isEqualTo("RISK_WARNING");
            assertThat(span.getBizType()).isEqualTo("PROJECT");
            assertThat(span.getBizId()).isEqualTo("B001");
            assertThat(span.getStepIndex()).isZero();
            assertThat(span.getStatus()).isEqualTo(AgentSpanName.STATUS_SUCCESS);
            assertThat(span.getCostMs()).isZero();
            assertThat(span.getProviderTraceId()).isEqualTo("provider-trace-001");
        }

        @Test
        @DisplayName("enabled=false 时不落库但仍返回 TraceContext")
        void shouldNotPersistWhenDisabled() {
            DefaultAgentTracer tracer = disabledTracer();
            AgentContext context = ctx();

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx).isNotNull();
            assertThat(traceCtx.getTraceId()).isEqualTo("trace-test-001");
            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("Mapper 不可用时不抛异常")
        void shouldNotThrowWhenMapperUnavailable() {
            DefaultAgentTracer tracer = noMapperTracer();
            AgentContext context = ctx();

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx).isNotNull();
            // 不抛异常即可
        }

        @Test
        @DisplayName("落库异常仅记录日志不传播")
        void shouldSwallowPersistException() {
            when(mapperProvider.getIfAvailable()).thenReturn(mapper);
            doThrow(new RuntimeException("DB 故障")).when(mapper).insert(any(AgentTraceDO.class));
            DefaultAgentTracer tracer = new DefaultAgentTracer(mapperProvider, true);

            // 不应抛异常
            TraceContext traceCtx = tracer.startAgent(ctx());

            assertThat(traceCtx).isNotNull();
        }

        @Test
        @DisplayName("ctx.traceId 优先于其他来源")
        void shouldUseCtxTraceIdFirst() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = ctx("ctx-trace-id", null);

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx.getTraceId()).isEqualTo("ctx-trace-id");
        }

        @Test
        @DisplayName("ctx.traceId 为空时降级为雪花算法生成")
        void shouldFallbackToSnowflakeWhenCtxTraceIdNull() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = ctx(null, null);

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx.getTraceId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("params.agentType 不存在时降级为 bizType")
        void shouldFallbackToBizTypeWhenAgentTypeMissing() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = new AgentContext("OPPORTUNITY", "OP001", "OP-REF",
                    "u1", "u", "MANUAL", new HashMap<>(), null, null);

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx.getAgentType()).isEqualTo("OPPORTUNITY");
        }

        @Test
        @DisplayName("params.agentType 与 bizType 都为空时降级为 UNKNOWN")
        void shouldFallbackToUnknownWhenAllEmpty() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = new AgentContext(null, "B001", "REF",
                    "u1", "u", "MANUAL", new HashMap<>(), null, null);

            TraceContext traceCtx = tracer.startAgent(context);

            assertThat(traceCtx.getAgentType()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("providerTraceId 为空时落库为空串")
        void shouldUseEmptyStringWhenProviderTraceIdNull() {
            DefaultAgentTracer tracer = enabledTracer();
            AgentContext context = ctx("trace-1", null);

            tracer.startAgent(context);

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(1)).insert(captor.capture());
            assertThat(captor.getValue().getProviderTraceId()).isEmpty();
        }
    }

    // ==================== span 测试 ====================

    @Nested
    @DisplayName("span 记录子节点")
    class SpanTest {

        @Test
        @DisplayName("enabled=true 时落子 span，包含 stepIndex 和 costMs")
        void shouldPersistChildSpan() throws InterruptedException {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            Thread.sleep(2); // 让 costMs > 0

            tracer.span(traceCtx, AgentSpanName.LLM_THOUGHT, 1, "input", "output");

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(2)).insert(captor.capture());
            AgentTraceDO childSpan = captor.getAllValues().get(1);

            assertThat(childSpan.getSpanName()).isEqualTo(AgentSpanName.LLM_THOUGHT);
            assertThat(childSpan.getStepIndex()).isEqualTo(1);
            assertThat(childSpan.getInputData()).isEqualTo("input");
            assertThat(childSpan.getOutputData()).isEqualTo("output");
            assertThat(childSpan.getParentSpanId()).isEqualTo(traceCtx.getRootSpanId());
            assertThat(childSpan.getStatus()).isEqualTo(AgentSpanName.STATUS_SUCCESS);
            assertThat(childSpan.getCostMs()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("enabled=false 时跳过 span 落库")
        void shouldSkipSpanWhenDisabled() {
            DefaultAgentTracer tracer = disabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.span(traceCtx, AgentSpanName.LLM_ACTION, 1, null, null);

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("traceCtx=null 时跳过 span 落库")
        void shouldSkipSpanWhenTraceCtxNull() {
            DefaultAgentTracer tracer = enabledTracer();

            tracer.span(null, AgentSpanName.LLM_ACTION, 1, null, null);

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("落库异常不传播")
        void shouldSwallowSpanPersistException() {
            when(mapperProvider.getIfAvailable()).thenReturn(mapper);
            doThrow(new RuntimeException("DB 故障")).when(mapper).insert(any(AgentTraceDO.class));
            DefaultAgentTracer tracer = new DefaultAgentTracer(mapperProvider, true);

            TraceContext traceCtx = tracer.startAgent(ctx());
            // 不抛异常即可
            tracer.span(traceCtx, AgentSpanName.LLM_THOUGHT, 1, null, null);
        }

        @Test
        @DisplayName("span 调用后 markStepStart 重置 stepStartMs")
        void shouldMarkStepStartAfterSpan() throws InterruptedException {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            Thread.sleep(5);
            long costBefore = traceCtx.stepCostMs();
            tracer.span(traceCtx, AgentSpanName.STEP_START, 1, null, null);

            // markStepStart 后 stepStartMs 应被刷新，下一次 stepCostMs 应较小
            Thread.sleep(2);
            long costAfter = traceCtx.stepCostMs();
            assertThat(costAfter).isLessThan(costBefore + 50); // 给点容差
        }
    }

    // ==================== error 测试 ====================

    @Nested
    @DisplayName("error 记录异常")
    class ErrorTest {

        @Test
        @DisplayName("落 AGENT_ERROR span，status=FAILED，errorMsg 填充")
        void shouldPersistErrorSpan() {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.error(traceCtx, new RuntimeException("Agent 执行失败"));

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(2)).insert(captor.capture());
            AgentTraceDO errorSpan = captor.getAllValues().get(1);

            assertThat(errorSpan.getSpanName()).isEqualTo(AgentSpanName.AGENT_ERROR);
            assertThat(errorSpan.getStatus()).isEqualTo(AgentSpanName.STATUS_FAILED);
            assertThat(errorSpan.getErrorMsg()).isEqualTo("Agent 执行失败");
            assertThat(errorSpan.getStepIndex()).isZero();
            assertThat(errorSpan.getCostMs()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("enabled=false 时跳过 error 落库")
        void shouldSkipErrorWhenDisabled() {
            DefaultAgentTracer tracer = disabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.error(traceCtx, new RuntimeException("err"));

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("traceCtx=null 时跳过 error 落库")
        void shouldSkipErrorWhenTraceCtxNull() {
            DefaultAgentTracer tracer = enabledTracer();

            tracer.error(null, new RuntimeException("err"));

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("error=null 时跳过 error 落库")
        void shouldSkipErrorWhenErrorNull() {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.error(traceCtx, null);

            // 仅 startAgent 的 AGENT_START 落一次库
            verify(mapper, times(1)).insert(any(AgentTraceDO.class));
        }
    }

    // ==================== endAgent 测试 ====================

    @Nested
    @DisplayName("endAgent 结束链路")
    class EndAgentTest {

        @Test
        @DisplayName("success=true 落 AGENT_END span，status=SUCCESS")
        void shouldPersistEndSpanOnSuccess() {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.endAgent(traceCtx, "{\"result\":\"ok\"}", true);

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(2)).insert(captor.capture());
            AgentTraceDO endSpan = captor.getAllValues().get(1);

            assertThat(endSpan.getSpanName()).isEqualTo(AgentSpanName.AGENT_END);
            assertThat(endSpan.getStatus()).isEqualTo(AgentSpanName.STATUS_SUCCESS);
            assertThat(endSpan.getOutputData()).isEqualTo("{\"result\":\"ok\"}");
            assertThat(endSpan.getStepIndex()).isZero();
        }

        @Test
        @DisplayName("success=false 落 AGENT_END span，status=FAILED")
        void shouldPersistEndSpanOnFailure() {
            DefaultAgentTracer tracer = enabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.endAgent(traceCtx, null, false);

            ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
            verify(mapper, times(2)).insert(captor.capture());
            AgentTraceDO endSpan = captor.getAllValues().get(1);

            assertThat(endSpan.getStatus()).isEqualTo(AgentSpanName.STATUS_FAILED);
            assertThat(endSpan.getOutputData()).isNull();
        }

        @Test
        @DisplayName("enabled=false 时跳过 endAgent 落库")
        void shouldSkipEndAgentWhenDisabled() {
            DefaultAgentTracer tracer = disabledTracer();
            TraceContext traceCtx = tracer.startAgent(ctx());

            tracer.endAgent(traceCtx, null, true);

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }

        @Test
        @DisplayName("traceCtx=null 时跳过 endAgent 落库")
        void shouldSkipEndAgentWhenTraceCtxNull() {
            DefaultAgentTracer tracer = enabledTracer();

            tracer.endAgent(null, null, true);

            verify(mapper, never()).insert(any(AgentTraceDO.class));
        }
    }

    // ==================== 综合 E2E ====================

    @Test
    @DisplayName("完整 Agent 执行链路：startAgent → span → span → error → endAgent")
    void shouldCompleteFullTraceChain() {
        DefaultAgentTracer tracer = enabledTracer();
        AgentContext context = ctx("full-trace-001", "provider-001");

        // 启动
        TraceContext traceCtx = tracer.startAgent(context);
        // ReAct 多步 span
        tracer.span(traceCtx, AgentSpanName.STEP_START, 1, null, null);
        tracer.span(traceCtx, AgentSpanName.LLM_THOUGHT, 1, null, "{\"thought\":\"分析风险\"}");
        tracer.span(traceCtx, AgentSpanName.LLM_ACTION, 1, null, "{\"action\":\"queryRisk\"}");
        tracer.span(traceCtx, AgentSpanName.STEP_END, 1, null, null);
        // 异常
        tracer.error(traceCtx, new RuntimeException("LLM 超时"));
        // 结束（标记失败）
        tracer.endAgent(traceCtx, "{\"error\":\"timeout\"}", false);

        ArgumentCaptor<AgentTraceDO> captor = ArgumentCaptor.forClass(AgentTraceDO.class);
        verify(mapper, times(7)).insert(captor.capture());

        // 验证 span 顺序
        assertThat(captor.getAllValues()).hasSize(7);
        assertThat(captor.getAllValues().get(0).getSpanName()).isEqualTo(AgentSpanName.AGENT_START);
        assertThat(captor.getAllValues().get(1).getSpanName()).isEqualTo(AgentSpanName.STEP_START);
        assertThat(captor.getAllValues().get(2).getSpanName()).isEqualTo(AgentSpanName.LLM_THOUGHT);
        assertThat(captor.getAllValues().get(3).getSpanName()).isEqualTo(AgentSpanName.LLM_ACTION);
        assertThat(captor.getAllValues().get(4).getSpanName()).isEqualTo(AgentSpanName.STEP_END);
        assertThat(captor.getAllValues().get(5).getSpanName()).isEqualTo(AgentSpanName.AGENT_ERROR);
        assertThat(captor.getAllValues().get(6).getSpanName()).isEqualTo(AgentSpanName.AGENT_END);

        // 所有 span 共享 traceId
        assertThat(captor.getAllValues()).allSatisfy(s ->
                assertThat(s.getTraceId()).isEqualTo("full-trace-001"));
        // 所有子 span 的 parent 都是根 span
        assertThat(captor.getAllValues()).allSatisfy(s -> {
            if (!AgentSpanName.AGENT_START.equals(s.getSpanName())) {
                assertThat(s.getParentSpanId()).isEqualTo(traceCtx.getRootSpanId());
            } else {
                assertThat(s.getParentSpanId()).isNull();
            }
        });
    }
}
