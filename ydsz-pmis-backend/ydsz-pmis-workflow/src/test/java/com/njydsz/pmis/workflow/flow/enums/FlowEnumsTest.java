package com.njydsz.pmis.workflow.flow.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowInstanceStatus / FlowTaskStatus / FlowNodeType 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("工作流枚举单元测试")
class FlowEnumsTest {

    @Test
    @DisplayName("FlowInstanceStatus.isFinished 终态判断")
    void testInstanceStatusFinished() {
        assertThat(FlowInstanceStatus.COMPLETED.isFinished()).isTrue();
        assertThat(FlowInstanceStatus.TERMINATED.isFinished()).isTrue();
        assertThat(FlowInstanceStatus.REJECTED.isFinished()).isTrue();
        assertThat(FlowInstanceStatus.RUNNING.isFinished()).isFalse();
        assertThat(FlowInstanceStatus.SUSPENDED.isFinished()).isFalse();
    }

    @Test
    @DisplayName("FlowTaskStatus.isFinished 终态判断")
    void testTaskStatusFinished() {
        assertThat(FlowTaskStatus.COMPLETED.isFinished()).isTrue();
        assertThat(FlowTaskStatus.REJECTED.isFinished()).isTrue();
        assertThat(FlowTaskStatus.SKIPPED.isFinished()).isTrue();
        assertThat(FlowTaskStatus.CANCELLED.isFinished()).isTrue();
        assertThat(FlowTaskStatus.TIMEOUT.isFinished()).isTrue();
        assertThat(FlowTaskStatus.PENDING.isFinished()).isFalse();
        assertThat(FlowTaskStatus.CLAIMED.isFinished()).isFalse();
        // P2-18: FROZEN 不是终态（可解冻回 PENDING）
        assertThat(FlowTaskStatus.FROZEN.isFinished()).isFalse();
        assertThat(FlowTaskStatus.DELEGATED.isFinished()).isFalse();
    }

    @Test
    @DisplayName("FlowNodeType 编码正确")
    void testNodeTypeCodes() {
        assertThat(FlowNodeType.START.getCode()).isEqualTo(0);
        assertThat(FlowNodeType.APPROVAL.getCode()).isEqualTo(1);
        assertThat(FlowNodeType.CC.getCode()).isEqualTo(2);
        assertThat(FlowNodeType.CONDITION.getCode()).isEqualTo(3);
        assertThat(FlowNodeType.PARALLEL.getCode()).isEqualTo(4);
        assertThat(FlowNodeType.INCLUSIVE.getCode()).isEqualTo(5);
        assertThat(FlowNodeType.END.getCode()).isEqualTo(6);
        assertThat(FlowNodeType.SUBPROCESS.getCode()).isEqualTo(7);
    }

    @Test
    @DisplayName("FlowSkipType 类型正确")
    void testSkipTypeNames() {
        assertThat(FlowSkipType.PASS.name()).isEqualTo("PASS");
        assertThat(FlowSkipType.REJECT.name()).isEqualTo("REJECT");
        assertThat(FlowSkipType.FORWARD.name()).isEqualTo("FORWARD");
        assertThat(FlowSkipType.BACK.name()).isEqualTo("BACK");
    }

    @Test
    @DisplayName("FlowAssigneeType 枚举值完整")
    void testAssigneeTypeValues() {
        // P2-38/P2-39: 新增 SELF_SELECT 和 MULTI_LEADER 追加到末尾，不破坏已有枚举序号
        assertThat(FlowAssigneeType.values())
                .contains(FlowAssigneeType.USER, FlowAssigneeType.ROLE,
                        FlowAssigneeType.DEPT, FlowAssigneeType.SPEL,
                        FlowAssigneeType.INITIATOR, FlowAssigneeType.LEADER,
                        FlowAssigneeType.POSITION,
                        FlowAssigneeType.SELF_SELECT, FlowAssigneeType.MULTI_LEADER);
        // SELF_SELECT 名称正确
        assertThat(FlowAssigneeType.SELF_SELECT.name()).isEqualTo("SELF_SELECT");
        // MULTI_LEADER 名称正确
        assertThat(FlowAssigneeType.MULTI_LEADER.name()).isEqualTo("MULTI_LEADER");
    }

    @Test
    @DisplayName("P2-38: FlowAssigneeType.SELF_SELECT 枚举存在且可序列化")
    void testSelfSelectEnum() {
        assertThat(FlowAssigneeType.valueOf("SELF_SELECT"))
                .isEqualTo(FlowAssigneeType.SELF_SELECT);
        assertThat(FlowAssigneeType.SELF_SELECT.ordinal())
                .isGreaterThan(FlowAssigneeType.POSITION.ordinal());
    }

    @Test
    @DisplayName("P2-39: FlowAssigneeType.MULTI_LEADER 枚举存在且可序列化")
    void testMultiLeaderEnum() {
        assertThat(FlowAssigneeType.valueOf("MULTI_LEADER"))
                .isEqualTo(FlowAssigneeType.MULTI_LEADER);
        assertThat(FlowAssigneeType.MULTI_LEADER.ordinal())
                .isGreaterThan(FlowAssigneeType.SELF_SELECT.ordinal());
    }
}
