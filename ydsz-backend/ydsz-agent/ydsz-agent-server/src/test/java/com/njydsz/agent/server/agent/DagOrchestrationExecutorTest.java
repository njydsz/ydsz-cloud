package com.njydsz.agent.server.agent;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.njydsz.agent.domain.agent.AgentDag;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.config.AgentProperties;

/**
 * {@link DagOrchestrationExecutor} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>并行执行无依赖节点</li>
 *   <li>串行执行有依赖节点（A→B 依赖链，数据传递）</li>
 *   <li>节点失败时下游自动跳过</li>
 *   <li>DAG 存在环时抛 IllegalArgumentException</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DAG 编排执行器 DagOrchestrationExecutor 测试")
@ExtendWith(MockitoExtension.class)
class DagOrchestrationExecutorTest {

    @Mock
    private LlmClient llmClient;

    private AgentProperties properties;
    private DagOrchestrationExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.getLlm().setDefaultModel("gpt-4o-mini");
        executor = new DagOrchestrationExecutor(llmClient, properties, null);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private ChatResponse mockResponse(String content, int tokens) {
        TokenUsage usage = new TokenUsage(tokens, tokens);
        ChatMessage msg = ChatMessage.assistant(content, null, usage);
        return new ChatResponse("resp-" + content.hashCode(), "gpt-4o-mini", msg, usage, "stop", List.of());
    }

    private AgentDag buildDag(String name, Map<String, AgentDag.Node> nodes,
                               Map<String, List<String>> edges) {
        return new AgentDag("dag-1", name, nodes, edges);
    }

    private AgentDag.Node node(String id, String prompt) {
        return new AgentDag.Node(id, "CHAT", prompt, null, Map.of());
    }

    private AgentDag.Node nodeWithInput(String id, String prompt, String inputFrom) {
        return new AgentDag.Node(id, "CHAT", prompt, inputFrom, Map.of());
    }

    @Nested
    @DisplayName("并行执行")
    class ParallelExecution {

        @Test
        @DisplayName("两个无依赖节点并行执行，均成功")
        void shouldExecuteParallelNodes() {
            AgentDag dag = buildDag("parallel",
                    Map.of(
                            "a", node("a", "分析A"),
                            "b", node("b", "分析B")),
                    Map.of());

            when(llmClient.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("结果A", 10))
                    .thenReturn(mockResponse("结果B", 20));

            DagOrchestrationExecutor.DagExecutionResult result = executor.execute(dag, "用户需求");

            assertThat(result.hasFailure()).isFalse();
            assertThat(result.completedNodes()).containsExactlyInAnyOrder("a", "b");
            assertThat(result.failedNodes()).isEmpty();
            assertThat(result.nodeResults()).containsEntry("a", "结果A");
            assertThat(result.nodeResults()).containsEntry("b", "结果B");
            assertThat(result.nodeUsages().get("a").getTotalTokens()).isEqualTo(20);
            assertThat(result.nodeUsages().get("b").getTotalTokens()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("串行执行（依赖链）")
    class SequentialExecution {

        @Test
        @DisplayName("A→B 依赖链：A 先执行，B 接收 A 的输出")
        void shouldExecuteSequentialChain() {
            AgentDag dag = buildDag("chain",
                    Map.of(
                            "a", node("a", "分析"),
                            "b", nodeWithInput("b", "生成报告", "a")),
                    Map.of("b", List.of("a")));

            when(llmClient.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("分析结果", 10))
                    .thenReturn(mockResponse("最终报告", 20));

            DagOrchestrationExecutor.DagExecutionResult result = executor.execute(dag, "项目分析");

            assertThat(result.hasFailure()).isFalse();
            assertThat(result.completedNodes()).containsExactlyInAnyOrder("a", "b");
            assertThat(result.nodeResults()).containsEntry("a", "分析结果");
            assertThat(result.nodeResults()).containsEntry("b", "最终报告");
        }
    }

    @Nested
    @DisplayName("失败传播")
    class FailurePropagation {

        @Test
        @DisplayName("节点 A 失败时，下游节点 B 自动跳过并标记失败")
        void shouldSkipDependentsOnFailure() {
            AgentDag dag = buildDag("fail-chain",
                    Map.of(
                            "a", node("a", "分析"),
                            "b", nodeWithInput("b", "报告", "a")),
                    Map.of("b", List.of("a")));

            // A 抛异常，B 不会被调用
            when(llmClient.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("LLM 不可用"));

            DagOrchestrationExecutor.DagExecutionResult result = executor.execute(dag, "需求");

            assertThat(result.hasFailure()).isTrue();
            assertThat(result.failedNodes()).contains("a");
            // B 因依赖 A 失败而跳过，也标记为失败
            assertThat(result.failedNodes()).contains("b");
            assertThat(result.completedNodes()).doesNotContain("a", "b");
        }
    }

    @Nested
    @DisplayName("环检测")
    class CycleDetection {

        @Test
        @DisplayName("DAG 存在环时抛 IllegalArgumentException")
        void shouldThrowOnCyclicDag() {
            // A 依赖 B，B 依赖 A → 环
            AgentDag dag = buildDag("cyclic",
                    Map.of(
                            "a", nodeWithInput("a", "A", "b"),
                            "b", nodeWithInput("b", "B", "a")),
                    Map.of(
                            "a", List.of("b"),
                            "b", List.of("a")));

            assertThatThrownBy(() -> executor.execute(dag, "需求"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("环");
        }
    }
}
