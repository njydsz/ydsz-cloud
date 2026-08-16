package com.njydsz.workflow.server.service.impl.instance;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程任务认领服务实现。
 *
 * <p>实现任务的「认领」模式：候选人都能看见，认领后由认领人处理，
 *
 * <p>其他人不再可见。认领基于乐观锁防止并发冲突，
 *
 * <p>未认领任务超时自动释放回候选人池。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskClaimService {

  private final FlowRunTaskMapper taskMapper;
  private final FlowTaskSupport support;
  private final FlowTaskAuditService auditService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /**
   * 签收任务。
   *
   * <p>校验任务当前状态必须为 PENDING，写入 assigneeId/claimAt 后状态变为 CLAIMED。 若任务处于其他状态，抛 BAD_REQUEST 异常。
   *
   * @param taskId 任务 ID
   * @param userId 签收人 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void claim(String taskId, String userId) {
    FlowRunTask task = support.getTaskOrThrow(taskId);
    if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_5873f2ae")
          .params(task.getTaskStatus())
          .build();
    }
    applyClaim(task, userId);
    taskMapper.updateById(task);
    support.audit(task, "CLAIM", userId, null, null);
    // P1-4: 记录代理签收日志
    auditService.logDelegateOperation(task, "CLAIM", "ACT");
    log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incTaskClaimed(task.getFlowCode(), task.getNodeCode());
    }
  }

  /** 将任务设置为已签收状态（不持久化）。 */
  private FlowRunTask applyClaim(FlowRunTask src, String userId) {
    src.setAssigneeId(String.valueOf(userId));
    src.setTaskStatus(FlowTaskStatus.CLAIMED.name());
    src.setClaimAt(LocalDateTime.now());
    return src;
  }
}
