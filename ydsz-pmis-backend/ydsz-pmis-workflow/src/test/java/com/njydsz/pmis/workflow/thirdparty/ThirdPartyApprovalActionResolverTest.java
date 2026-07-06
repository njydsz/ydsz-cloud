package com.njydsz.pmis.workflow.thirdparty;

import com.njydsz.pmis.workflow.thirdparty.ThirdPartyApprovalActionResolver.FlowAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ThirdPartyApprovalActionResolver 单元测试
 *
 * <p>P0-2: 三方审批事件 → 工作流动作映射测试。
 * 覆盖钉钉/飞书/企微各事件类型的 PASS/REJECT/WITHDRAW 映射，以及无法映射的边界场景。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("三方审批事件 → 工作流动作映射")
class ThirdPartyApprovalActionResolverTest {

    // ==================== 钉钉 ====================

    @Test
    @DisplayName("钉钉 bpmsTaskChange + taskAction=AGREE → PASS")
    void dingTalkTaskChangeAgreeShouldMapToPass() {
        Map<String, Object> body = new HashMap<>();
        body.put("taskAction", "AGREE");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsTaskChange", body);

        assertThat(action).isEqualTo(FlowAction.PASS);
        assertThat(action.code()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("钉钉 bpmsTaskChange + taskAction=REFUSE → REJECT")
    void dingTalkTaskChangeRefuseShouldMapToReject() {
        Map<String, Object> body = new HashMap<>();
        body.put("taskAction", "REFUSE");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsTaskChange", body);

        assertThat(action).isEqualTo(FlowAction.REJECT);
        assertThat(action.code()).isEqualTo("REJECT");
    }

    @Test
    @DisplayName("钉钉 bpmsTaskChange + 未知 taskAction → null")
    void dingTalkTaskChangeUnknownActionShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("taskAction", "UNKNOWN");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsTaskChange", body);

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("钉钉 bpmsTaskChange + 缺少 taskAction 字段 → null")
    void dingTalkTaskChangeWithoutTaskActionShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsTaskChange", body);

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("钉钉 bpmsInstanceChange（实例级变更）→ null（不映射）")
    void dingTalkInstanceChangeShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("taskAction", "AGREE");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsInstanceChange", body);

        assertThat(action).isNull();
    }

    // ==================== 飞书 ====================

    @Test
    @DisplayName("飞书 approval.approved → PASS")
    void feishuApprovedShouldMapToPass() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "FEISHU", "approval.approved", new HashMap<>());

        assertThat(action).isEqualTo(FlowAction.PASS);
    }

    @Test
    @DisplayName("飞书 approval.rejected → REJECT")
    void feishuRejectedShouldMapToReject() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "FEISHU", "approval.rejected", new HashMap<>());

        assertThat(action).isEqualTo(FlowAction.REJECT);
    }

    @Test
    @DisplayName("飞书 approval.canceled → WITHDRAW（撤销对应发起人撤回）")
    void feishuCanceledShouldMapToWithdraw() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "FEISHU", "approval.canceled", new HashMap<>());

        assertThat(action).isEqualTo(FlowAction.WITHDRAW);
        assertThat(action.code()).isEqualTo("WITHDRAW");
    }

    @Test
    @DisplayName("飞书未知事件类型 → null")
    void feishuUnknownEventShouldReturnNull() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "FEISHU", "approval.cc", new HashMap<>());

        assertThat(action).isNull();
    }

    // ==================== 企微 ====================

    @Test
    @DisplayName("企微 sys_approval_change + status=1 → PASS")
    void weComStatus1ShouldMapToPass() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "1");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "sys_approval_change", body);

        assertThat(action).isEqualTo(FlowAction.PASS);
    }

    @Test
    @DisplayName("企微 sys_approval_change + status=2 → REJECT")
    void weComStatus2ShouldMapToReject() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "2");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "sys_approval_change", body);

        assertThat(action).isEqualTo(FlowAction.REJECT);
    }

    @Test
    @DisplayName("企微 sys_approval_change + status=3 → WITHDRAW")
    void weComStatus3ShouldMapToWithdraw() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "3");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "sys_approval_change", body);

        assertThat(action).isEqualTo(FlowAction.WITHDRAW);
    }

    @Test
    @DisplayName("企微 sys_approval_change + 未知 status → null")
    void weComUnknownStatusShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "99");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "sys_approval_change", body);

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("企微非 sys_approval_change 事件 → null")
    void weComOtherEventShouldReturnNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "1");

        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "other_event", body);

        assertThat(action).isNull();
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("未知平台 → null")
    void unknownPlatformShouldReturnNull() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "UNKNOWN_PLATFORM", "approval.approved", new HashMap<>());

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("平台名称忽略大小写（dingtalk 应能识别）")
    void platformNameShouldBeCaseInsensitive() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "feishu", "approval.approved", new HashMap<>());

        assertThat(action).isEqualTo(FlowAction.PASS);
    }

    @Test
    @DisplayName("null 平台 → null")
    void nullPlatformShouldReturnNull() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                null, "approval.approved", new HashMap<>());

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("null 事件类型 → null")
    void nullEventTypeShouldReturnNull() {
        FlowAction action = ThirdPartyApprovalActionResolver.resolve(
                "FEISHU", null, new HashMap<>());

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("null body（钉钉/企微需要子字段）→ null")
    void nullBodyShouldReturnNull() {
        FlowAction dingTalk = ThirdPartyApprovalActionResolver.resolve(
                "DINGTALK", "bpmsTaskChange", null);
        FlowAction weCom = ThirdPartyApprovalActionResolver.resolve(
                "WECOM", "sys_approval_change", null);

        assertThat(dingTalk).isNull();
        assertThat(weCom).isNull();
    }

    @Test
    @DisplayName("FlowAction 枚举 code() 返回大写名称")
    void flowActionCodeShouldReturnName() {
        assertThat(FlowAction.PASS.code()).isEqualTo("PASS");
        assertThat(FlowAction.REJECT.code()).isEqualTo("REJECT");
        assertThat(FlowAction.WITHDRAW.code()).isEqualTo("WITHDRAW");
    }
}
