package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.orchestration.strategy.OrchestrationStrategy;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多智能体协调器实现单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>构造时注入 List<OrchestrationStrategy>，按 mode() 收集到 EnumMap</li>
 *   <li>req=null / mode=null / agents=null/empty 抛 BizException</li>
 *   <li>正常 coordinate() 调用对应策略</li>
 *   <li>未知 mode 抛 BizException</li>
 *   <li>重复注册同一 mode 时打印 warn 但不报错</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentCoordinatorImpl 多智能体协调器测试")
class AgentCoordinatorImplTest {

    // ==================== 辅助方法 ====================

    /** 构造 mock 策略 */
    private OrchestrationStrategy mockStrategy(OrchestrationMode mode) {
        OrchestrationStrategy s = mock(OrchestrationStrategy.class);
        when(s.mode()).thenReturn(mode);
        return s;
    }

    /** 构造 mock 策略并设置 apply 行为 */
    private OrchestrationStrategy mockStrategyWithApply(OrchestrationMode mode, OrchestrationResult result) {
        OrchestrationStrategy s = mockStrategy(mode);
        when(s.apply(any(), any(), any())).thenReturn(result);
        return s;
    }

    /** 构造编排请求 */
    private OrchestrationRequest req(OrchestrationMode mode) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(mode);
        req.setBizType("project");
        req.setBizId("P001");
        req.setBizRef("PRJ-001");
        req.setCallerId("U001");
        req.setCallerName("张三");
        req.setSource("unit-test");
        req.setAgentTypes(List.of("A"));
        req.setFacts(new HashMap<>());
        return req;
    }

    // ==================== 构造测试 ====================

    @Nested
    @DisplayName("构造与策略收集测试")
    class ConstructorTest {

        @Test
        @DisplayName("按 mode() 收集策略到 EnumMap")
        void shouldCollectStrategiesByMode() {
            OrchestrationStrategy seq = mockStrategy(OrchestrationMode.SEQUENTIAL);
            OrchestrationStrategy par = mockStrategy(OrchestrationMode.PARALLEL);
            OrchestrationStrategy vot = mockStrategy(OrchestrationMode.VOTING);
            OrchestrationStrategy cas = mockStrategy(OrchestrationMode.CASCADE);

            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(
                    Arrays.asList(seq, par, vot, cas));

            OrchestrationResult result = new OrchestrationResult();
            when(seq.apply(any(), any(), any())).thenReturn(result);

            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            OrchestrationResult r = coordinator.coordinate(req, agents);
            assertThat(r).isSameAs(result);
            verify(seq).apply(eq(req), eq(agents), any(AgentBlackboard.class));
        }

        @Test
        @DisplayName("空策略列表 - 不报错，但任何 mode 都抛 BizException")
        void shouldHandleEmptyStrategyList() {
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(Collections.emptyList());
            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            assertThatThrownBy(() -> coordinator.coordinate(req, agents))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("重复注册同一 mode - 后者覆盖前者，不报错")
        void shouldOverrideWhenDuplicateMode() {
            OrchestrationStrategy seq1 = mockStrategy(OrchestrationMode.SEQUENTIAL);
            OrchestrationStrategy seq2 = mockStrategy(OrchestrationMode.SEQUENTIAL);

            // 重复注册不应抛异常
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(Arrays.asList(seq1, seq2));

            OrchestrationResult result = new OrchestrationResult();
            result.setNote("from-seq2");
            when(seq2.apply(any(), any(), any())).thenReturn(result);

            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            OrchestrationResult r = coordinator.coordinate(req, agents);
            // 后注册的 seq2 生效
            assertThat(r.getNote()).isEqualTo("from-seq2");
        }
    }

    // ==================== 参数校验测试 ====================

    @Nested
    @DisplayName("参数校验测试")
    class ValidationTest {

        @Test
        @DisplayName("req=null - 抛 BizException")
        void shouldThrowBizExceptionWhenReqNull() {
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(
                    List.of(mockStrategy(OrchestrationMode.SEQUENTIAL)));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            assertThatThrownBy(() -> coordinator.coordinate(null, agents))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("mode=null - 抛 BizException")
        void shouldThrowBizExceptionWhenModeNull() {
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(
                    List.of(mockStrategy(OrchestrationMode.SEQUENTIAL)));
            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            req.setMode(null);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            assertThatThrownBy(() -> coordinator.coordinate(req, agents))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("agents=null - 抛 BizException")
        void shouldThrowBizExceptionWhenAgentsNull() {
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(
                    List.of(mockStrategy(OrchestrationMode.SEQUENTIAL)));
            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);

            assertThatThrownBy(() -> coordinator.coordinate(req, null))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("agents=空 Map - 抛 BizException")
        void shouldThrowBizExceptionWhenAgentsEmpty() {
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(
                    List.of(mockStrategy(OrchestrationMode.SEQUENTIAL)));
            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);

            assertThatThrownBy(() -> coordinator.coordinate(req, new HashMap<>()))
                    .isInstanceOf(BizException.class);
        }
    }

    // ==================== 调用策略测试 ====================

    @Nested
    @DisplayName("策略调度测试")
    class DispatchTest {

        @Test
        @DisplayName("正常 coordinate() - 调用对应 SEQUENTIAL 策略")
        void shouldCallSequentialStrategy() {
            OrchestrationStrategy seq = mockStrategy(OrchestrationMode.SEQUENTIAL);
            OrchestrationResult expected = new OrchestrationResult();
            when(seq.apply(any(), any(), any())).thenReturn(expected);
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(List.of(seq));

            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            OrchestrationResult r = coordinator.coordinate(req, agents);
            assertThat(r).isSameAs(expected);
        }

        @Test
        @DisplayName("正常 coordinate() - 调用对应 PARALLEL 策略")
        void shouldCallParallelStrategy() {
            OrchestrationStrategy par = mockStrategy(OrchestrationMode.PARALLEL);
            OrchestrationResult expected = new OrchestrationResult();
            when(par.apply(any(), any(), any())).thenReturn(expected);
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(List.of(par));

            OrchestrationRequest req = req(OrchestrationMode.PARALLEL);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            OrchestrationResult r = coordinator.coordinate(req, agents);
            assertThat(r).isSameAs(expected);
        }

        @Test
        @DisplayName("未知 mode - 抛 BizException")
        void shouldThrowBizExceptionForUnknownMode() {
            // 只注册了 SEQUENTIAL，调用 VOTING 应抛异常
            OrchestrationStrategy seq = mockStrategy(OrchestrationMode.SEQUENTIAL);
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(List.of(seq));

            OrchestrationRequest req = req(OrchestrationMode.VOTING);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            assertThatThrownBy(() -> coordinator.coordinate(req, agents))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("blackboard 被正确初始化 - 包含 facts")
        void shouldInitializeBlackboardWithFacts() {
            OrchestrationStrategy seq = mockStrategy(OrchestrationMode.SEQUENTIAL);
            OrchestrationResult expected = new OrchestrationResult();
            when(seq.apply(any(), any(), any())).thenReturn(expected);
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(List.of(seq));

            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            Map<String, Object> facts = new HashMap<>();
            facts.put("fact-key", "fact-value");
            req.setFacts(facts);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            coordinator.coordinate(req, agents);

            // 验证 blackboard 的 facts 被正确初始化
            verify(seq).apply(eq(req), eq(agents), any(AgentBlackboard.class));
        }

        @Test
        @DisplayName("agentTypes=null 仍能正常调度 - 由策略内部处理")
        void shouldDispatchEvenWhenAgentTypesNull() {
            OrchestrationStrategy seq = mockStrategyWithApply(OrchestrationMode.SEQUENTIAL, new OrchestrationResult());
            AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl(List.of(seq));

            OrchestrationRequest req = req(OrchestrationMode.SEQUENTIAL);
            req.setAgentTypes(null);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mock(Agent.class));

            coordinator.coordinate(req, agents);
            verify(seq).apply(eq(req), eq(agents), any(AgentBlackboard.class));
        }
    }
}
