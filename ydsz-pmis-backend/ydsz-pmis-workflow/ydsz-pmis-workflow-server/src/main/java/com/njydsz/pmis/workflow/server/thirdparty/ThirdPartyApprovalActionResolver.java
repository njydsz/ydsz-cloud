package com.njydsz.pmis.workflow.server.thirdparty;

import com.njydsz.pmis.workflow.domain.enums.ThirdPartyPlatform;

import java.util.Map;

/**
 * 三方审批事件 → 工作流动作映射工具
 *
 * <p>P0-2: 三方审批回调驱动工作流的核心映射逻辑。
 *
 * <p>映射规则：
 * <ul>
 *   <li>钉钉 {@code bpmsTaskChange}：根据 {@code taskAction} 区分
 *     <ul>
 *       <li>{@code AGREE} → PASS</li>
 *       <li>{@code REFUSE} → REJECT</li>
 *     </ul>
 *   </li>
 *   <li>飞书：
 *     <ul>
 *       <li>{@code approval.approved} → PASS</li>
 *       <li>{@code approval.rejected} → REJECT</li>
 *       <li>{@code approval.canceled} → WITHDRAW（三方撤销对应发起人撤回）</li>
 *     </ul>
 *   </li>
 *   <li>企微 {@code sys_approval_change}：根据 {@code status} 区分
 *     <ul>
 *       <li>{@code 1}（审批通过）→ PASS</li>
 *       <li>{@code 2}（审批驳回）→ REJECT</li>
 *       <li>{@code 3}（审批撤销）→ WITHDRAW</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>无法映射的事件返回 {@code null}（如钉钉 bpmsInstanceChange 实例级变更、
 * 飞书抄送事件等），由调用方决定是否忽略。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class ThirdPartyApprovalActionResolver {

    /** 钉钉任务变更事件类型 */
    public static final String DINGTALK_EVENT_TASK_CHANGE = "bpmsTaskChange";
    /** 钉钉实例变更事件类型（暂不映射） */
    public static final String DINGTALK_EVENT_INSTANCE_CHANGE = "bpmsInstanceChange";

    /** 飞书审批通过 */
    public static final String FEISHU_EVENT_APPROVED = "approval.approved";
    /** 飞书审批驳回 */
    public static final String FEISHU_EVENT_REJECTED = "approval.rejected";
    /** 飞书审批撤销 */
    public static final String FEISHU_EVENT_CANCELED = "approval.canceled";

    /** 企微审批变更事件类型 */
    public static final String WECOM_EVENT_APPROVAL_CHANGE = "sys_approval_change";

    /** 企微审批状态：通过 */
    public static final String WECOM_STATUS_PASS = "1";
    /** 企微审批状态：驳回 */
    public static final String WECOM_STATUS_REJECT = "2";
    /** 企微审批状态：撤销 */
    public static final String WECOM_STATUS_CANCEL = "3";

    /** 钉钉任务动作：同意 */
    public static final String DINGTALK_TASK_ACTION_AGREE = "AGREE";
    /** 钉钉任务动作：拒绝 */
    public static final String DINGTALK_TASK_ACTION_REFUSE = "REFUSE";

    private ThirdPartyApprovalActionResolver() {
    }

    /**
     * 工作流动作枚举（与 {@code EmbeddedApprovalActionDTO.action} 字段值对应）
     */
    public enum FlowAction {
        /** 通过 */
        PASS,
        /** 驳回 */
        REJECT,
        /** 撤回（对应三方 CANCEL/撤销） */
        WITHDRAW;

        /**
         * 转为 EmbeddedApprovalActionDTO.action 字段值
         */
        public String code() {
            return name();
        }
    }

    /**
     * 解析三方事件为工作流动作
     *
     * @param platform  平台（DINGTALK/FEISHU/WECOM）
     * @param eventType 事件类型
     * @param body      回调数据（用于读取钉钉 taskAction / 企微 status 等子字段）
     * @return 工作流动作，无法映射返回 {@code null}
     */
    public static FlowAction resolve(String platform, String eventType, Map<String, Object> body) {
        ThirdPartyPlatform p = ThirdPartyPlatform.ofName(platform);
        if (p == null || eventType == null) {
            return null;
        }
        switch (p) {
            case DINGTALK:
                return resolveDingTalk(eventType, body);
            case FEISHU:
                return resolveFeishu(eventType);
            case WECOM:
                return resolveWeCom(eventType, body);
            default:
                return null;
        }
    }

    /**
     * 钉钉事件映射
     *
     * <p>仅处理任务级变更（bpmsTaskChange）；实例级变更（bpmsInstanceChange）不映射。
     */
    private static FlowAction resolveDingTalk(String eventType, Map<String, Object> body) {
        if (!DINGTALK_EVENT_TASK_CHANGE.equals(eventType)) {
            return null;
        }
        String taskAction = mapStr(body, "taskAction");
        if (DINGTALK_TASK_ACTION_AGREE.equals(taskAction)) {
            return FlowAction.PASS;
        }
        if (DINGTALK_TASK_ACTION_REFUSE.equals(taskAction)) {
            return FlowAction.REJECT;
        }
        return null;
    }

    /**
     * 飞书事件映射（事件类型即动作）
     */
    private static FlowAction resolveFeishu(String eventType) {
        switch (eventType) {
            case FEISHU_EVENT_APPROVED:
                return FlowAction.PASS;
            case FEISHU_EVENT_REJECTED:
                return FlowAction.REJECT;
            case FEISHU_EVENT_CANCELED:
                return FlowAction.WITHDRAW;
            default:
                return null;
        }
    }

    /**
     * 企微事件映射（根据 status 字段区分）
     */
    private static FlowAction resolveWeCom(String eventType, Map<String, Object> body) {
        if (!WECOM_EVENT_APPROVAL_CHANGE.equals(eventType)) {
            return null;
        }
        String status = mapStr(body, "status");
        if (WECOM_STATUS_PASS.equals(status)) {
            return FlowAction.PASS;
        }
        if (WECOM_STATUS_REJECT.equals(status)) {
            return FlowAction.REJECT;
        }
        if (WECOM_STATUS_CANCEL.equals(status)) {
            return FlowAction.WITHDRAW;
        }
        return null;
    }

    /**
     * 安全地从 Map 中读取字符串值
     */
    private static String mapStr(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
