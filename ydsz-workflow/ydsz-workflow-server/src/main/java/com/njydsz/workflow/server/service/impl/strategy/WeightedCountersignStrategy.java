package com.njydsz.workflow.server.service.impl.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowRunTask;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

/**
 * 票签（加权投票）策略。
 *
 * <p>每个办理人拥有不同的权重值，当累计通过权重 / 总权重 ≥ votePassRate 时满足推进条件。
 *
 * <p><b>设计场景：</b>
 *
 * <ul>
 *   <li>董事会表决：董事长 30 票、副董事长 20 票、董事各 15 票，过半数通过
 *   <li>技术委员会架构评审：架构师权重高、普通开发权重低
 *   </ul>
 *
 * <p><b>计票规则：</b>
 *
 * <ol>
 *   <li>每次办理人通过时，累加其 {@code userWeight} 到 {@code approveWeight}</li>
 *   <li>通过率 = {@code approveWeight / totalWeight}，与 {@code votePassRate} 比较</li>
 *   <li>通过率 ≥ {@code votePassRate} 时满足推进条件，自动归档当前办理人任务</li>
 * </ol>
 *
 * <p><b>节点配置（ext JSON）：</b>
 *
 * <ul>
 *   <li>{@code userWeights}：{@code {"userId": weight, ...}} 映射，未配置默认为 1</li>
 *   <li>{@code votePassRate}：通过率阈值（0~1），默认 0.5</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowPerformType#WEIGHTED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeightedCountersignStrategy implements CountersignStrategy {

  /** 运行时任务仓储，用于乐观锁更新 approveWeight 计数 */
  private final FlowRunTaskRepository taskRepository;

  /** MapStruct 转换器（DO/VO/DTO 转换） */
  private final WorkflowConverter converter;

  /** 任务归档服务，票签满足条件后完成 + 归档到历史表 */
  private final FlowTaskArchiveService archiveService;

  /** 默认通过率阈值（50%） */
  private static final BigDecimal DEFAULT_PASS_RATE = new BigDecimal("0.5");

  /** 计算精度（小数位） */
  private static final int SCALE = 4;

  @Override
  public FlowPerformType supportedType() {
    return FlowPerformType.WEIGHTED;
  }

  @Override
  public void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto) {
    int weight = task.getUserWeight() == null ? 1 : task.getUserWeight();
    int currentApproved = task.getApproveWeight() == null ? 0 : task.getApproveWeight();
    int newApproved = currentApproved + weight;
    task.setApproveWeight(newApproved);

    int updated = taskRepository.update(converter.entityToVO(task)) != null ? 1 : 0;
    if (updated == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_199e8ba1")
          .params(task.getId())
          .build();
    }
    archiveService.completeAndArchive(task, dto.getComment());
  }

  @Override
  public boolean shouldAdvance(FlowRunTask task) {
    int approved = task.getApproveWeight() == null ? 0 : task.getApproveWeight();
    int total = task.getTotalWeight() == null || task.getTotalWeight() <= 0
        ? (task.getApproveCount() == null ? 1 : task.getApproveCount())
        : task.getTotalWeight();

    if (total <= 0) {
      log.warn("[Flow] 票签总权重为 0，无法计算: taskId={}", task.getId());
      return false;
    }

    BigDecimal passRate = task.getVotePassRate() == null
        ? DEFAULT_PASS_RATE
        : task.getVotePassRate();

    BigDecimal currentRate = new BigDecimal(approved)
        .divide(new BigDecimal(total), SCALE, RoundingMode.HALF_UP);

    boolean canAdvance = currentRate.compareTo(passRate) >= 0;

    log.info(
        "[Flow] 票签计票: taskId={} approved={}/{} rate={} passRate={} canAdvance={}",
        task.getId(), approved, total, currentRate, passRate, canAdvance);

    return canAdvance;
  }

  @Override
  public void onAdvance(FlowRunTask task, FlowTaskOperateDTO dto) {
    int approved = task.getApproveWeight() == null ? 0 : task.getApproveWeight();
    int total = task.getTotalWeight() == null ? 0 : task.getTotalWeight();
    log.info(
        "[Flow] 票签通过: instanceId={} nodeCode={} approveWeight={}/{}",
        task.getInstanceId(), task.getNodeCode(), approved, total);
  }
}
