package com.njydsz.pmis.workflow.listener;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.workflow.engine.FlowEventContext;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.entity.EventOutboxDO;
import com.njydsz.pmis.workflow.service.FlowEventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Outbox 事件写入监听器（P2-1）
 *
 * <p>实现 {@link FlowEventListener}，在关键事件触发时将事件信息写入 outbox 表，
 * 保证与主事务的原子性（同事务内 INSERT，事务回滚则 outbox 也回滚）。
 *
 * <p>优先级最高（@Order(Ordered.HIGHEST_PRECEDENCE)），确保在业务监听器之前写入 outbox，
 * 即使后续监听器抛异常导致事务回滚，outbox 也不会残留脏数据。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class FlowEventOutboxListener implements FlowEventListener {

    private final FlowEventOutboxService outboxService;

    @Override
    public void onTaskCreated(Long taskId) {
        saveOutbox("TASK_CREATED", "WORKFLOW_TASK", taskId, null, taskId, null);
    }

    @Override
    public void onTaskCompleted(Long taskId, String action, Map<String, Object> variables) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        saveOutbox("TASK_COMPLETED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onTaskCompleted(Long taskId, FlowEventContext ctx) {
        Map<String, Object> payload = new HashMap<>();
        if (ctx != null) {
            payload.put("action", ctx.getAction());
            payload.put("operatorId", ctx.getOperatorId());
        }
        saveOutbox("TASK_COMPLETED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onInstanceStart(Long instanceId, Map<String, Object> variables) {
        saveOutbox("INSTANCE_START", "WORKFLOW_INSTANCE", instanceId, instanceId, null, null);
    }

    @Override
    public void onInstanceCompleted(Long instanceId) {
        saveOutbox("INSTANCE_COMPLETED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, null);
    }

    @Override
    public void onInstanceRejected(Long instanceId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        saveOutbox("INSTANCE_REJECTED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, payload);
    }

    @Override
    public void onInstanceTerminated(Long instanceId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        saveOutbox("INSTANCE_TERMINATED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, payload);
    }

    @Override
    public void onInstanceTerminated(Long instanceId, String reason, FlowEventContext ctx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        if (ctx != null) {
            payload.put("operatorId", ctx.getOperatorId());
        }
        saveOutbox("INSTANCE_TERMINATED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, payload);
    }

    @Override
    public void onInstanceSuspended(Long instanceId) {
        saveOutbox("INSTANCE_SUSPENDED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, null);
    }

    @Override
    public void onInstanceActivated(Long instanceId) {
        saveOutbox("INSTANCE_ACTIVATED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, null);
    }

    @Override
    public void onInstanceRecalled(Long instanceId, Long initiatorId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("initiatorId", initiatorId);
        saveOutbox("INSTANCE_RECALLED", "WORKFLOW_INSTANCE", instanceId, instanceId, null, payload);
    }

    @Override
    public void onTaskUrged(Long instanceId, Long taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        saveOutbox("TASK_URGED", "WORKFLOW_TASK", taskId, instanceId, taskId, payload);
    }

    @Override
    public void onTaskTransferred(Long taskId, Long fromUserId, Long toUserId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fromUserId", fromUserId);
        payload.put("toUserId", toUserId);
        saveOutbox("TASK_TRANSFERRED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onTaskDelegated(Long taskId, Long fromUserId, Long toUserId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fromUserId", fromUserId);
        payload.put("toUserId", toUserId);
        saveOutbox("TASK_DELEGATED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onTaskCountersigned(Long taskId, Long targetUserId, String action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetUserId", targetUserId);
        payload.put("action", action);
        saveOutbox("TASK_COUNTERSIGNED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onTaskJumped(Long taskId, String fromNodeCode, String toNodeCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fromNodeCode", fromNodeCode);
        payload.put("toNodeCode", toNodeCode);
        saveOutbox("TASK_JUMPED", "WORKFLOW_TASK", taskId, null, taskId, payload);
    }

    @Override
    public void onTaskTimeout(Long taskId, Long instanceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        saveOutbox("TASK_TIMEOUT", "WORKFLOW_TASK", taskId, instanceId, taskId, payload);
    }

    // ============================== 内部辅助 ==============================

    private void saveOutbox(String eventType, String bizType, Long bizId,
                            Long instanceId, Long taskId, Map<String, Object> extraPayload) {
        try {
            EventOutboxDO event = new EventOutboxDO();
            event.setEventType(eventType);
            event.setBizType(bizType);
            event.setBizId(bizId);
            event.setInstanceId(instanceId);
            event.setTaskId(taskId);
            event.setPayload(extraPayload == null || extraPayload.isEmpty() ? "{}"
                    : JSON.toJSONString(extraPayload));
            // 阶段一：不指定 targetChannels/targetUserIds，由 NotificationClient 根据 eventType 自行路由
            outboxService.saveOutbox(event);
        } catch (Exception e) {
            // outbox 写入失败不应阻塞主流程，降级到日志
            log.error("[Outbox] 事件写入失败（降级到日志）: type={} bizId={} err={}",
                    eventType, bizId, e.getMessage());
        }
    }
}
