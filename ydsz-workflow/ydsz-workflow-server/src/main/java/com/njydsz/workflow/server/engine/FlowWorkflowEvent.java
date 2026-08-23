package com.njydsz.workflow.server.engine;

import java.util.Collections;
import java.util.Map;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 工作流事件（Spring ApplicationEvent）。
 *
 * <p>通过 {@code ApplicationEventPublisher} 发布，监听方使用 {@code @EventListener} + {@code @Async} 异步处理，解耦主流程事务。
 *
 * <p>事件类型（eventType）枚举：
 *
 * <ul>
 *   <li>INSTANCE_TERMINATED / INSTANCE_SUSPENDED / INSTANCE_ACTIVATED / INSTANCE_RECALLED /
 *       INSTANCE_COMPLETED
 *   <li>TASK_CREATED / TASK_COMPLETED / TASK_URGED / TASK_TRANSFERRED / TASK_DELEGATED /
 *       TASK_COUNTERSIGNED / TASK_JUMPED / TASK_TIMEOUT
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Getter
public class FlowWorkflowEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  /** 事件类型 */
  private final String eventType;

  /** 流程实例 ID */
  private final String instanceId;

  /** 任务 ID */
  private final String taskId;

  /** 附加数据 */
  private final Map<String, Object> data;

  /**
   * 构造工作流事件。
   * 
   *
   * @param source 参数说明
   * @param eventType 参数说明
   * @param instanceId 参数说明
   * @param taskId 参数说明
   * @param data 参数说明   */
  public FlowWorkflowEvent(
      Object source, String eventType, String instanceId, String taskId, Map<String, Object> data) {
    super(source);
    this.eventType = eventType;
    this.instanceId = instanceId;
    this.taskId = taskId;
    this.data = data != null ? data : Collections.emptyMap();
  }
}
