package com.njydsz.agent.server.agent;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;

/**
 * {@link AgentFactory} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>按 Agent 类型路由到正确的执行器（CHAT/REACT/RAG/PLAN_EXECUTE/ROUTER）</li>
 *   <li>执行器缓存：同一类型返回同一实例</li>
 *   <li>getDefaultExecutor 返回 ReAct 执行器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("Agent 工厂 AgentFactory 测试")
@ExtendWith(MockitoExtension.class)
class AgentFactoryTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private ConversationMemory memory;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private RagService ragService;
    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private AgentMetrics agentMetrics;
    @Mock
    private CostAnalysisService costAnalysisService;

    private AgentProperties properties;
    private List<InputGuardrail> inputGuardrails;
    private List<OutputGuardrail> outputGuardrails;
    private AgentFactory factory;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        inputGuardrails = List.of();
        outputGuardrails = List.of();
        factory = new AgentFactory(llmClient, memory, toolRegistry, properties,
                inputGuardrails, outputGuardrails, ragService,
                traceRecorder, agentMetrics, costAnalysisService);
    }

    private AgentDefinition definition(AgentDefinition.Type type) {
        return new AgentDefinition("agent-1", "code-1", "name-1", type,
                "system prompt", List.of(), 0.7, 2048, 10, "gpt-4o-mini");
    }

    @Test
    @DisplayName("CHAT 类型 → SimpleAgentExecutor")
    void shouldCreateSimpleAgentForChatType() {
        AgentExecutor executor = factory.getExecutor(definition(AgentDefinition.Type.CHAT));

        assertThat(executor).isInstanceOf(SimpleAgentExecutor.class);
    }

    @Test
    @DisplayName("REACT 类型 → ReActAgentExecutor")
    void shouldCreateReActAgentForReActType() {
        AgentExecutor executor = factory.getExecutor(definition(AgentDefinition.Type.REACT));

        assertThat(executor).isInstanceOf(ReActAgentExecutor.class);
    }

    @Test
    @DisplayName("RAG 类型 → RagAgentExecutor")
    void shouldCreateRagAgentForRagType() {
        AgentExecutor executor = factory.getExecutor(definition(AgentDefinition.Type.RAG));

        assertThat(executor).isInstanceOf(RagAgentExecutor.class);
    }

    @Test
    @DisplayName("PLAN_EXECUTE 类型 → PlanExecuteAgentExecutor")
    void shouldCreatePlanExecuteAgentForPlanExecuteType() {
        AgentExecutor executor = factory.getExecutor(definition(AgentDefinition.Type.PLAN_EXECUTE));

        assertThat(executor).isInstanceOf(PlanExecuteAgentExecutor.class);
    }

    @Test
    @DisplayName("ROUTER 类型 → RouterAgentExecutor")
    void shouldCreateRouterAgentForRouterType() {
        AgentExecutor executor = factory.getExecutor(definition(AgentDefinition.Type.ROUTER));

        assertThat(executor).isInstanceOf(RouterAgentExecutor.class);
    }

    @Test
    @DisplayName("缓存：同一类型多次获取返回同一实例")
    void shouldCacheExecutorByType() {
        AgentDefinition def = definition(AgentDefinition.Type.CHAT);

        AgentExecutor first = factory.getExecutor(def);
        AgentExecutor second = factory.getExecutor(def);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("缓存：不同类型返回不同实例")
    void shouldReturnDifferentInstancesForDifferentTypes() {
        AgentExecutor chatExecutor = factory.getExecutor(definition(AgentDefinition.Type.CHAT));
        AgentExecutor reactExecutor = factory.getExecutor(definition(AgentDefinition.Type.REACT));

        assertThat(chatExecutor).isNotSameAs(reactExecutor);
    }

    @Test
    @DisplayName("getDefaultExecutor → ReActAgentExecutor")
    void shouldReturnReActAsDefault() {
        AgentExecutor executor = factory.getDefaultExecutor();

        assertThat(executor).isInstanceOf(ReActAgentExecutor.class);
    }

    @Test
    @DisplayName("getDefaultExecutor 缓存：多次调用返回同一实例")
    void shouldCacheDefaultExecutor() {
        AgentExecutor first = factory.getDefaultExecutor();
        AgentExecutor second = factory.getDefaultExecutor();

        assertThat(first).isSameAs(second);
    }
}
