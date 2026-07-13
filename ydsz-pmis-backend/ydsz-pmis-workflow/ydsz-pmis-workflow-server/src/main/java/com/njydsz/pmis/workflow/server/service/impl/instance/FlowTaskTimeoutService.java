package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 任务超时/挂起/激活服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"任务生命周期状态切换"职责。
 * 集中处理：
 * <ul>
 *   <li>{@link #timeoutTask} — 超时标记（P2-36 引入 onTaskTimeout 事件）</li>
 *   <li>{@link #suspendTask} — 任务级挂起（P2-1）</li>
 *   <li>{@link #activateTask} — 任务级激活（P2-1）</li>
 *   <li>{@link #cancelByInstance} — 取消某实例全部 PENDING 任务（终止/驳回终态使用）</li>
 * </ul>
 *
 * <p>这四个方法都不推进流程，仅做任务状态切换；超时和挂起触发对应事件，挂起期
 * 间 JobScanner 应跳过 SUSPENDED 状态任务。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskTimeoutService {

    private final FlowRunTaskMapper taskMapper;
    private final FlowTaskSupport support;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    /**
     * 标记任务为 TIMEOUT 状态。
     *
     * <p>任务状态必须为 PENDING/CLAIMED，否则抛 BAD_REQUEST。完成后写审计日志、
     * 触发 onTaskTimeout 事件、累计指标。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutTask(String taskId, String reason) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        String status = task.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_ecc09732", status);
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.TIMEOUT.name(),
                reason, now, durationMs);
        task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
        task.setComment(reason);
        task.setFinishAt(now);
        task.setDurationMs(durationMs);
        support.audit(task, "TIMEOUT", null, null, reason);
        log.info("[Flow] 任务超时: taskId={} reason={}", taskId, reason);
        if (flowMetrics != null) {
            flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "TIMEOUT");
        }
        // P2-36: 触发 onTaskTimeout 事件
        support.fireEvent(l -> l.onTaskTimeout(task.getId(), task.getInstanceId()), task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_TIMEOUT", task.getInstanceId(), task.getId());
    }

    /**
     * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
     *
     * <p>仅修改任务状态，不推进流程、不取消其它任务。挂起期间不计超时
     * （JobScanner 应跳过 SUSPENDED）。激活后回到 PENDING，需重新签收。
     */
    @Transactional(rollbackFor = Exception.class)
    public void suspendTask(String taskId, String operatorId, String reason) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        String status = task.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_d0e1f2a3", status);
        }
        task.setTaskStatus(FlowTaskStatus.SUSPENDED.name());
        task.setComment(reason);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "SUSPEND", operatorId, null, reason);
        log.info("[Flow] 任务挂起: taskId={} operator={} reason={}", taskId, operatorId, reason);
    }

    /**
     * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
     *
     * <p>激活后清空签收人（assigneeId/assigneeName），需重新签收。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateTask(String taskId, String operatorId) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        String status = task.getTaskStatus();
        if (!FlowTaskStatus.SUSPENDED.name().equals(status)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_e1f2a3b4", status);
        }
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId(null);
        task.setAssigneeName(null);
        task.setClaimAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "ACTIVATE", operatorId, null, null);
        log.info("[Flow] 任务激活: taskId={} operator={}", taskId, operatorId);
    }

    /**
     * 取消某实例的全部 PENDING 任务（终止/驳回终态时使用）
     */
    public void cancelByInstance(String instanceId, String taskStatus) {
        taskMapper.cancelByInstance(instanceId, taskStatus);
    }
}
