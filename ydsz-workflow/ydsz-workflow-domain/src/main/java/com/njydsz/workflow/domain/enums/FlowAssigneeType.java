package com.njydsz.workflow.domain.enums;

/**
 * 办理人类型
 *
 * <p>P1-5: 跨节点办理人去重 — 在 {@code ydsz_flow_node.ext} JSON 中配置 {@code autoDedup: true}
 * 可启用跨节点去重。启用后，同实例下已审批过（his_task 中 task_status=COMPLETED）的办理人
 * 将从当前节点候选办理人中排除；若排除后候选人为空，则自动跳过该节点（记录审计日志）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum FlowAssigneeType {

  /** 指定用户：assignee_id = userId */
  USER("指定用户"),
  /** 角色：assignee_id = roleCode */
  ROLE("指定角色"),
  /** 部门：assignee_id = deptId */
  DEPT("指定部门"),
  /** SpEL 表达式：assignee_id = ${expression}，由 VariableStrategy 解析 */
  SPEL("SpEL 表达式"),
  /** 发起人本人 */
  INITIATOR("发起人本人"),
  /** 直属上级：assignee_id = 发起人的 leader userId */
  LEADER("直属上级"),
  /** 岗位：assignee_id = positionCode */
  POSITION("指定岗位"),
  /** P1-5: 部门负责人：assignee_id = deptId，解析为该部门的负责人 userId */
  DEPT_LEADER("部门负责人"),
  /** P2-38: 发起人自选审批人：assignee_id = 流程变量名（如 self_select:approvers） */
  SELF_SELECT("发起人自选"),
  /** P2-39: 多级上级：assignee_id = 级数（如 multi_leader:3 表示连续 3 级上级） */
  MULTI_LEADER("多级上级"),
  /**
   * P2-2: 分组抢办（对标 flowlong 分组策略）。
   *
   * <p>assignee_id = 分组编码（如 team_code）。系统查询该分组的成员列表，
   * 创建任务后<b>第一个签收</b>的办理人获得处理权，其他成员的任务自动取消。
   * 适用于"客服组接工单"、"抢单"等场景。
   */
  GROUP_CLAIM("分组抢办"),
  /**
   * P2-2: 分组全办（对标 flowlong 分组策略）。
   *
   * <p>assignee_id = 分组编码（如 team_code）。系统查询该分组的成员列表，
   * <b>每位成员</b>都会收到待办，默认使用 PARALLEL 会签（全部通过才推进）。
   * 可通过 {@code performType} 指定其他会签模式。
   */
  GROUP_ALL("分组全办");

  /** 设计器显示名称 */
  private final String desc;

  FlowAssigneeType(String desc) {
    this.desc = desc;
  }

  public String getDesc() {
    return desc;
  }

  /**
   * 根据枚举名称解析（忽略大小写），未匹配时返回 {@code null}
   *
   * @param name 枚举名称
   * @return 对应枚举，无匹配时返回 {@code null}
   */
  public static FlowAssigneeType fromName(String name) {
    if (name == null) {
      return null;
    }
    for (FlowAssigneeType t : values()) {
      if (t.name().equalsIgnoreCase(name)) {
        return t;
      }
    }
    return null;
  }
}
