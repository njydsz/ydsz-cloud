package com.njydsz.workflow.server.engine;

import java.util.Map;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * 工作流事件（Spring ApplicationEvent 封装）
 *
 * <p>P2-35: 用于异步事件机制，通过 ApplicationEventPublisher 发布，
 * 监听方使用 @EventListener + @Async 异步处理，解耦主流程事务。
 *
 * <p>事件类型（eventType）枚举：
 * <ul>
 *   <li>INSTANCE_TERMINATED / INSTANCE_SUSPENDED / INSTANCE_ACTIVATED / INSTANCE_RECALLED / INSTANCE_COMPLETED</li>
 *   <li>TASK_CREATED / TASK_COMPLETED / TASK_URGED / TASK_TRANSFERRED / TASK_DELEGATED / TASK_COUNTERSIGNED / TASK_JUMPED / TASK_TIMEOUT</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Getter
public class FlowWorkflowEvent extends ApplicationEvent {

    /** 事件类型 */
    private final String eventType;
    /** 流程实例 ID */
    private final String instanceId;
    /** 任务 ID */
    private final String taskId;
    /** 附加数据 */
    private final Map<String, Object> data;

    public FlowWorkflowEvent(Object source, String eventType, String instanceId,
                             String taskId, Map<String, Object> data) {
        super(source);
        this.eventType = eventType;
        this.instanceId = instanceId;
        this.taskId = taskId;
        this.data = data;
    }
}
