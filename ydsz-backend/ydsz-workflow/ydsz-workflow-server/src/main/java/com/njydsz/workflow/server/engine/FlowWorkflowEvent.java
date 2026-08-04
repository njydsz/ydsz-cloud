package com.njydsz.workflow.server.engine;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.njydsz.common.domain.event.DomainEvent;

import lombok.Getter;

/**
 * 工作流事件（领域事件封装）。
 *
 * <p>继承 {@link DomainEvent}（→ {@link org.springframework.context.ApplicationEvent}），
 * 通过 {@code ApplicationEventPublisher} 或 {@link com.njydsz.common.domain.event.DomainEventPublisher}
 * 发布，监听方使用 {@code @EventListener} + {@code @Async} 异步处理，解耦主流程事务。
 *
 * <p><b>P2-1</b>：现在继承 {@link DomainEvent}，复用统一的元数据字段（tenantId/userId/traceId），
 * 事件类型常量定义在 {@link com.njydsz.common.domain.event.ModuleEventTypes}。
 *
 * <p>事件类型（eventType）枚举：
 * <ul>
 *   <li>INSTANCE_TERMINATED / INSTANCE_SUSPENDED / INSTANCE_ACTIVATED / INSTANCE_RECALLED / INSTANCE_COMPLETED</li>
 *   <li>TASK_CREATED / TASK_COMPLETED / TASK_URGED / TASK_TRANSFERRED / TASK_DELEGATED / TASK_COUNTERSIGNED / TASK_JUMPED / TASK_TIMEOUT</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Getter
public class FlowWorkflowEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private final String taskId;
    /** 附加数据 */
    private final Map<String, Object> data;

    /**
     * 构造工作流事件。
     *
     * @param eventType   事件类型
     * @param instanceId  流程实例 ID（映射为 aggregateId）
     * @param taskId      任务 ID
     * @param data        附加数据
     */
    public FlowWorkflowEvent(String eventType, String instanceId,
                             String taskId, Map<String, Object> data) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), eventType,
              instanceId, "FlowInstance",
              Collections.emptyMap());
        this.taskId = taskId;
        this.data = data;
    }

    /**
     * 获取流程实例 ID（即 aggregateId，语义别名）。
     *
     * @return 流程实例 ID
     */
    public String getInstanceId() {
        return getAggregateId();
    }
}