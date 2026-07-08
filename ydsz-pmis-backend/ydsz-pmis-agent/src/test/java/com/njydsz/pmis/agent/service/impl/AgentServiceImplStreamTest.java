package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.StreamableAgent;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentServiceImpl#executeStream} 流式执行方法单元测试（P2-1 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>StreamableAgent 路径：调用 executeStream 并透传 listener</li>
 *   <li>非 StreamableAgent 路径：降级为同步 execute + 包装为单个事件</li>
 *   <li>无效 agentType：抛 BizException，触发 listener.onError + onComplete</li>
 *   <li>Agent 执行异常：触发 listener.onError + onComplete</li>
 *   <li>listener=null 自动降级为 NoOp</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentServiceImpl.executeStream 流式测试")
class AgentServiceImplStreamTest {

    @Mock
    private AgentPredictionMapper predictionMapper;

    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        // P2-3: 使用 NoOp tracer 避免对 tracing 行为产生依赖
        service = new AgentServiceImpl(List.of(), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));
    }

    private AgentContext ctx() {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("test");
        ctx.setBizId("B001");
        ctx.setBizRef("REF-001");
        return ctx;
    }

    // ==================== 辅助 mock ====================

    /** 构造 StreamableAgent mock */
    private StreamableAgent mockStreamable(AgentType type, AgentResult result) {
        StreamableAgent agent = mock(StreamableAgent.class);
        when(agent.type()).thenReturn(type);
        when(agent.executeStream(any(), any())).thenReturn(result);
        return agent;
    }

    /** 构造普通 Agent mock（非 StreamableAgent） */
    private Agent mockPlainAgent(AgentType type, AgentResult result) {
        Agent agent = mock(Agent.class);
        when(agent.type()).thenReturn(type);
        when(agent.execute(any())).thenReturn(result);
        return agent;
    }

    // ==================== 测试用例 ====================

    @Nested
    @DisplayName("StreamableAgent 路径")
    class StreamableAgentPathTest {

        @Test
        @DisplayName("StreamableAgent 调用 executeStream 并透传 listener")
        void shouldCallExecuteStreamForStreamableAgent() {
            AgentResult expected = new AgentResult(AgentType.FLOW_GENERATOR,
                    AgentAlertLevel.RECOMMEND, BigDecimal.valueOf(0.8),
                    BigDecimal.valueOf(0.75), "ok", List.of(), Map.of());
            StreamableAgent agent = mockStreamable(AgentType.FLOW_GENERATOR, expected);
            service = new AgentServiceImpl(List.of(agent), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));

            ReActEventListener listener = mock(ReActEventListener.class);
            AgentContext context = ctx();

            AgentResult result = service.executeStream(
                    AgentType.FLOW_GENERATOR.getCode(), context, listener);

            assertThat(result).isSameAs(expected);
            // P2-3: 传入的是 composite（业务 + tracing），不是原始 listener
            verify(agent, times(1)).executeStream(eq(context), any(ReActEventListener.class));
            verify(agent, never()).execute(any());
        }

        @Test
        @DisplayName("StreamableAgent 执行抛异常时触发 listener.onError + onComplete")
        void shouldTriggerOnErrorWhenStreamableThrows() {
            StreamableAgent agent = mock(StreamableAgent.class);
            when(agent.type()).thenReturn(AgentType.FLOW_GENERATOR);
            when(agent.executeStream(any(), any()))
                    .thenThrow(new RuntimeException("ReAct 故障"));
            service = new AgentServiceImpl(List.of(agent), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));

            ReActEventListener listener = mock(ReActEventListener.class);

            assertThatThrownBy(() -> service.executeStream(
                    AgentType.FLOW_GENERATOR.getCode(), ctx(), listener))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ReAct 故障");

            verify(listener, times(1)).onError(eq(0), any());
            verify(listener, times(1)).onComplete(any());
        }
    }

    @Nested
    @DisplayName("非 StreamableAgent 降级路径")
    class NonStreamableAgentPathTest {

        @Test
        @DisplayName("非 StreamableAgent 调用 execute 并包装为 FINAL_ANSWER 事件")
        void shouldFallbackToSyncExecuteForNonStreamable() {
            AgentResult result = new AgentResult(AgentType.RISK_WARNING,
                    AgentAlertLevel.RED, BigDecimal.valueOf(0.9),
                    BigDecimal.valueOf(0.8), "风险很高", List.of(), Map.of());
            Agent agent = mockPlainAgent(AgentType.RISK_WARNING, result);
            service = new AgentServiceImpl(List.of(agent), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));

            ReActEventListener listener = mock(ReActEventListener.class);

            AgentResult returned = service.executeStream(
                    AgentType.RISK_WARNING.getCode(), ctx(), listener);

            assertThat(returned).isSameAs(result);
            verify(agent, times(1)).execute(any());
            verify(listener, times(1)).onFinalAnswer(eq(1), eq("风险很高"));
            verify(listener, times(1)).onComplete(any());
        }

        @Test
        @DisplayName("非 StreamableAgent suggestion=null 时 onFinalAnswer 推送空字符串")
        void shouldHandleNullSuggestionForNonStreamable() {
            AgentResult result = new AgentResult(AgentType.RISK_WARNING,
                    AgentAlertLevel.NORMAL, null, null, null, null, null);
            Agent agent = mockPlainAgent(AgentType.RISK_WARNING, result);
            service = new AgentServiceImpl(List.of(agent), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));

            ReActEventListener listener = mock(ReActEventListener.class);

            service.executeStream(AgentType.RISK_WARNING.getCode(), ctx(), listener);

            verify(listener, times(1)).onFinalAnswer(eq(1), eq(""));
        }
    }

    @Nested
    @DisplayName("无效 agentType 路径")
    class InvalidAgentTypeTest {

        @Test
        @DisplayName("agentType=null 时抛 BizException，触发 onError + onComplete")
        void shouldThrowBizExceptionForInvalidAgentType() {
            ReActEventListener listener = mock(ReActEventListener.class);

            assertThatThrownBy(() -> service.executeStream("INVALID_TYPE", ctx(), listener))
                    .isInstanceOf(BizException.class);

            verify(listener, times(1)).onError(eq(0), any());
            verify(listener, times(1)).onComplete(any());
        }

        @Test
        @DisplayName("agentType=null 时抛 BizException")
        void shouldThrowBizExceptionForNullAgentType() {
            assertThatThrownBy(() -> service.executeStream(null, ctx(),
                    NoOpReActEventListener.getInstance()))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("listener 降级")
    class ListenerFallbackTest {

        @Test
        @DisplayName("listener=null 时降级为 NoOp，不抛异常")
        void shouldFallbackToNoOpWhenListenerNull() {
            AgentResult result = new AgentResult(AgentType.RISK_WARNING,
                    AgentAlertLevel.NORMAL, null, null, "ok", null, null);
            Agent agent = mockPlainAgent(AgentType.RISK_WARNING, result);
            service = new AgentServiceImpl(List.of(agent), predictionMapper, AgentTracer.noOp(), mock(ThreadPoolTaskExecutor.class));

            AgentResult returned = service.executeStream(
                    AgentType.RISK_WARNING.getCode(), ctx(), null);

            assertThat(returned).isSameAs(result);
        }
    }
}
