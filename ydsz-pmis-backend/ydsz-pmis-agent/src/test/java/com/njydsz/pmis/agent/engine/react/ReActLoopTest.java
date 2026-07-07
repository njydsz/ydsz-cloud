package com.njydsz.pmis.agent.engine.react;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import com.njydsz.pmis.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ReAct 推理循环单元测试（P1-2 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>单步成功：LLM 第 1 轮直接返回 final_answer</li>
 *   <li>多步成功：LLM 调用工具后返回 final_answer</li>
 *   <li>LLM 异常 / 空决策 / action=null 时返回失败</li>
 *   <li>工具不存在 / 工具执行异常时 Observation 反馈</li>
 *   <li>达到最大循环次数返回失败</li>
 *   <li>maxSteps <= 0 时使用默认值</li>
 *   <li>system prompt 包含工具清单 + ReAct 格式说明</li>
 *   <li>多轮 user prompt 追加 Observation</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReAct 推理循环测试")
class ReActLoopTest {

    @Mock
    private LlmProviderRouter llmProviderRouter;

    @Mock
    private LlmProvider llmProvider;

    private ToolRegistry toolRegistry;
    private ReActLoop reactLoop;

    @BeforeEach
    void setUp() {
        // 构造空的 ToolRegistry（不依赖 Spring 容器）
        toolRegistry = new ToolRegistry(List.of());
        reactLoop = new ReActLoop(llmProviderRouter, toolRegistry);

        when(llmProviderRouter.active()).thenReturn(llmProvider);
    }

    // ==================== 辅助方法 ====================

    /** 构造 AgentContext */
    private AgentContext ctx() {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("test");
        ctx.setBizId("B001");
        ctx.setBizRef("REF-001");
        ctx.setTraceId("trace-001");
        return ctx;
    }

    /** 构造 final_answer 决策 */
    private ReActDecision finalAnswer(String answer) {
        ReActDecision d = new ReActDecision();
        d.setThought("已得到最终答案");
        d.setAction(ReActLoop.ACTION_FINAL_ANSWER);
        d.setFinalAnswer(answer);
        return d;
    }

    /** 构造工具调用决策 */
    private ReActDecision callTool(String toolName, Map<String, Object> params) {
        ReActDecision d = new ReActDecision();
        d.setThought("调用工具 " + toolName);
        d.setAction(toolName);
        d.setParameters(params);
        return d;
    }

    /** mock LLM 多轮调用返回不同决策 */
    private void mockLlmDecisions(ReActDecision... decisions) {
        if (decisions.length == 1) {
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any())).thenReturn(decisions[0]);
        } else {
            // 注意：thenReturn(T first, T... rest) 支持顺序返回
            ReActDecision first = decisions[0];
            ReActDecision[] rest = new ReActDecision[decisions.length - 1];
            System.arraycopy(decisions, 1, rest, 0, rest.length);
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any())).thenReturn(first, rest);
        }
    }

    /** 构造 mock AgentTool */
    private AgentTool mockTool(String name, ToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn("mock tool " + name);
        when(tool.parameterSchema()).thenReturn(new HashMap<>());
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }

    // ==================== 1. 单步成功测试 ====================

    @Nested
    @DisplayName("单步成功测试")
    class SingleStepSuccessTest {

        @Test
        @DisplayName("LLM 第 1 轮直接返回 final_answer → success=true, steps=1")
        void shouldReturnSuccessWhenLlmGivesFinalAnswerImmediately() {
            mockLlmDecisions(finalAnswer("这是最终答案"));

            ReActResult result = reactLoop.run("你是助手", "你好", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("这是最终答案");
            assertThat(result.getTotalSteps()).isEqualTo(1);
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getAction())
                    .isEqualTo(ReActLoop.ACTION_FINAL_ANSWER);
            assertThat(result.getSteps().get(0).getFinalAnswer())
                    .isEqualTo("这是最终答案");
        }

        @Test
        @DisplayName("baseSystemPrompt=null 时不抛 NPE")
        void shouldNotThrowWhenSystemPromptNull() {
            mockLlmDecisions(finalAnswer("ok"));

            ReActResult result = reactLoop.run(null, "你好", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("ok");
        }
    }

    // ==================== 2. 多步成功测试 ====================

    @Nested
    @DisplayName("多步成功测试")
    class MultiStepSuccessTest {

        @Test
        @DisplayName("LLM 第 1 轮调用工具，第 2 轮返回 final_answer → success=true, steps=2")
        void shouldReturnSuccessAfterToolCall() {
            // 注册 mock 工具
            AgentTool tool = mockTool("query_weather",
                    ToolResult.success("北京今天 25℃ 晴"));
            toolRegistry.register(tool);

            // LLM 第 1 轮调用工具，第 2 轮返回 final_answer
            mockLlmDecisions(
                    callTool("query_weather", Map.of("city", "北京")),
                    finalAnswer("北京今天天气晴，25 度")
            );

            ReActResult result = reactLoop.run("你是天气助手", "北京天气如何？", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("北京今天天气晴，25 度");
            assertThat(result.getTotalSteps()).isEqualTo(2);

            // 验证第 1 步调用了工具
            ReActStep step1 = result.getSteps().get(0);
            assertThat(step1.getAction()).isEqualTo("query_weather");
            assertThat(step1.getObservation()).contains("北京今天 25℃ 晴");
            assertThat(step1.getFinalAnswer()).isNull();

            // 验证第 2 步为 final_answer
            ReActStep step2 = result.getSteps().get(1);
            assertThat(step2.isTerminal()).isTrue();
            assertThat(step2.getFinalAnswer()).isEqualTo("北京今天天气晴，25 度");
        }

        @Test
        @DisplayName("多轮工具调用后最终返回 final_answer（3 步）")
        void shouldHandleMultipleToolCallsBeforeFinalAnswer() {
            AgentTool tool1 = mockTool("step1", ToolResult.success("结果1"));
            AgentTool tool2 = mockTool("step2", ToolResult.success("结果2"));
            toolRegistry.register(tool1);
            toolRegistry.register(tool2);

            mockLlmDecisions(
                    callTool("step1", Map.of()),
                    callTool("step2", Map.of()),
                    finalAnswer("最终答案")
            );

            ReActResult result = reactLoop.run("你是助手", "执行任务", ctx(), 5);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(3);
            assertThat(result.getSteps().get(0).getObservation()).contains("结果1");
            assertThat(result.getSteps().get(1).getObservation()).contains("结果2");
        }

        @Test
        @DisplayName("第 2 轮 user prompt 包含第 1 轮的 Observation")
        void shouldAppendObservationToNextUserPrompt() {
            AgentTool tool = mockTool("echo", ToolResult.success("ECHO_RESULT"));
            toolRegistry.register(tool);

            mockLlmDecisions(
                    callTool("echo", Map.of()),
                    finalAnswer("done")
            );

            reactLoop.run("sys", "user", ctx());

            // 验证第 2 次调用 LLM 时，userPrompt 包含 Observation
            org.mockito.ArgumentCaptor<String> captor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(llmProvider, org.mockito.Mockito.times(2))
                    .chatForJson(anyString(), captor.capture(),
                            eq(ReActDecision.class), any());

            // 第 2 次调用的 userPrompt 应包含 Observation
            String secondCallUserPrompt = captor.getAllValues().get(1);
            assertThat(secondCallUserPrompt).contains("ECHO_RESULT");
            assertThat(secondCallUserPrompt).contains("[步骤 1 观察]");
        }
    }

    // ==================== 3. LLM 异常测试 ====================

    @Nested
    @DisplayName("LLM 异常测试")
    class LlmExceptionTest {

        @Test
        @DisplayName("chatForJson 抛异常时返回失败")
        void shouldReturnFailureWhenLlmThrows() {
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any()))
                    .thenThrow(new RuntimeException("LLM 服务不可用"));

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("LLM 调用失败");
            assertThat(result.getFailureReason()).contains("LLM 服务不可用");
            assertThat(result.getTotalSteps()).isEqualTo(1);
        }

        @Test
        @DisplayName("LLM 返回 null 决策时返回失败")
        void shouldReturnFailureWhenDecisionNull() {
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any())).thenReturn(null);

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("空决策");
        }

        @Test
        @DisplayName("LLM 返回 action=null 决策时返回失败")
        void shouldReturnFailureWhenActionNull() {
            ReActDecision badDecision = new ReActDecision();
            badDecision.setThought("思考中");
            badDecision.setAction(null);
            mockLlmDecisions(badDecision);

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("空决策");
        }

        @Test
        @DisplayName("LLM 第 2 轮抛异常时返回失败，steps=2")
        void shouldReturnFailureWhenLlmThrowsOnSecondCall() {
            AgentTool tool = mockTool("ok", ToolResult.success("ok"));
            toolRegistry.register(tool);

            // 第 1 次成功，第 2 次抛异常
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any()))
                    .thenReturn(callTool("ok", Map.of()))
                    .thenThrow(new RuntimeException("第 2 轮失败"));

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("第 2 轮失败");
            assertThat(result.getTotalSteps()).isEqualTo(2);
        }
    }

    // ==================== 4. 工具异常测试 ====================

    @Nested
    @DisplayName("工具异常测试")
    class ToolExceptionTest {

        @Test
        @DisplayName("工具不存在时将错误信息作为 Observation 反馈，LLM 下一轮成功")
        void shouldFeedbackErrorWhenToolNotExist() {
            // 不注册任何工具，但 LLM 第 1 轮要求调用不存在的工具
            mockLlmDecisions(
                    callTool("nonexistent_tool", Map.of()),
                    finalAnswer("工具不存在，改为直接回答")
            );

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(2);
            // 第 1 步的 Observation 应包含工具不存在的提示
            assertThat(result.getSteps().get(0).getObservation())
                    .contains("不存在");
        }

        @Test
        @DisplayName("工具执行抛异常时将异常信息作为 Observation 反馈")
        void shouldFeedbackErrorWhenToolThrowsException() {
            AgentTool tool = mock(AgentTool.class);
            when(tool.name()).thenReturn("risky_tool");
            when(tool.description()).thenReturn("会抛异常的工具");
            when(tool.parameterSchema()).thenReturn(new HashMap<>());
            when(tool.execute(any(), any()))
                    .thenThrow(new RuntimeException("工具内部错误"));
            toolRegistry.register(tool);

            mockLlmDecisions(
                    callTool("risky_tool", Map.of()),
                    finalAnswer("工具异常，改为直接回答")
            );

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSteps().get(0).getObservation())
                    .contains("执行异常")
                    .contains("工具内部错误");
        }

        @Test
        @DisplayName("工具返回 failure 时将 error 作为 Observation 反馈")
        void shouldFeedbackErrorWhenToolReturnsFailure() {
            AgentTool tool = mockTool("failing_tool", ToolResult.failure("参数不合法"));
            toolRegistry.register(tool);

            mockLlmDecisions(
                    callTool("failing_tool", Map.of()),
                    finalAnswer("工具失败，改为直接回答")
            );

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSteps().get(0).getObservation())
                    .contains("执行失败")
                    .contains("参数不合法");
        }
    }

    // ==================== 5. 最大循环次数测试 ====================

    @Nested
    @DisplayName("最大循环次数测试")
    class MaxStepsTest {

        @Test
        @DisplayName("达到最大循环次数仍未 final_answer → 返回失败")
        void shouldReturnFailureWhenMaxStepsReached() {
            // LLM 一直调用工具，从不返回 final_answer
            AgentTool tool = mockTool("loop_tool", ToolResult.success("obs"));
            toolRegistry.register(tool);

            ReActDecision loopDecision = callTool("loop_tool", Map.of());
            // 每次都返回相同的 loopDecision
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any())).thenReturn(loopDecision);

            ReActResult result = reactLoop.run("sys", "user", ctx(), 3);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("最大循环次数");
            assertThat(result.getTotalSteps()).isEqualTo(3);
        }

        @Test
        @DisplayName("maxSteps=0 时使用默认值 DEFAULT_MAX_STEPS")
        void shouldUseDefaultMaxStepsWhenMaxStepsIsZero() {
            AgentTool tool = mockTool("loop_tool", ToolResult.success("obs"));
            toolRegistry.register(tool);

            ReActDecision loopDecision = callTool("loop_tool", Map.of());
            when(llmProvider.chatForJson(anyString(), anyString(),
                    eq(ReActDecision.class), any())).thenReturn(loopDecision);

            ReActResult result = reactLoop.run("sys", "user", ctx(), 0);

            // 默认 5 步
            assertThat(result.getTotalSteps()).isEqualTo(ReActLoop.DEFAULT_MAX_STEPS);
            assertThat(result.getFailureReason()).contains(String.valueOf(ReActLoop.DEFAULT_MAX_STEPS));
        }

        @Test
        @DisplayName("maxSteps=-1 时使用默认值")
        void shouldUseDefaultMaxStepsWhenNegative() {
            mockLlmDecisions(finalAnswer("done"));

            ReActResult result = reactLoop.run("sys", "user", ctx(), -1);

            // 应在第 1 步成功
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(1);
        }
    }

    // ==================== 6. System Prompt 构建测试 ====================

    @Nested
    @DisplayName("System Prompt 构建测试")
    class SystemPromptTest {

        @Test
        @DisplayName("完整 system prompt 包含业务提示词 + ReAct 格式说明 + 工具清单")
        void shouldBuildFullSystemPromptWithToolList() {
            // 注册一个工具
            AgentTool tool = mockTool("my_tool", ToolResult.success("ok"));
            toolRegistry.register(tool);

            mockLlmDecisions(finalAnswer("done"));

            org.mockito.ArgumentCaptor<String> captor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            reactLoop.run("你是 PMIS 助手", "你好", ctx());

            org.mockito.Mockito.verify(llmProvider)
                    .chatForJson(captor.capture(), anyString(),
                            eq(ReActDecision.class), any());

            String systemPrompt = captor.getValue();
            assertThat(systemPrompt).contains("你是 PMIS 助手");
            assertThat(systemPrompt).contains("ReAct");
            assertThat(systemPrompt).contains("final_answer");
            assertThat(systemPrompt).contains("my_tool");
            assertThat(systemPrompt).contains("可用工具");
        }

        @Test
        @DisplayName("空 ToolRegistry 时 system prompt 仍包含 '无可用工具'")
        void shouldContainNoToolAvailableWhenRegistryEmpty() {
            mockLlmDecisions(finalAnswer("done"));

            org.mockito.ArgumentCaptor<String> captor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            reactLoop.run("sys", "user", ctx());

            org.mockito.Mockito.verify(llmProvider)
                    .chatForJson(captor.capture(), anyString(),
                            eq(ReActDecision.class), any());

            assertThat(captor.getValue()).contains("无可用工具");
        }
    }

    // ==================== 7. 默认 run 方法测试 ====================

    @Nested
    @DisplayName("run() 默认方法测试")
    class DefaultRunTest {

        @Test
        @DisplayName("run(systemPrompt, userPrompt, ctx) 使用默认最大步数")
        void shouldUseDefaultMaxSteps() {
            mockLlmDecisions(finalAnswer("done"));

            ReActResult result = reactLoop.run("sys", "user", ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(1);
        }
    }
}
