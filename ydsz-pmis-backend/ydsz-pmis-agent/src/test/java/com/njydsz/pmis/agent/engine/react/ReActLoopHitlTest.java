package com.njydsz.pmis.agent.engine.react;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.memory.ChatMemory;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.engine.prompt.TestPromptRegistryFactory;
import com.njydsz.pmis.agent.enums.HitlApprovalStatus;
import com.njydsz.pmis.agent.hitl.ReActSnapshot;
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
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReAct 推理循环 HITL 暂停/恢复单元测试（P3-4 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>工具 {@code requiresApproval()=true} 时循环暂停，返回 PAUSED 结果</li>
 *   <li>暂停快照包含完整的循环状态（prompt / steps / context / 工具信息）</li>
 *   <li>暂停时工具 {@code execute} 从未被调用</li>
 *   <li>{@code requiresApproval()=false} 的工具正常执行，不暂停</li>
 *   <li>APPROVED 恢复时执行工具并继续循环至 final_answer</li>
 *   <li>REJECTED 恢复时将拒绝意见作为 Observation 反馈给 LLM</li>
 *   <li>resume(null) 抛 IllegalArgumentException</li>
 *   <li>resume 缺少审批结果抛 IllegalStateException</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReAct HITL 暂停/恢复测试")
class ReActLoopHitlTest {

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
        reactLoop = new ReActLoop(llmProviderRouter, toolRegistry, promptRegistry, chatMemoryProvider);
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

    /**
     * mock LLM 顺序返回多个 JSON 决策（与 ReActLoop 的 chat + JSON.parseObject 路径一致）。
     *
     * <p>将 ReActDecision 序列化为 JSON 字符串，按顺序返回。调用超过 decisions
     * 数量时返回 null（触发"空决策"失败分支）。
     */
    private void mockLlmJson(ReActDecision... decisions) {
        Iterator<String> it = Arrays.stream(decisions)
                .map(JSON::toJSONString)
                .iterator();
        when(llmProvider.chat(anyString(), anyString(), any()))
                .thenAnswer(inv -> it.hasNext() ? it.next() : null);
    }

    /** 构造需要审批的 mock 工具 */
    private AgentTool approvalRequiredTool(String name, ToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn("mock approval tool " + name);
        when(tool.parameterSchema()).thenReturn(new HashMap<>());
        when(tool.requiresApproval()).thenReturn(true);
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }

    /** 构造无需审批的 mock 工具 */
    private AgentTool normalTool(String name, ToolResult result) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn("mock tool " + name);
        when(tool.parameterSchema()).thenReturn(new HashMap<>());
        when(tool.requiresApproval()).thenReturn(false);
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }

    // ==================== 1. HITL 暂停测试 ====================

    @Nested
    @DisplayName("HITL 暂停测试")
    class PauseTest {

        @Test
        @DisplayName("requiresApproval=true 的工具导致循环暂停，返回 PAUSED 结果")
        void shouldPauseWhenToolRequiresApproval() {
            AgentTool tool = approvalRequiredTool("send_email",
                    ToolResult.success("邮件已发送"));
            toolRegistry.register(tool);

            mockLlmJson(callTool("send_email", Map.of("to", "test@example.com")));

            ReActResult result = reactLoop.run("sys", "发邮件", ctx());

            assertThat(result.isPaused()).isTrue();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getPausedToolName()).isEqualTo("send_email");
            assertThat(result.getPausedSnapshot()).isNotNull();
            assertThat(result.getPausedSnapshot().getPendingToolName())
                    .isEqualTo("send_email");
            assertThat(result.getPausedSnapshot().getPendingParameters())
                    .containsEntry("to", "test@example.com");
            // 暂停时工具不应被执行
            verify(tool, never()).execute(any(), any());
        }

        @Test
        @DisplayName("requiresApproval=false 的工具正常执行，不暂停")
        void shouldNotPauseWhenToolDoesNotRequireApproval() {
            AgentTool tool = normalTool("query", ToolResult.success("查询结果"));
            toolRegistry.register(tool);

            mockLlmJson(
                    callTool("query", Map.of()),
                    finalAnswer("完成")
            );

            ReActResult result = reactLoop.run("sys", "查询", ctx());

            assertThat(result.isPaused()).isFalse();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalSteps()).isEqualTo(2);
            verify(tool, times(1)).execute(any(), any());
        }

        @Test
        @DisplayName("暂停快照包含完整的循环状态（prompt / context / 工具信息）")
        void shouldContainCompleteSnapshotWhenPaused() {
            AgentTool tool = approvalRequiredTool("dangerous_op", ToolResult.success("done"));
            toolRegistry.register(tool);

            ReActDecision decision = callTool("dangerous_op", Map.of("k", "v"));
            decision.setThought("我需要执行危险操作");
            mockLlmJson(decision);

            ReActResult result = reactLoop.run("业务系统", "执行危险操作", ctx());

            assertThat(result.isPaused()).isTrue();
            ReActSnapshot snapshot = result.getPausedSnapshot();
            assertThat(snapshot.getBaseSystemPrompt()).isEqualTo("业务系统");
            assertThat(snapshot.getOriginalUserPrompt()).isEqualTo("执行危险操作");
            assertThat(snapshot.getPendingToolName()).isEqualTo("dangerous_op");
            assertThat(snapshot.getPendingThought()).isEqualTo("我需要执行危险操作");
            assertThat(snapshot.getPendingParameters()).containsEntry("k", "v");
            assertThat(snapshot.getAgentContext()).isNotNull();
            assertThat(snapshot.getAgentContext().getTraceId()).isEqualTo("trace-001");
            assertThat(snapshot.getPausedStepIndex()).isEqualTo(1);
            assertThat(snapshot.getMaxSteps()).isGreaterThan(0);
            assertThat(snapshot.getCurrentUserPrompt()).isNotBlank();
            assertThat(snapshot.getSteps()).isNotNull();
        }

        @Test
        @DisplayName("暂停结果的 failureReason 包含工具名")
        void shouldContainToolNameInFailureReasonWhenPaused() {
            AgentTool tool = approvalRequiredTool("delete_record",
                    ToolResult.success("已删除"));
            toolRegistry.register(tool);

            mockLlmJson(callTool("delete_record", Map.of()));

            ReActResult result = reactLoop.run("sys", "删除记录", ctx());

            assertThat(result.isPaused()).isTrue();
            assertThat(result.getFailureReason()).contains("delete_record");
            assertThat(result.getFailureReason()).contains("人工审批");
        }
    }

    // ==================== 2. HITL 恢复测试 ====================

    @Nested
    @DisplayName("HITL 恢复测试")
    class ResumeTest {

        @Test
        @DisplayName("APPROVED 恢复时执行工具并继续循环至 final_answer")
        void shouldExecuteToolAndContinueWhenApproved() {
            AgentTool tool = approvalRequiredTool("send_email",
                    ToolResult.success("邮件已发送"));
            toolRegistry.register(tool);

            // 第 1 次循环：暂停
            mockLlmJson(callTool("send_email", Map.of("to", "x@y.com")));

            ReActResult pausedResult = reactLoop.run("sys", "发邮件", ctx());
            assertThat(pausedResult.isPaused()).isTrue();

            // 获取快照并填充审批结果
            ReActSnapshot snapshot = pausedResult.getPausedSnapshot();
            snapshot.withApproval(HitlApprovalStatus.APPROVED, "同意发送");

            // 重新 mock 恢复后的 LLM 决策：返回 final_answer
            mockLlmJson(finalAnswer("邮件已成功发送"));

            ReActResult resumeResult = reactLoop.resume(snapshot);

            assertThat(resumeResult.isSuccess()).isTrue();
            assertThat(resumeResult.getFinalAnswer()).isEqualTo("邮件已成功发送");
            assertThat(resumeResult.isPaused()).isFalse();
            // 验证工具在恢复后被执行了一次
            verify(tool, times(1)).execute(any(), any());
        }

        @Test
        @DisplayName("REJECTED 恢复时将拒绝意见作为 Observation 反馈，工具不执行")
        void shouldFeedbackRejectionWhenRejected() {
            AgentTool tool = approvalRequiredTool("delete_record",
                    ToolResult.success("已删除"));
            toolRegistry.register(tool);

            // 第 1 次循环：暂停
            mockLlmJson(callTool("delete_record", Map.of()));

            ReActResult pausedResult = reactLoop.run("sys", "删除", ctx());
            assertThat(pausedResult.isPaused()).isTrue();

            ReActSnapshot snapshot = pausedResult.getPausedSnapshot();
            snapshot.withApproval(HitlApprovalStatus.REJECTED, "禁止删除");

            // 重新 mock 恢复后的 LLM 决策
            mockLlmJson(finalAnswer("好的，不删除"));

            ReActResult resumeResult = reactLoop.resume(snapshot);

            assertThat(resumeResult.isSuccess()).isTrue();
            assertThat(resumeResult.getFinalAnswer()).isEqualTo("好的，不删除");
            // 验证工具从未被执行
            verify(tool, never()).execute(any(), any());
        }

        @Test
        @DisplayName("APPROVED 恢复时工具执行结果作为 Observation 反馈给 LLM")
        void shouldUseToolResultAsObservationWhenApproved() {
            AgentTool tool = approvalRequiredTool("compute",
                    ToolResult.success("计算结果=42"));
            toolRegistry.register(tool);

            mockLlmJson(callTool("compute", Map.of("x", "1")));

            ReActResult pausedResult = reactLoop.run("sys", "计算", ctx());
            assertThat(pausedResult.isPaused()).isTrue();

            ReActSnapshot snapshot = pausedResult.getPausedSnapshot();
            snapshot.withApproval(HitlApprovalStatus.APPROVED, "同意");

            // 恢复后 LLM 看到 Observation 后给出 final_answer
            mockLlmJson(finalAnswer("答案是 42"));

            ReActResult resumeResult = reactLoop.resume(snapshot);

            assertThat(resumeResult.isSuccess()).isTrue();
            // 恢复后的步骤应包含工具执行结果
            assertThat(resumeResult.getSteps()).isNotEmpty();
            // 暂停步骤的 Observation 应包含工具执行结果（通过 action 定位暂停步骤）
            ReActStep pausedStep = resumeResult.getSteps().stream()
                    .filter(s -> "compute".equals(s.getAction()))
                    .findFirst()
                    .orElse(null);
            assertThat(pausedStep).isNotNull();
            assertThat(pausedStep.getObservation()).contains("计算结果=42");
        }

        @Test
        @DisplayName("REJECTED 恢复时拒绝意见作为 Observation 反馈给 LLM")
        void shouldUseRejectionAsObservationWhenRejected() {
            AgentTool tool = approvalRequiredTool("risk_op",
                    ToolResult.success("done"));
            toolRegistry.register(tool);

            mockLlmJson(callTool("risk_op", Map.of()));

            ReActResult pausedResult = reactLoop.run("sys", "操作", ctx());
            assertThat(pausedResult.isPaused()).isTrue();

            ReActSnapshot snapshot = pausedResult.getPausedSnapshot();
            snapshot.withApproval(HitlApprovalStatus.REJECTED, "风险太高");

            mockLlmJson(finalAnswer("已放弃该操作"));

            ReActResult resumeResult = reactLoop.resume(snapshot);

            assertThat(resumeResult.isSuccess()).isTrue();
            // 暂停步骤的 Observation 应包含拒绝意见（通过 action 定位暂停步骤）
            ReActStep pausedStep = resumeResult.getSteps().stream()
                    .filter(s -> "risk_op".equals(s.getAction()))
                    .findFirst()
                    .orElse(null);
            assertThat(pausedStep).isNotNull();
            assertThat(pausedStep.getObservation()).contains("人工审批拒绝");
            assertThat(pausedStep.getObservation()).contains("风险太高");
        }

        @Test
        @DisplayName("resume(null) 抛 IllegalArgumentException")
        void shouldThrowWhenSnapshotNull() {
            assertThatThrownBy(() -> reactLoop.resume(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("快照不能为空");
        }

        @Test
        @DisplayName("resume 时快照缺少审批结果抛 IllegalStateException")
        void shouldThrowWhenSnapshotHasNoApproval() {
            AgentTool tool = approvalRequiredTool("op", ToolResult.success("done"));
            toolRegistry.register(tool);

            mockLlmJson(callTool("op", Map.of()));

            ReActResult pausedResult = reactLoop.run("sys", "op", ctx());
            assertThat(pausedResult.isPaused()).isTrue();

            ReActSnapshot snapshot = pausedResult.getPausedSnapshot();
            // 不调用 withApproval，缺少审批结果

            assertThatThrownBy(() -> reactLoop.resume(snapshot))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("缺少审批结果");
        }

        @Test
        @DisplayName("resume 后循环可再次暂停（连续审批场景）")
        void shouldSupportConsecutivePauses() {
            AgentTool tool1 = approvalRequiredTool("step1", ToolResult.success("结果1"));
            AgentTool tool2 = approvalRequiredTool("step2", ToolResult.success("结果2"));
            toolRegistry.register(tool1);
            toolRegistry.register(tool2);

            // 第 1 次循环：调用 step1 → 暂停
            mockLlmJson(callTool("step1", Map.of()));

            ReActResult firstPause = reactLoop.run("sys", "执行", ctx(), 5);
            assertThat(firstPause.isPaused()).isTrue();
            assertThat(firstPause.getPausedToolName()).isEqualTo("step1");

            // 批准 step1，恢复后会调用 step2 → 再次暂停
            ReActSnapshot snapshot1 = firstPause.getPausedSnapshot();
            snapshot1.withApproval(HitlApprovalStatus.APPROVED, "同意 step1");

            mockLlmJson(callTool("step2", Map.of()));

            ReActResult secondPause = reactLoop.resume(snapshot1);
            assertThat(secondPause.isPaused()).isTrue();
            assertThat(secondPause.getPausedToolName()).isEqualTo("step2");

            // 批准 step2，恢复后返回 final_answer
            ReActSnapshot snapshot2 = secondPause.getPausedSnapshot();
            snapshot2.withApproval(HitlApprovalStatus.APPROVED, "同意 step2");

            mockLlmJson(finalAnswer("全部完成"));

            ReActResult finalResult = reactLoop.resume(snapshot2);
            assertThat(finalResult.isSuccess()).isTrue();
            assertThat(finalResult.getFinalAnswer()).isEqualTo("全部完成");

            // 验证两个工具各执行一次
            verify(tool1, times(1)).execute(any(), any());
            verify(tool2, times(1)).execute(any(), any());
        }
    }
}
