package com.njydsz.pmis.literule.agent;

import com.njydsz.pmis.literule.ai.LLMClient;
import com.njydsz.pmis.literule.ai.LLMException;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.orchestrator.RuleChain;
import com.njydsz.pmis.literule.orchestrator.RuleChainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ReActAgentExecutor 与 AgentRuleNode 单元测试（P3-5）
 *
 * <p>测试目标：覆盖 ReAct 推理循环（单轮/多轮/工具调用/超时/迭代限制/降级/解析）、
 * AgentRuleNode 集成（evaluate/RuleChain 编排/输出变量写入）。
 * 测试风格参考 {@link com.njydsz.pmis.literule.orchestrator.RuleChainTest}（Mockito 手动 mock）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@DisplayName("ReAct Agent 执行器测试")
class ReActAgentExecutorTest {

    // ==================== 辅助方法 ====================

    /** 创建 mock LLM 客户端 */
    private LLMClient mockLLM() {
        return mock(LLMClient.class);
    }

    /** 创建带 facts 的上下文 */
    private RuleContext context(Map<String, Object> facts) {
        return RuleContext.of(facts);
    }

    // ==================== ReAct 推理循环测试 ====================

    @Nested
    @DisplayName("ReAct 推理循环测试")
    class ReactLoopTest {

        @Test
        @DisplayName("1. 单轮推理 - LLM 直接返回 Final Answer")
        void shouldReturnFinalAnswerInSingleRound() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 这个问题很简单\nFinal Answer: 42");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "生命、宇宙及一切的答案是什么？",
                    Collections.emptyList(), null, 3, 0);

            assertThat(result.getOutput()).isEqualTo("42");
            assertThat(result.getIterations()).isEqualTo(1);
            assertThat(result.isDegraded()).isFalse();
            assertThat(result.getThoughts()).hasSize(1);
        }

        @Test
        @DisplayName("2. 多轮推理 - Thought → Action → Observation → Final Answer")
        void shouldHandleMultiRoundReasoning() {
            LLMClient llm = mockLLM();
            // 第一次返回 Action，第二次返回 Final Answer
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 需要查询数据\nAction: query-rule\nAction Input: {\"q\": \"test\"}")
                    .thenReturn("Thought: 已获得数据\nFinal Answer: 查询完成");

            Function<String, String> toolExecutor = code -> "Observation: 数据为 test";
            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "查询数据",
                    Collections.singletonList("query-rule"), toolExecutor, 3, 0);

            assertThat(result.getOutput()).isEqualTo("查询完成");
            assertThat(result.getIterations()).isEqualTo(2);
            assertThat(result.getThoughts()).hasSize(2);
        }

        @Test
        @DisplayName("3. 工具调用 - Agent 调用其他规则作为工具")
        void shouldCallToolAndUseObservation() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 调用风险检查规则\nAction: RISK_CHECK\nAction Input: {}")
                    .thenReturn("Thought: 风险检查已触发\nFinal Answer: 高风险");

            AtomicInteger toolCallCount = new AtomicInteger(0);
            Function<String, String> toolExecutor = code -> {
                toolCallCount.incrementAndGet();
                assertThat(code).isEqualTo("RISK_CHECK");
                return "规则触发: 预算超支 | 红色预警";
            };

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是风险分析专家", "分析风险",
                    Collections.singletonList("RISK_CHECK"), toolExecutor, 3, 0);

            assertThat(toolCallCount.get()).isEqualTo(1);
            assertThat(result.getOutput()).isEqualTo("高风险");
            assertThat(result.getIterations()).isEqualTo(2);
        }

        @Test
        @DisplayName("4. 超时保护 - LLM 慢于 timeoutMs 时中断")
        void shouldInterruptOnTimeout() {
            LLMClient llm = mockLLM();
            // 模拟 LLM 每次调用耗时 200ms
            when(llm.chatWithHistory(any(), isNull())).thenAnswer(invocation -> {
                Thread.sleep(200);
                return "Thought: 思考中\nAction: tool\nAction Input: {}";
            });

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            // timeoutMs=50，第一次调用（200ms）后就超时
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "测试超时",
                    Collections.singletonList("tool"), null, 10, 50);

            // 超时后中断，未获得 Final Answer
            assertThat(result.getIterations()).isLessThanOrEqualTo(10);
            assertThat(result.getThoughts()).isNotEmpty();
            // 超时中断的 thoughts 应包含超时标记
            assertThat(result.getThoughts()).anyMatch(t -> t.contains("超时"));
        }

        @Test
        @DisplayName("5. 最大迭代次数限制 - 达到上限终止")
        void shouldTerminateAtMaxIterations() {
            LLMClient llm = mockLLM();
            // LLM 始终返回 Action，永不返回 Final Answer
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 继续推理\nAction: tool\nAction Input: {}");

            Function<String, String> toolExecutor = code -> "Observation: 继续";
            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "无限循环测试",
                    Collections.singletonList("tool"), toolExecutor, 3, 0);

            assertThat(result.getIterations()).isEqualTo(3);
            // 未获得 Final Answer，使用最后输出
            assertThat(result.getOutput()).isNotBlank();
            assertThat(result.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("6. LLM 不可用降级 - 返回默认结果")
        void shouldDegradeWhenLLMUnavailable() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenThrow(new LLMException("MOCK", "连接超时"));

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "测试降级",
                    Collections.emptyList(), null, 3, 0);

            assertThat(result.isDegraded()).isTrue();
            assertThat(result.getOutput()).isEqualTo(ReActAgentExecutor.DEGRADED_OUTPUT);
        }

        @Test
        @DisplayName("6.1 LLM 客户端为 null - 直接降级")
        void shouldDegradeWhenLLMClientNull() {
            ReActAgentExecutor executor = new ReActAgentExecutor(null);
            ReActAgentExecutor.AgentExecutionResult result = executor.execute(
                    "你是助手", "测试 null LLM",
                    Collections.emptyList(), null, 3, 0);

            assertThat(result.isDegraded()).isTrue();
            assertThat(result.getOutput()).isEqualTo(ReActAgentExecutor.DEGRADED_OUTPUT);
            assertThat(result.getIterations()).isEqualTo(0);
        }
    }

    // ==================== Action 解析测试 ====================

    @Nested
    @DisplayName("Action 解析测试")
    class ActionParseTest {

        @Test
        @DisplayName("7. Action 解析 - 格式正确")
        void shouldParseWellFormedAction() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            String llmOutput = "Thought: 需要查询\nAction: query-rule\nAction Input: {\"key\": \"value\"}";

            ReActAgentExecutor.ActionParseResult action = executor.parseAction(llmOutput);
            assertThat(action).isNotNull();
            assertThat(action.toolName).isEqualTo("query-rule");
            assertThat(action.toolInput).isEqualTo("{\"key\": \"value\"}");
        }

        @Test
        @DisplayName("7.1 Action 解析 - 缺少 Action Input 时默认为 {}")
        void shouldDefaultActionInputToEmptyJson() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            String llmOutput = "Thought: 需要查询\nAction: query-rule";

            ReActAgentExecutor.ActionParseResult action = executor.parseAction(llmOutput);
            assertThat(action).isNotNull();
            assertThat(action.toolName).isEqualTo("query-rule");
            assertThat(action.toolInput).isEqualTo("{}");
        }

        @Test
        @DisplayName("7.2 Action 解析 - 格式错误（无 Action）返回 null")
        void shouldReturnNullForMalformedAction() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            String llmOutput = "Thought: 我不知道该做什么";

            ReActAgentExecutor.ActionParseResult action = executor.parseAction(llmOutput);
            assertThat(action).isNull();
        }

        @Test
        @DisplayName("7.3 Final Answer 解析 - 大小写不敏感")
        void shouldParseFinalAnswerCaseInsensitive() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            String llmOutput = "Thought: 完成\nfinal answer: 结果是 42";

            String answer = executor.parseFinalAnswer(llmOutput);
            assertThat(answer).isEqualTo("结果是 42");
        }

        @Test
        @DisplayName("7.4 Final Answer 解析 - 不存在时返回 null")
        void shouldReturnNullWhenNoFinalAnswer() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            String answer = executor.parseFinalAnswer("Thought: 继续思考");
            assertThat(answer).isNull();
        }
    }

    // ==================== AgentRuleNode 集成测试 ====================

    @Nested
    @DisplayName("AgentRuleNode 集成测试")
    class AgentRuleNodeIntegrationTest {

        @Test
        @DisplayName("8. AgentRuleNode evaluate - 集成测试")
        void shouldEvaluateAgentRuleNode() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 分析完成\nFinal Answer: 项目风险等级为黄色");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-risk", "风险分析Agent",
                    "你是项目风险分析专家",
                    "分析项目 ${projectName} 的风险",
                    3, Collections.emptyList(),
                    "riskResult", 5000L,
                    executor, null);

            Map<String, Object> facts = new HashMap<>();
            facts.put("projectName", "测试项目");
            RuleContext ctx = context(facts);

            RuleResult result = agentNode.evaluate(ctx);

            assertThat(result.isTriggered()).isTrue();
            assertThat(result.getRuleCode()).isEqualTo("agent-risk");
            assertThat(result.getCategory()).isEqualTo("AGENT");
            assertThat(result.getSeverity()).isEqualTo(RuleSeverity.INFO);
            assertThat(result.getDescription()).isEqualTo("项目风险等级为黄色");
        }

        @Test
        @DisplayName("8.1 AgentRuleNode 模板渲染 - ${var} 变量替换")
        void shouldRenderTemplateVariables() {
            LLMClient llm = mockLLM();
            // 捕获传入的 userPrompt，验证模板已渲染
            when(llm.chatWithHistory(any(), isNull())).thenAnswer(invocation -> {
                List<Map<String, String>> messages = invocation.getArgument(0);
                // 第二条消息是 user，验证内容
                String userContent = messages.get(1).get("content");
                assertThat(userContent).contains("测试项目");
                assertThat(userContent).doesNotContain("${projectName}");
                return "Final Answer: OK";
            });

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-1", "测试Agent", "你是助手",
                    "分析项目 ${projectName}（预算 ${budget}）",
                    1, null, null, 0L, executor, null);

            Map<String, Object> facts = new HashMap<>();
            facts.put("projectName", "测试项目");
            facts.put("budget", 10000);
            agentNode.evaluate(context(facts));
        }

        @Test
        @DisplayName("8.2 AgentRuleNode 执行器为 null - 降级")
        void shouldDegradeWhenExecutorNull() {
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-1", "测试Agent", "你是助手", "测试",
                    1, null, null, 0L, null, null);

            RuleResult result = agentNode.evaluate(context(new HashMap<>()));

            assertThat(result.isTriggered()).isTrue();
            assertThat(result.getDescription()).isEqualTo(ReActAgentExecutor.DEGRADED_OUTPUT);
            assertThat(result.getTitle()).isEqualTo("Agent 降级");
        }

        @Test
        @DisplayName("9. AgentRuleNode 在 RuleChain 中执行")
        void shouldExecuteAgentInRuleChain() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 完成\nFinal Answer: 链中执行结果");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-chain", "链中Agent", "你是助手", "执行",
                    1, null, null, 0L, executor, null);

            // 通过 RuleChain.agent 工厂创建 AGENT 链
            RuleChain chain = RuleChain.agent(agentNode);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.AGENT);

            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isTriggered()).isTrue();
            assertThat(results.get(0).getDescription()).isEqualTo("链中执行结果");
            assertThat(results.get(0).getRuleCode()).isEqualTo("agent-chain");
        }

        @Test
        @DisplayName("9.1 AgentRuleNode 嵌入 THEN 链执行")
        void shouldExecuteAgentInThenChain() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Final Answer: Agent 结果");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-then", "THEN链Agent", "你是助手", "执行",
                    1, null, null, 0L, executor, null);

            // AgentRuleNode 实现 Rule，可直接放入 THEN 链
            RuleChain chain = RuleChain.then(agentNode);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDescription()).isEqualTo("Agent 结果");
        }

        @Test
        @DisplayName("10. 输出变量写入 context - 通过 expressionCache")
        void shouldWriteOutputToContext() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Thought: 完成\nFinal Answer: 输出值123");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-out", "输出Agent", "你是助手", "执行",
                    1, null, "agentOutput", 0L, executor, null);

            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = context(facts);

            agentNode.evaluate(ctx);

            // 输出应写入 expressionCache（因 RuleContext.facts 不可变）
            Object output = ctx.getExpressionCache().get("agentOutput");
            assertThat(output).isEqualTo("输出值123");
        }

        @Test
        @DisplayName("10.1 无 outputVariable 时不写入 context")
        void shouldNotWriteWhenNoOutputVariable() {
            LLMClient llm = mockLLM();
            when(llm.chatWithHistory(any(), isNull()))
                    .thenReturn("Final Answer: 结果");

            ReActAgentExecutor executor = new ReActAgentExecutor(llm);
            AgentRuleNode agentNode = new AgentRuleNode(
                    "agent-noout", "无输出Agent", "你是助手", "执行",
                    1, null, null, 0L, executor, null);

            RuleContext ctx = context(new HashMap<>());
            agentNode.evaluate(ctx);

            assertThat(ctx.getExpressionCache()).isEmpty();
        }
    }

    // ==================== AgentRuleNodeFactory 测试 ====================

    @Nested
    @DisplayName("AgentRuleNodeFactory 测试")
    class FactoryTest {

        @Test
        @DisplayName("create - 快速创建无工具 Agent")
        void shouldCreateAgentWithoutTools() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            AgentRuleNodeFactory factory = new AgentRuleNodeFactory(executor);

            AgentRuleNode node = factory.create("测试Agent", "你是助手", "分析 ${data}");
            assertThat(node.getAgentName()).isEqualTo("测试Agent");
            assertThat(node.getSystemPrompt()).isEqualTo("你是助手");
            assertThat(node.getUserPromptTemplate()).isEqualTo("分析 ${data}");
            assertThat(node.getExecutor()).isEqualTo(executor);
            assertThat(node.getMaxIterations()).isEqualTo(3);
            assertThat(node.getTimeoutMs()).isEqualTo(5000L);
            assertThat(node.getTools()).isEmpty();
        }

        @Test
        @DisplayName("createWithTools - 带工具创建")
        void shouldCreateAgentWithTools() {
            ReActAgentExecutor executor = new ReActAgentExecutor(mockLLM());
            AgentRuleNodeFactory factory = new AgentRuleNodeFactory(executor);

            List<String> tools = Arrays.asList("RISK_CHECK", "BUDGET_CHECK");
            AgentRuleNode node = factory.createWithTools("风险Agent", "你是专家", "分析", tools);

            assertThat(node.getTools()).containsExactly("RISK_CHECK", "BUDGET_CHECK");
            assertThat(node.getExecutor()).isEqualTo(executor);
        }
    }
}
