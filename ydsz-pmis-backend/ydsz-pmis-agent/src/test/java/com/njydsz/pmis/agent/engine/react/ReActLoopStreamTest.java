package com.njydsz.pmis.agent.engine.react;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.memory.ChatMemory;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.engine.prompt.TestPromptRegistryFactory;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import com.njydsz.pmis.agent.tool.ToolResult;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReActLoop#runStream} 流式版本单元测试（P2-1 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>单步成功：监听器按序收到 STEP_START → THOUGHT → ACTION → FINAL_ANSWER → STEP_END → DONE</li>
 *   <li>多步成功：每步完整回调</li>
 *   <li>LLM 异常：监听器收到 onError + onComplete（失败结果）</li>
 *   <li>listener=null 自动降级为 NoOp，不抛异常</li>
 *   <li>监听器抛异常被捕获，不中断主流程</li>
 *   <li>达到最大循环次数时仍触发 onComplete</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReActLoop.runStream 流式测试")
class ReActLoopStreamTest {

    @Mock
    private LlmProviderRouter llmProviderRouter;

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private ObjectProvider<ChatMemory> chatMemoryProvider;

    private ToolRegistry toolRegistry;
    private ReActLoop reactLoop;
    private PromptTemplateRegistry promptRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry(List.of());
        promptRegistry = TestPromptRegistryFactory.createWithBuiltInDefaults();
        // chatMemoryProvider 默认返回 null（无 ChatMemory），ReActLoop 退化为无状态单轮
        reactLoop = new ReActLoop(llmProviderRouter, toolRegistry, promptRegistry, chatMemoryProvider);
        when(llmProviderRouter.active()).thenReturn(llmProvider);
    }

    // ==================== 辅助方法 ====================

    private AgentContext ctx() {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("test");
        ctx.setBizId("B001");
        ctx.setBizRef("REF-001");
        return ctx;
    }

    private ReActDecision finalAnswer(String answer) {
        ReActDecision d = new ReActDecision();
        d.setThought("已得到最终答案");
        d.setAction(ReActLoop.ACTION_FINAL_ANSWER);
        d.setFinalAnswer(answer);
        return d;
    }

    private ReActDecision callTool(String toolName, Map<String, Object> params) {
        ReActDecision d = new ReActDecision();
        d.setThought("调用工具 " + toolName);
        d.setAction(toolName);
        d.setParameters(params);
        return d;
    }

    private void mockLlmDecisions(ReActDecision... decisions) {
        Iterator<String> it = Arrays.stream(decisions)
                .map(JSON::toJSONString)
                .iterator();
        when(llmProvider.chat(anyString(), anyString(), any()))
                .thenAnswer(inv -> it.hasNext() ? it.next() : null);
    }

    private AgentTool mockTool(String name, ToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn("mock tool " + name);
        when(tool.parameterSchema()).thenReturn(new HashMap<>());
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }

    /** 收集所有触发的事件（用 recording listener） */
    private static class RecordingListener implements ReActEventListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onStepStart(int stepIndex) {
            events.add("STEP_START:" + stepIndex);
        }

        @Override
        public void onThought(int stepIndex, String thought) {
            events.add("THOUGHT:" + stepIndex + ":" + (thought == null ? "" : thought));
        }

        @Override
        public void onAction(int stepIndex, ReActDecision decision) {
            events.add("ACTION:" + stepIndex + ":" + decision.getAction());
        }

        @Override
        public void onObservation(int stepIndex, String observation) {
            events.add("OBSERVATION:" + stepIndex);
        }

        @Override
        public void onFinalAnswer(int stepIndex, String finalAnswer) {
            events.add("FINAL_ANSWER:" + stepIndex);
        }

        @Override
        public void onStepEnd(int stepIndex) {
            events.add("STEP_END:" + stepIndex);
        }

        @Override
        public void onComplete(ReActResult result) {
            events.add("ON_COMPLETE:" + (result == null ? "null" : result.isSuccess()));
        }

        @Override
        public void onError(int stepIndex, Throwable error) {
            events.add("ON_ERROR:" + stepIndex + ":" + error.getClass().getSimpleName());
        }
    }

    // ==================== 测试用例 ====================

    @Nested
    @DisplayName("单步成功场景")
    class SingleStepSuccessTest {

        @Test
        @DisplayName("LLM 第 1 轮直接返回 final_answer，监听器按序收到 6 个回调")
        void shouldTriggerAllCallbacksForSingleStepSuccess() {
            mockLlmDecisions(finalAnswer("最终答案"));
            RecordingListener listener = new RecordingListener();

            ReActResult result = reactLoop.runStream("system", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, listener);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("最终答案");

            // 验证事件顺序
            assertThat(listener.events).containsExactly(
                    "STEP_START:1",
                    "THOUGHT:1:已得到最终答案",
                    "ACTION:1:final_answer",
                    "FINAL_ANSWER:1",
                    "STEP_END:1",
                    "ON_COMPLETE:true");
        }

        @Test
        @DisplayName("run 与 runStream 行为一致（同步版等价于传入 NoOpListener）")
        void runShouldBeEquivalentToRunStreamWithNoOpListener() {
            mockLlmDecisions(finalAnswer("ok"));

            ReActResult syncResult = reactLoop.run("sys", "user", ctx());
            ReActResult streamResult = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, NoOpReActEventListener.getInstance());

            assertThat(streamResult.isSuccess()).isEqualTo(syncResult.isSuccess());
            assertThat(streamResult.getFinalAnswer()).isEqualTo(syncResult.getFinalAnswer());
            assertThat(streamResult.getTotalSteps()).isEqualTo(syncResult.getTotalSteps());
        }
    }

    @Nested
    @DisplayName("多步成功场景")
    class MultiStepSuccessTest {

        @Test
        @DisplayName("LLM 第 1 步调用工具，第 2 步返回 final_answer")
        void shouldTriggerCallbacksForMultiStepSuccess() {
            AgentTool tool = mockTool("query", ToolResult.success("结果"));
            toolRegistry.register(tool);
            mockLlmDecisions(
                    callTool("query", Map.of()),
                    finalAnswer("最终答案"));
            RecordingListener listener = new RecordingListener();

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, listener);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(2);

            // 验证事件序列：第 1 步 + 第 2 步 + 完成
            assertThat(listener.events).contains(
                    "STEP_START:1", "THOUGHT:1:调用工具 query", "ACTION:1:query",
                    "OBSERVATION:1", "STEP_END:1",
                    "STEP_START:2", "THOUGHT:2:已得到最终答案", "ACTION:2:final_answer",
                    "FINAL_ANSWER:2", "STEP_END:2",
                    "ON_COMPLETE:true");
            assertThat(listener.events).hasSize(11);
        }
    }

    @Nested
    @DisplayName("异常场景")
    class ExceptionTest {

        @Test
        @DisplayName("LLM 异常时，监听器收到 onStepEnd + onComplete(失败)")
        void shouldNotifyOnLlmException() {
            when(llmProvider.chat(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("LLM 网络异常"));
            RecordingListener listener = new RecordingListener();

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, listener);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("LLM 调用失败");

            // 验证事件序列
            assertThat(listener.events).contains(
                    "STEP_START:1",
                    "STEP_END:1",
                    "ON_COMPLETE:false");
        }

        @Test
        @DisplayName("LLM 返回 null decision 时触发 onComplete(失败)")
        void shouldNotifyOnNullDecision() {
            when(llmProvider.chat(anyString(), anyString(), any())).thenReturn(null);
            RecordingListener listener = new RecordingListener();

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, listener);

            assertThat(result.isSuccess()).isFalse();
            assertThat(listener.events).contains("STEP_START:1", "ON_COMPLETE:false");
        }

        @Test
        @DisplayName("达到最大循环次数触发 onComplete(失败)")
        void shouldNotifyOnMaxStepsReached() {
            // LLM 始终调用工具，但工具不存在
            mockLlmDecisions(callTool("nonexistent_tool", Map.of()));
            RecordingListener listener = new RecordingListener();

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    2, listener);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("达到最大循环次数");
            // 2 步 + DONE
            assertThat(listener.events).contains(
                    "STEP_START:1", "STEP_END:1",
                    "STEP_START:2", "STEP_END:2",
                    "ON_COMPLETE:false");
        }
    }

    @Nested
    @DisplayName("监听器异常隔离")
    class ListenerExceptionTest {

        @Test
        @DisplayName("监听器回调抛异常不影响主流程")
        void shouldNotInterruptWhenListenerThrows() {
            mockLlmDecisions(finalAnswer("ok"));

            ReActEventListener throwingListener = new ReActEventListener() {
                @Override
                public void onStepStart(int stepIndex) {
                    throw new RuntimeException("listener 故障");
                }

                @Override
                public void onComplete(ReActResult result) {
                    throw new RuntimeException("onComplete 也故障");
                }
            };

            // 不应抛异常
            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, throwingListener);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("null 监听器降级")
    class NullListenerTest {

        @Test
        @DisplayName("listener=null 自动降级为 NoOp，不抛异常")
        void shouldFallbackToNoOpWhenListenerNull() {
            mockLlmDecisions(finalAnswer("ok"));

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFinalAnswer()).isEqualTo("ok");
        }

        @Test
        @DisplayName("maxSteps<=0 时使用默认值")
        void shouldUseDefaultMaxStepsWhenNonPositive() {
            mockLlmDecisions(finalAnswer("ok"));

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    0, NoOpReActEventListener.getInstance());

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Mockito 验证场景")
    class MockitoVerifyTest {

        @Test
        @DisplayName("单步成功至少触发 6 次 listener 回调")
        void shouldTriggerAtLeastSixCallbacks() {
            mockLlmDecisions(finalAnswer("ok"));
            ReActEventListener mockListener = mock(ReActEventListener.class);

            reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, mockListener);

            // 单步成功共触发 6 种回调：onStepStart / onThought / onAction /
            // onFinalAnswer / onStepEnd / onComplete（各 1 次）
            verify(mockListener, times(1)).onStepStart(anyInt());
            verify(mockListener, times(1)).onThought(anyInt(), any());
            verify(mockListener, times(1)).onAction(anyInt(), any());
            verify(mockListener, times(1)).onFinalAnswer(anyInt(), anyString());
            verify(mockListener, times(1)).onStepEnd(anyInt());
            verify(mockListener, times(1)).onComplete(any());
        }

        @Test
        @DisplayName("多步成功（2 步）触发 2 次 onStepStart")
        void shouldTriggerOnStepStartTwiceForTwoSteps() {
            AgentTool tool = mockTool("query", ToolResult.success("ok"));
            toolRegistry.register(tool);
            mockLlmDecisions(
                    callTool("query", Map.of()),
                    finalAnswer("ok"));
            ReActEventListener mockListener = mock(ReActEventListener.class);

            reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, mockListener);

            verify(mockListener, times(2)).onStepStart(anyInt());
            verify(mockListener, times(1)).onObservation(eq(1), anyString());
            verify(mockListener, times(1)).onFinalAnswer(eq(2), anyString());
            verify(mockListener, never()).onError(anyInt(), any());
        }

        @Test
        @DisplayName("LLM 异常触发 onComplete 但不触发 onFinalAnswer")
        void shouldNotTriggerFinalAnswerOnLlmException() {
            when(llmProvider.chat(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("网络异常"));
            ReActEventListener mockListener = mock(ReActEventListener.class);

            ReActResult result = reactLoop.runStream("sys", "user", ctx(),
                    ReActLoop.DEFAULT_MAX_STEPS, mockListener);

            assertThat(result.isSuccess()).isFalse();
            verify(mockListener, never()).onFinalAnswer(anyInt(), any());
            verify(mockListener, times(1)).onComplete(any());

            // 捕获 onComplete 的参数，验证 success=false
            ArgumentCaptor<ReActResult> captor = ArgumentCaptor.forClass(ReActResult.class);
            verify(mockListener).onComplete(captor.capture());
            assertThat(captor.getValue().isSuccess()).isFalse();
        }
    }
}
