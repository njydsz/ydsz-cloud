package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.metrics.FlowMetrics;

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

  private final FlowRunTaskRepository taskRepository;
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
    FlowRunTaskVO task = support.getTaskOrThrow(taskId);
    if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_5873f2ae")
          .params(task.getTaskStatus())
          .build();
    }
    applyClaim(task, userId);
    taskRepository.update(task);
    support.audit(task, "CLAIM", userId, null, null);
    // P1-4: 记录代理签收日志
    auditService.logDelegateOperation(task, "CLAIM");
    log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), "claimed");
    }
  }

  /**
   * 将任务设置为已签收状态（不持久化）。
   *
   * @param src 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private FlowRunTaskVO applyClaim(FlowRunTaskVO src, String userId) {
    src.setAssigneeId(String.valueOf(userId));
    src.setTaskStatus(FlowTaskStatus.CLAIMED.name());
    src.setClaimAt(LocalDateTime.now());
    return src;
  }
}
