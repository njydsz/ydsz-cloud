package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DagExecutor 单元测试（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@DisplayName("DagExecutor 执行引擎")
class DagExecutorTest {

    private DagExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DagExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.destroy();
    }

    /**
     * 创建 mock Agent，execute 返回固定结果。
     */
    private Agent mockAgent(String output) {
        Agent agent = mock(Agent.class);
        when(agent.type()).thenReturn(AgentType.RISK_WARNING);
        AgentResult result = new AgentResult();
        result.setSuggestion(output);
        when(agent.execute(any())).thenReturn(result);
        return agent;
    }

    /**
     * 创建失败的 mock Agent。
     */
    private Agent mockFailingAgent(String errorMsg) {
        Agent agent = mock(Agent.class);
        when(agent.type()).thenReturn(AgentType.RISK_WARNING);
        when(agent.execute(any())).thenThrow(new RuntimeException(errorMsg));
        return agent;
    }

    /**
     * 创建重试后成功的 mock Agent（前 N 次失败，第 N+1 次成功）。
     */
    private Agent mockRetryAgent(int failCount) {
        Agent agent = mock(Agent.class);
        when(agent.type()).thenReturn(AgentType.RISK_WARNING);
        AtomicInteger counter = new AtomicInteger(0);
        when(agent.execute(any())).thenAnswer(invocation -> {
            if (counter.incrementAndGet() <= failCount) {
                throw new RuntimeException("transient failure #" + counter.get());
            }
            AgentResult result = new AgentResult();
            result.setSuggestion("recovered");
            return result;
        });
        return agent;
    }

    @Nested
    @DisplayName("基础执行")
    class BasicExecutionTest {

        @Test
        @DisplayName("线性链全部成功")
        void shouldExecuteLinearChainSuccessfully() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagNode b = DagNode.builder().name("b").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").agentType("RISK_WARNING").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("linear").nodes(List.of(a, b, c))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getSuccessCount()).isEqualTo(3);
            assertThat(result.getFailedCount()).isZero();
            assertThat(result.getSkippedCount()).isZero();
            assertThat(result.getTotalNodes()).isEqualTo(3);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SUCCESS);
            assertThat(result.getNodeStatuses().get("b")).isEqualTo(DagNodeStatus.SUCCESS);
            assertThat(result.getNodeStatuses().get("c")).isEqualTo(DagNodeStatus.SUCCESS);
        }

        @Test
        @DisplayName("菱形并行执行全部成功")
        void shouldExecuteDiamondInParallel() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagNode b = DagNode.builder().name("b").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagNode d = DagNode.builder().name("d").agentType("RISK_WARNING").dependsOn(List.of("b", "c")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("diamond").nodes(List.of(a, b, c, d))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getSuccessCount()).isEqualTo(4);
            assertThat(result.getNodeOutputs()).containsKeys("a", "b", "c", "d");
        }

        @Test
        @DisplayName("空节点（无 agentType）直接成功")
        void shouldExecuteEmptyNodeSuccessfully() {
            DagNode a = DagNode.builder().name("a").build(); // 无 agentType
            DagDefinition dag = DagDefinition.builder()
                    .name("empty").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            DagExecutionResult result = executor.execute(dag, new HashMap<>(), new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SUCCESS);
        }

        @Test
        @DisplayName("单节点成功并返回输出")
        void shouldReturnNodeOutput() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("single").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("risk-high"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getNodeOutputs()).containsKey("a");
            Object output = result.getNodeOutputs().get("a");
            assertThat(output).isInstanceOf(AgentResult.class);
            assertThat(((AgentResult) output).getSuggestion()).isEqualTo("risk-high");
        }
    }

    @Nested
    @DisplayName("条件分支")
    class ConditionBranchTest {

        @Test
        @DisplayName("条件为 true 时执行节点")
        void shouldExecuteWhenConditionTrue() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING")
                    .condition("['amount'] > 50").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cond").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("amount", 100);

            DagExecutionResult result = executor.execute(dag, agents, inputs, null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SUCCESS);
        }

        @Test
        @DisplayName("条件为 false 时跳过节点")
        void shouldSkipWhenConditionFalse() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING")
                    .condition("['amount'] > 200").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cond").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("amount", 100);

            DagExecutionResult result = executor.execute(dag, agents, inputs, null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SKIPPED);
            assertThat(result.getSkippedCount()).isEqualTo(1);
            assertThat(result.getSuccessCount()).isZero();
        }

        @Test
        @DisplayName("条件表达式异常时跳过（保守策略）")
        void shouldSkipWhenConditionExpressionInvalid() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING")
                    .condition("invalid syntax !!!").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cond").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SKIPPED);
        }

        @Test
        @DisplayName("条件跳过后下游也跳过")
        void shouldSkipDownstreamWhenUpstreamSkipped() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING")
                    .condition("['skip'] == true").build();
            DagNode b = DagNode.builder().name("b").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cascade").nodes(List.of(a, b))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SKIPPED);
            assertThat(result.getNodeStatuses().get("b")).isEqualTo(DagNodeStatus.SKIPPED);
            assertThat(result.getSkippedCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("失败策略")
    class FailureStrategyTest {

        @Test
        @DisplayName("ABORT 策略：失败后中止整个 DAG")
        void shouldAbortOnFailure() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagNode b = DagNode.builder().name("b").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").agentType("RISK_WARNING").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("abort").nodes(List.of(a, b, c))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockFailingAgent("boom"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
            assertThat(result.getNodeStatuses().get("b")).isEqualTo(DagNodeStatus.SKIPPED);
            assertThat(result.getNodeStatuses().get("c")).isEqualTo(DagNodeStatus.SKIPPED);
            assertThat(result.getFailedCount()).isEqualTo(1);
            assertThat(result.getSkippedCount()).isEqualTo(2);
            assertThat(result.getNote()).contains("a");
        }

        @Test
        @DisplayName("CONTINUE 策略：失败后失败节点的下游跳过，其他分支继续")
        void shouldContinueOnFailure() {
            // a 失败，c 成功
            Agent failAgent = mockFailingAgent("fail");
            Agent okAgent = mockAgent("ok");
            Map<String, Agent> agents = new HashMap<>();
            // 需要让 a 失败但 c 成功，用不同 agentType
            DagNode aNode = DagNode.builder().name("a").agentType("FAIL_AGENT").build();
            DagNode bNode = DagNode.builder().name("b").agentType("OK_AGENT").dependsOn(List.of("a")).build();
            DagNode cNode = DagNode.builder().name("c").agentType("OK_AGENT").build();
            DagDefinition dag2 = DagDefinition.builder()
                    .name("continue").nodes(List.of(aNode, bNode, cNode))
                    .failureStrategy(DagFailureStrategy.CONTINUE).build();
            agents.put("FAIL_AGENT", failAgent);
            agents.put("OK_AGENT", okAgent);

            DagExecutionResult result = executor.execute(dag2, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
            assertThat(result.getNodeStatuses().get("b")).isEqualTo(DagNodeStatus.SKIPPED); // 上游失败跳过
            assertThat(result.getNodeStatuses().get("c")).isEqualTo(DagNodeStatus.SUCCESS); // 独立分支继续
            assertThat(result.getFailedCount()).isEqualTo(1);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getSkippedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("RETRY 策略：重试后成功")
        void shouldRetryAndSucceed() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("retry").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.RETRY)
                    .maxRetries(3).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockRetryAgent(2)); // 前 2 次失败，第 3 次成功

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.SUCCESS);
            assertThat(result.getRetryCount("a")).isEqualTo(2);
        }

        @Test
        @DisplayName("RETRY 策略：重试耗尽后失败")
        void shouldFailAfterRetriesExhausted() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("retry").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.RETRY)
                    .maxRetries(2).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockFailingAgent("always fail"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
            assertThat(result.getRetryCount("a")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("异常场景")
    class ErrorScenarioTest {

        @Test
        @DisplayName("Agent 不存在时节点失败")
        void shouldFailWhenAgentNotFound() {
            DagNode a = DagNode.builder().name("a").agentType("GHOST").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("noAgent").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            DagExecutionResult result = executor.execute(dag, new HashMap<>(), new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
            assertThat(result.getNodeErrors()).containsKey("a");
        }

        @Test
        @DisplayName("空 agents Map 且节点有 agentType 时失败")
        void shouldFailWhenAgentsNull() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("nullAgents").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            DagExecutionResult result = executor.execute(dag, null, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
        }

        @Test
        @DisplayName("执行追踪包含完整事件链")
        void shouldRecordCompleteTrace() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagNode b = DagNode.builder().name("b").agentType("RISK_WARNING").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("trace").nodes(List.of(a, b))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Map<String, Agent> agents = new HashMap<>();
            agents.put("RISK_WARNING", mockAgent("ok"));

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getTraces()).isNotEmpty();
            assertThat(result.getTraces())
                    .anyMatch(t -> "DAG_STARTED".equals(t.getEvent()));
            assertThat(result.getTraces())
                    .anyMatch(t -> "DAG_FINISHED".equals(t.getEvent()));
            assertThat(result.getTraces())
                    .anyMatch(t -> "SUCCESS".equals(t.getEvent()) && "a".equals(t.getNodeName()));
            assertThat(result.getTraces())
                    .anyMatch(t -> "SUCCESS".equals(t.getEvent()) && "b".equals(t.getNodeName()));
        }
    }

    @Nested
    @DisplayName("超时控制")
    class TimeoutTest {

        @Test
        @DisplayName("节点超时后标记 FAILED")
        void shouldMarkFailedOnTimeout() {
            DagNode a = DagNode.builder().name("a").agentType("SLOW")
                    .timeoutMs(100).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("timeout").nodes(List.of(a))
                    .failureStrategy(DagFailureStrategy.ABORT).build();

            Agent slowAgent = mock(Agent.class);
            when(slowAgent.type()).thenReturn(AgentType.RISK_WARNING);
            when(slowAgent.execute(any())).thenAnswer(invocation -> {
                Thread.sleep(500); // 超过 100ms 超时
                return new AgentResult();
            });
            Map<String, Agent> agents = new HashMap<>();
            agents.put("SLOW", slowAgent);

            DagExecutionResult result = executor.execute(dag, agents, new HashMap<>(), null);

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.FAILED);
            assertThat(result.getNodeStatuses().get("a")).isEqualTo(DagNodeStatus.FAILED);
        }
    }
}
