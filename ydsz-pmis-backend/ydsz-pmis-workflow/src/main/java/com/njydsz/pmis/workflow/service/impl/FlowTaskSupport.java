package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * FlowTask 跨子 Service 共享的辅助方法（任务校验、审计、事件）
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分而来，提供各子 Service 共用的工具方法，
 * 避免代码重复。仅包含被多个子 Service 共享的方法；单一子 Service 专用的私有
 * 方法仍保留在对应子 Service 内部。
 *
 * <p>包含的能力：
 * <ul>
 *   <li>{@link #getTaskOrThrow(Long)} — 按主键查任务，不存在抛 NOT_FOUND</li>
 *   <li>{@link #audit} — 审计日志写入（带/不带意见分类两个重载）</li>
 *   <li>{@link #fireEvent} — 触发事件监听器（吞异常，避免单监听器失败影响主流程）</li>
 *   <li>{@link #publishWorkflowEvent} — 发布 Spring 异步事件</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTaskSupport {

    private final FlowRunTaskMapper taskMapper;
    private final FlowAuditLogMapper auditLogMapper;
    private final List<FlowEventListener> eventListeners;
    /** P2-35: Spring 事件发布器，用于异步事件机制（测试环境可能为 null） */
    private final ApplicationEventPublisher eventPublisher;

    // ============================== 任务校验 ==============================

    /**
     * 按主键查任务，不存在抛 NOT_FOUND。
     *
     * @param id 任务 ID
     * @return 任务 DO
     */
    public FlowRunTaskDO getTaskOrThrow(String id) {
        FlowRunTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_6541ab08", id);
        }
        return task;
    }

    // ============================== 审计日志 ==============================

    /**
     * 写审计日志（无意见分类）。
     */
    public void audit(FlowRunTaskDO task, String action, Long operatorId,
                      Long targetId, String comment) {
        audit(task, action, operatorId, targetId, comment, null);
    }

    /**
     * P2-42: 审计日志写入（带意见分类）
     *
     * @param task        任务
     * @param action      操作类型
     * @param operatorId  操作人 ID
     * @param targetId    目标人 ID
     * @param comment     审批意见
     * @param commentType 意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE
     */
    public void audit(FlowRunTaskDO task, String action, Long operatorId,
                      Long targetId, String comment, String commentType) {
        try {
            FlowAuditLogDO log = new FlowAuditLogDO();
            log.setInstanceId(task.getInstanceId());
            log.setTaskId(task.getId());
            log.setFlowCode(task.getFlowCode());
            log.setBusinessType(task.getBusinessType());
            log.setBusinessId(task.getBusinessId());
            log.setNodeCode(task.getNodeCode());
            log.setNodeName(task.getNodeName());
            log.setAction(action);
            log.setOperatorId(operatorId);
            log.setTargetId(targetId);
            log.setComment(comment);
            log.setCommentType(commentType);
            log.setOperatedAt(LocalDateTime.now());
            log.setTenantId(task.getTenantId());
            log.setProviderTraceId(task.getProviderTraceId());
            auditLogMapper.insert(log);
        } catch (Exception e) {
            FlowTaskSupport.log.warn("[Flow] 审计日志写入失败: {}", e.getMessage());
        }
    }

    // ============================== 事件 ==============================

    /**
     * 触发事件监听器（吞异常，避免单监听器失败影响主流程）。
     *
     * @param action 监听器动作
     * @param taskId 任务 ID（仅用于日志，可空）
     */
    public void fireEvent(Consumer<FlowEventListener> action, Long taskId) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("[Flow] 事件监听器异常: listener={} err={}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * P2-35: 发布 Spring 异步事件（ApplicationEventPublisher 可能为 null，需检查）
     *
     * @param eventType  事件类型
     * @param instanceId 实例 ID（可空）
     * @param taskId     任务 ID（可空）
     */
    public void publishWorkflowEvent(String eventType, String instanceId, Long taskId) {
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, instanceId, taskId, null));
        } catch (Exception e) {
            log.warn("[Flow] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
    }
}
