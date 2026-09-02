package com.njydsz.workflow.server.engine.listener;

/**
 * 流程监听器事件类型
 *
 * <p>监听器生命周期（CREATE / START / FINISH 等）， 结合 ydsz 已有事件体系扩展。
 *
 * <p>设计器中每个节点可为此事件类型绑定监听器（Spring Bean 名称），引擎在执行到关键节点时自动回调。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowListenerPluginExecutor 监听器执行器
 */
public enum FlowListenerEventType {

  /** 任务创建时（{@code FlowTaskCreatedEvent} 发布之后） */
  TASK_CREATED("taskCreated", "任务创建"),

  /** 任务开始办理时（分派到具体办理人、首次打开审批页） */
  TASK_STARTED("taskStarted", "任务开始办理"),

  /** 任务完成时（通过 / 驳回 / 自动通过，单个任务级完成） */
  TASK_FINISHED("taskFinished", "任务完成"),

  /** 流程实例启动时 */
  INSTANCE_STARTED("instanceStarted", "实例启动"),

  /** 流程实例完成时（所有节点审批通过、到达结束节点） */
  INSTANCE_FINISHED("instanceFinished", "实例完成"),

  /** 流程实例被拒绝 / 驳回到终止时 */
  INSTANCE_REJECTED("instanceRejected", "实例拒绝"),

  /** 流程实例被终止时 */
  INSTANCE_TERMINATED("instanceTerminated", "实例终止"),

  /** 会签中单个办理人完成审批时（全部会签完成前，每通过一人触发一次） */
  TASK_PERSONAL_FINISHED("taskPersonalFinished", "个人审批完成");

  private final String code;
  private final String desc;

  FlowListenerEventType(String code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public String getCode() {
    return code;
  }

  public String getDesc() {
    return desc;
  }

  /**
   * 根据编码解析事件类型，未匹配时返回 {@code null}
   *
   * @param code 事件类型编码
   * @return 对应枚举，无匹配时返回 {@code null}
   */
  public static FlowListenerEventType fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (FlowListenerEventType t : values()) {
      if (t.code.equals(code)) {
        return t;
      }
    }
    return null;
  }
}
