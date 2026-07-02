package com.njydsz.pmis.workflow.flow.enums;

/**
 * 办理人类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowAssigneeType {

    /** 指定用户：assignee_id = userId */
    USER,
    /** 角色：assignee_id = roleCode */
    ROLE,
    /** 部门：assignee_id = deptId */
    DEPT,
    /** SpEL 表达式：assignee_id = ${expression}，由 VariableStrategy 解析 */
    SPEL,
    /** 发起人本人 */
    INITIATOR,
    /** 直属上级：assignee_id = 发起人的 leader userId */
    LEADER,
    /** 岗位：assignee_id = positionCode */
    POSITION,
    /** P2-38: 发起人自选审批人：assignee_id = 流程变量名（如 self_select:approvers） */
    SELF_SELECT,
    /** P2-39: 多级上级：assignee_id = 级数（如 multi_leader:3 表示连续 3 级上级） */
    MULTI_LEADER
}
