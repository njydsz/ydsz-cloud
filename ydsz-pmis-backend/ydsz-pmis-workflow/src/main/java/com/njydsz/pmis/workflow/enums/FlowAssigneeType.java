package com.njydsz.pmis.workflow.enums;

/**
 * 办理人类型
 *
 * <p>P1-5: 跨节点办理人去重 — 在 {@code pmis_flow_node.ext} JSON 中配置 {@code autoDedup: true}
 * 可启用跨节点去重。启用后，同实例下已审批过（his_task 中 task_status=COMPLETED）的办理人
 * 将从当前节点候选办理人中排除；若排除后候选人为空，则自动跳过该节点（记录审计日志）。
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
    /** P1-5: 部门负责人：assignee_id = deptId，解析为该部门的负责人 userId */
    DEPT_LEADER,
    /** P2-38: 发起人自选审批人：assignee_id = 流程变量名（如 self_select:approvers） */
    SELF_SELECT,
    /** P2-39: 多级上级：assignee_id = 级数（如 multi_leader:3 表示连续 3 级上级） */
    MULTI_LEADER
}
