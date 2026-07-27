package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowSensitiveMasker;
import com.njydsz.workflow.server.engine.FlowWorkflowEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTaskSupport {

    /** 运行时任务 Mapper，查询/更新任务状态 */
    private final FlowRunTaskMapper taskMapper;
    /** 审计日志 Mapper，写入任务操作审计轨迹 */
    private final FlowAuditLogMapper auditLogMapper;
    /** 事件监听器列表（Spring 自动注入所有实现），处理流程生命周期事件 */
    private final List<FlowEventListener> eventListeners;
    /** P2-35: Spring 事件发布器，用于异步事件机制（测试环境可能为 null） */
    private final ApplicationEventPublisher eventPublisher;
    /** P0-1: 敏感字段脱敏器 */
    private final FlowSensitiveMasker sensitiveMasker;

    // ============================== 任务校验 ==============================

    /**
     * 按主键查任务，不存在抛 NOT_FOUND。
     *
     * @param id 任务 ID
     * @return 任务 DO
     */
    public FlowRunTask getTaskOrThrow(String id) {
        FlowRunTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_6541ab08", id);
        }
        return task;
    }

    // ============================== 审计日志 ==============================

    /**
     * 写审计日志（无意见分类）。
     */
    public void audit(FlowRunTask task, String action, String operatorId,
                      String targetId, String comment) {
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
    public void audit(FlowRunTask task, String action, String operatorId,
                      String targetId, String comment, String commentType) {
        try {
            FlowAuditLog log = new FlowAuditLog();
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
            log.setComment(sensitiveMasker.mask(comment));
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
    public void fireEvent(Consumer<FlowEventListener> action, String taskId) {
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
    public void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new FlowWorkflowEvent(eventType, instanceId, taskId, null));
        } catch (Exception e) {
            log.warn("[Flow] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
    }
}
