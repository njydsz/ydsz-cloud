package com.njydsz.pmis.agent.hitl;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.react.ReActStep;
import com.njydsz.pmis.agent.enums.HitlApprovalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReActSnapshot 快照单元测试（P3-4 落地）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@DisplayName("ReActSnapshot 快照测试")
class ReActSnapshotTest {

    @Nested
    @DisplayName("工厂方法 of()")
    class FactoryTest {

        @Test
        @DisplayName("正常构造快照")
        void shouldCreateSnapshot() {
            AgentContext ctx = new AgentContext();
            ctx.setTraceId("trace-1");
            ReActStep step = new ReActStep();
            step.setStepIndex(1);

            ReActSnapshot snapshot = ReActSnapshot.of(
                    "system-prompt", "user-prompt", "original",
                    List.of(step), ctx, 5, 2,
                    "thought", "tool_name", Map.of("param", "value"));

            assertThat(snapshot.getBaseSystemPrompt()).isEqualTo("system-prompt");
            assertThat(snapshot.getCurrentUserPrompt()).isEqualTo("user-prompt");
            assertThat(snapshot.getOriginalUserPrompt()).isEqualTo("original");
            assertThat(snapshot.getSteps()).hasSize(1);
            assertThat(snapshot.getAgentContext()).isEqualTo(ctx);
            assertThat(snapshot.getMaxSteps()).isEqualTo(5);
            assertThat(snapshot.getPausedStepIndex()).isEqualTo(2);
            assertThat(snapshot.getPendingThought()).isEqualTo("thought");
            assertThat(snapshot.getPendingToolName()).isEqualTo("tool_name");
            assertThat(snapshot.getPendingParameters()).containsEntry("param", "value");
        }

        @Test
        @DisplayName("null steps 转为空列表")
        void nullStepsBecomesEmptyList() {
            ReActSnapshot snapshot = ReActSnapshot.of(
                    null, null, null, null, null, 0, 1, null, "tool", null);
            assertThat(snapshot.getSteps()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("steps 为防御性拷贝")
        void stepsIsDefensiveCopy() {
            ReActStep step = new ReActStep();
            List<ReActStep> original = List.of(step);

            ReActSnapshot snapshot = ReActSnapshot.of(
                    null, null, null, original, null, 0, 1, null, "tool", null);

            // 修改原列表不影响快照
            assertThat(snapshot.getSteps()).hasSize(1);
            assertThat(snapshot.getSteps()).isNotSameAs(original);
        }
    }

    @Nested
    @DisplayName("审批结果填充")
    class ApprovalTest {

        @Test
        @DisplayName("withApproval 填充审批结果")
        void shouldFillApproval() {
            ReActSnapshot snapshot = ReActSnapshot.of(
                    null, null, null, null, null, 0, 1, null, "tool", null);

            snapshot.withApproval(HitlApprovalStatus.APPROVED, "同意");

            assertThat(snapshot.getApprovalStatus()).isEqualTo(HitlApprovalStatus.APPROVED);
            assertThat(snapshot.getApproverComment()).isEqualTo("同意");
            assertThat(snapshot.hasApproval()).isTrue();
        }

        @Test
        @DisplayName("未填充时 hasApproval 返回 false")
        void noApprovalReturnsFalse() {
            ReActSnapshot snapshot = ReActSnapshot.of(
                    null, null, null, null, null, 0, 1, null, "tool", null);
            assertThat(snapshot.hasApproval()).isFalse();
        }

        @Test
        @DisplayName("REJECTED 审批结果")
        void shouldFillRejection() {
            ReActSnapshot snapshot = ReActSnapshot.of(
                    null, null, null, null, null, 0, 1, null, "tool", null);

            snapshot.withApproval(HitlApprovalStatus.REJECTED, "拒绝：参数不合法");

            assertThat(snapshot.getApprovalStatus()).isEqualTo(HitlApprovalStatus.REJECTED);
            assertThat(snapshot.getApproverComment()).isEqualTo("拒绝：参数不合法");
            assertThat(snapshot.hasApproval()).isTrue();
        }
    }
}
