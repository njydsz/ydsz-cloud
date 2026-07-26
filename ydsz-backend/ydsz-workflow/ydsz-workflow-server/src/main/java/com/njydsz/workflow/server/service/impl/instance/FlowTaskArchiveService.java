package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.njydsz.workflow.domain.entity.FlowHisTaskDO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务归档服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"任务完成 + 归档"职责。
 * 统一处理：
 * <ul>
 *   <li>将 run_task 表状态置为终态（COMPLETED/REJECTED/SKIPPED/CANCELLED）</li>
 *   <li>将任务记录写入 his_task 表（历史归档）</li>
 *   <li>触发关联的边界事件订阅取消（P0-1）</li>
 * </ul>
 *
 * <p>被 {@code FlowTaskPassService} / {@code FlowTaskRejectService} / 会签策略等复用。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskArchiveService {

    private final FlowRunTaskMapper taskMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    /**
     * P0-1: 事件订阅服务 — 任务完成时取消关联的边界事件订阅。
     * 使用 @Lazy 避免循环依赖。
     */
    @Lazy
    private final FlowEventSubscriptionService eventSubscriptionService;

    /**
     * 完成任务 + 归档到历史表 + 取消边界事件订阅。
     *
     * <p>主流程（如 OR 会签、跳转等）调用此方法一次性完成：状态置为 COMPLETED、
     * 写入历史、取消订阅。注意：本方法会修改 task 的运行时状态（taskStatus/finishAt/durationMs），
     * 调用方传入的 task 对象将被同步更新（用于后续业务判断）。
     *
     * @param task    任务（会被原地修改状态/时间/时长）
     * @param comment 审批意见
     */
    public void completeAndArchive(FlowRunTaskDO task, String comment) {
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.COMPLETED.name(),
                comment, now, durationMs);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        task.setComment(comment);
        task.setFinishAt(now);
        task.setDurationMs(durationMs);
        archiveToHistory(task, FlowTaskStatus.COMPLETED);
        // P0-1: 任务完成后取消关联的边界事件订阅
        try {
            eventSubscriptionService.cancelByTask(task.getId(), "TASK_COMPLETED");
        } catch (Exception e) {
            log.warn("[Flow] 取消事件订阅异常: taskId={} err={}", task.getId(), e.getMessage());
        }
    }

    /**
     * 直接归档到历史表（不修改 run_task 状态，由调用方负责）。
     *
     * <p>用于 reject 场景：调用方已通过 taskMapper.completeTask 写入终态，这里
     * 仅做历史归档。也用于 AUTO_PASS / 自动去重 / 跨节点推进等场景。
     *
     * @param src        源任务
     * @param finalStatus 归档时的最终状态（用于历史表 taskStatus 字段）
     */
    public void archiveToHistory(FlowRunTaskDO src, FlowTaskStatus finalStatus) {
        FlowHisTaskDO his = new FlowHisTaskDO();
        his.setInstanceId(src.getInstanceId());
        his.setTaskId(src.getId());
        his.setFlowCode(src.getFlowCode());
        his.setDefinitionId(src.getDefinitionId());
        his.setNodeCode(src.getNodeCode());
        his.setNodeName(src.getNodeName());
        his.setNodeType(src.getNodeType());
        his.setBusinessType(src.getBusinessType());
        his.setBusinessId(src.getBusinessId());
        his.setBusinessNo(src.getBusinessNo());
        his.setFlowName(src.getFlowName());
        his.setTitle(src.getTitle());
        his.setAssigneeType(src.getAssigneeType());
        his.setAssigneeId(src.getAssigneeId());
        his.setAssigneeName(src.getAssigneeName());
        his.setPerformType(src.getPerformType());
        his.setApproveCount(src.getApproveCount());
        his.setApproveFinished(src.getApproveFinished());
        his.setVotePassRate(src.getVotePassRate());
        his.setTaskStatus(finalStatus.name());
        his.setComment(src.getComment());
        his.setCreatedAt(src.getCreatedAt());
        his.setClaimAt(src.getClaimAt());
        his.setFinishAt(src.getFinishAt());
        his.setDurationMs(src.getDurationMs());
        his.setTenantId(src.getTenantId());
        his.setProviderTraceId(src.getProviderTraceId());
        // GAP-P2-10: 归档保留 iter_var，FOREACH 任务审批历史可追溯
        his.setIterVar(src.getIterVar());
        hisTaskMapper.insert(his);
    }
}
