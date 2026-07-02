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
    POSITION
}
