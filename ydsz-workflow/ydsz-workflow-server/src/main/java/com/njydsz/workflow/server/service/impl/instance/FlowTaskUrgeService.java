package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowUrgeLimiter;
import com.njydsz.workflow.server.metrics.FlowMetrics;

/**
 * 流程任务催办服务实现。
 *
 * <p>向当前审批人发送催办通知（站内信/IM），支持催办次数限制、
 *
 * <p>催办升级（催办 N 次后自动转办给上级）、催办抑制（防骚扰）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskUrgeService {

  private final FlowRunTaskRepository taskRepository;
  private final FlowInstanceRepository instanceRepository;
  private final FlowTaskSupport support;
  private final FlowUrgeLimiter urgeLimiter;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /**
   * P1-9: 实例级催办 — 通知当前节点所有待办处理人。
   *
   * <p>P0-2: 同一催办人对同一实例 30 分钟内只允许一次。
   *
   *
   *
   * @param instanceId 流程实例 ID
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务处理人 ID 列表
   */
  public List<String> urge(String instanceId, String operatorId, String comment) {
    if (operatorId != null
        && instanceId != null
        && !urgeLimiter.tryAcquire(operatorId, Long.parseLong(instanceId), "INSTANCE")) {
      throw SysException.builder()
          .resultCode(YdszResultCode.TOO_MANY_REQUESTS)
          .message("error.workflow.urge.too.frequent")
          .build();
    }
    List<FlowRunTaskVO> pendingTasks = taskRepository.findPendingByInstance(instanceId);
    List<String> urged = new ArrayList<>();
    for (FlowRunTaskVO task : pendingTasks) {
      urged.add(task.getAssigneeId());
      support.audit(task, "URGE", operatorId, null, comment);
    }
    log.info("[Flow] 催办: instanceId={} 被催办人={}", instanceId, urged);
    recordUrgeMetrics(instanceId);
    return urged;
  }

  /**
   * 节点级催办 — 仅通知指定节点的待办处理人。
   *
   * <p>nodeCode 为空时退化为实例级催办。 P0-2: 节点级限流（同一催办人对该节点 30 分钟内只允许一次）。
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 目标节点编码
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务处理人 ID 列表
   */
  public List<String> urgeByNode(
      String instanceId, String nodeCode, String operatorId, String comment) {
    if (nodeCode == null || nodeCode.isBlank()) {
      return urge(instanceId, operatorId, comment);
    }
    if (operatorId != null && instanceId != null) {
      String nodeTarget = instanceId + ":" + nodeCode;
      if (!urgeLimiter.tryAcquire(operatorId, nodeTarget.hashCode() & Long.MAX_VALUE, "NODE")) {
        throw SysException.builder()
            .resultCode(YdszResultCode.TOO_MANY_REQUESTS)
            .message("error.workflow.urge.too.frequent")
            .build();
      }
    }
    List<FlowRunTaskVO> pendingTasks = taskRepository.findPendingByNode(instanceId, nodeCode);
    List<String> urged = new ArrayList<>();
    for (FlowRunTaskVO task : pendingTasks) {
      urged.add(task.getAssigneeId());
      support.audit(task, "URGE", operatorId, null, comment);
      // P2-3: 节点级催办事件
      support.fireEvent(l -> l.onTaskUrged(instanceId, task.getId()), task.getId());
      support.publishWorkflowEvent("TASK_URGED", instanceId, task.getId());
    }
    log.info("[Flow] 节点级催办: instanceId={} nodeCode={} 被催办人={}", instanceId, nodeCode, urged);
    recordUrgeMetrics(instanceId);
    return urged;
  }

  /**
   * 记录催办指标（按 flowCode 维度）
   *
   * @param instanceId 流程实例 ID（用于获取 flowCode 维度的指标标记）
   */
  private void recordUrgeMetrics(String instanceId) {
    if (flowMetrics == null) {
      return;
    }
    try {
      FlowInstanceVO ins = instanceRepository.findById(instanceId).orElse(null);
      flowMetrics.incTask(ins != null ? ins.getFlowCode() : "unknown", "", "urged");
    } catch (Exception e) {
      flowMetrics.incTask("unknown", "", "urged");
    }
  }
}
