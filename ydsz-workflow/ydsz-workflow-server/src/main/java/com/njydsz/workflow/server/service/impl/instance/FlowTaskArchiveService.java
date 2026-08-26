package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

/**
 * 流程任务归档服务实现。
 *
 * <p>将已结束的流程任务（{@code ydsz_flow_run_task}）批量归档到历史表，
 *
 * <p>降低在线表数据量、提升查询性能。归档后通过专门的历史查询接口访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskArchiveService {

  private final FlowRunTaskRepository taskRepository;
  private final FlowHisTaskRepository hisTaskRepository;

  /** P0-1: 事件订阅服务 — 任务完成时取消关联的边界事件订阅。 使用 @Lazy 避免循环依赖。 */
  @Lazy private final FlowEventSubscriptionService eventSubscriptionService;

  /**
   * 完成任务 + 归档到历史表 + 取消边界事件订阅。
   *
   * <p>主流程（如 OR 会签、跳转等）调用此方法一次性完成：状态置为 COMPLETED、 写入历史、取消订阅。注意：本方法会修改 task
   * 的运行时状态（taskStatus/finishAt/durationMs）， 调用方传入的 task 对象将被同步更新（用于后续业务判断）。
   *
   * @param task 任务（会被原地修改状态/时间/时长）
   * @param comment 审批意见
   */
  public void completeAndArchive(FlowRunTaskVO task, String comment) {
    completeAndArchive(task, comment, null);
  }

  /**
   * 完成任务 + 归档到历史表 + 取消边界事件订阅（支持补录审批）。
   *
   * <p>当 {@code effectiveTime} 非空时，使用补录时间作为 {@code finishAt}（穿越时空/补录审批）， 否则使用当前系统时间。
   *
   * @param task          任务（会被原地修改状态/时间/时长）
   * @param comment       审批意见
   * @param effectiveTime 补录生效时间，{@code null} 表示即时生效
   */
  public void completeAndArchive(FlowRunTaskVO task, String comment, LocalDateTime effectiveTime) {
    LocalDateTime finishTime = effectiveTime != null ? effectiveTime : LocalDateTime.now();
    Long durationMs =
        task.getCreatedAt() == null ? null : Duration.between(task.getCreatedAt(), finishTime).toMillis();
    task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
    task.setComment(comment);
    task.setFinishAt(finishTime);
    task.setDurationMs(durationMs);
    if (effectiveTime != null) {
      task.setEffectiveTime(effectiveTime);
    }
    // P0-2: CAS 并发防护 — 基于 task_status IN ('PENDING','CLAIMED') 条件更新，
    // 受影响行数为 0 表示任务已被其他请求并发处理完成（如或签双人同时提交），
    // 直接抛 TASK_ALREADY_HANDLED 触发事务回滚，杜绝重复归档/重复推进。
    int updated =
        taskRepository.completeTaskWithComment(
            task.getId(), FlowTaskStatus.COMPLETED.name(), comment, finishTime, durationMs);
    if (updated == 0) {
      log.warn("[Flow] 任务已被并发处理完成，终止本请求: taskId={}", task.getId());
      throw BusinessException.builder()
          .resultCode(WorkflowExceptionCode.TASK_ALREADY_HANDLED)
          .params(task.getId())
          .build();
    }
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
   * <p>用于 reject 场景：调用方已通过 taskMapper.completeTask 写入终态，这里 仅做历史归档。也用于 AUTO_PASS / 自动去重 / 跨节点推进等场景。
   *
   * @param src 源任务
   * @param finalStatus 归档时的最终状态（用于历史表 taskStatus 字段）
   */
  public void archiveToHistory(FlowRunTaskVO src, FlowTaskStatus finalStatus) {
    FlowHisTaskVO his = new FlowHisTaskVO();
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
    // P2-1: 穿越时空（补录审批）— 复制生效时间到历史表
    his.setEffectiveTime(src.getEffectiveTime());
    his.setTenantId(src.getTenantId());
    his.setProviderTraceId(src.getProviderTraceId());
    // GAP-P2-10: 归档保留 iter_var，FOREACH 任务审批历史可追溯
    his.setIterVar(src.getIterVar());
    hisTaskRepository.save(his);
  }
}
