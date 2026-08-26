package com.njydsz.workflow.server.service.impl.instance;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * P2-6: 会签动态完成条件服务
 *
 * <p>支持在审批运行时<b>动态修改</b>会签通过人数阈值。
 *
 * <p><b>业务场景：</b>
 *
 * <ul>
 *   <li><b>人数补强</b>：会签过程中需要新增或减少通过人数要求
 *   <li><b>紧急应对</b>：发现配置不合理时，<b>运行时</b>立即调整而非终止重新发起
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>动态通过人数</b>：{@link #updateApproveCount} — 修改会签所需通过人数
 *   <li><b>参数校验</b>：通过人数 ≥ 1、任务必须存在
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}， 「参数校验 + 任务更新」原子性。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>模式无关</b>：不限制 performType，任何模式都允许动态调整通过人数
 *   <li><b>操作审计</b>：所有修改操作记录「旧值 → 新值 + operator」日志，便于追溯
 *   <li><b>幂等性</b>：相同参数的多次调用结果一致（更新为相同值）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowRunTaskVO 运行时任务视图对象（持有 approveCount 字段）
 * @see CountersignStrategy 会签策略接口
 * @see SysException 业务异常
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCountersignDynamicService {

  // ============================== 依赖注入 ==============================

  /** 运行时任务仓储，负责 {@code ydsz_flow_run_task} 表的查询与更新 */
  private final FlowRunTaskRepository taskRepository;

  // ============================== 公共方法 ==============================

  /**
   * 动态更新会签所需通过人数
   *
   * <p>与会签模式的会签模式相比，本方法不限制 {@code performType}（任何模式都允许修改）。 修改后<b>未达成</b>的会签按新人数阈值判断。
   *
   * <p><b>事务边界：</b>开启 {@code @Transactional(rollbackFor = Exception.class)}， 「参数校验 + 任务更新」原子性。
   *
   * @param taskId 任务 ID
   * @param approveCount 新的所需通过人数（{@link Integer}，必须 ≥ 1）
   * @param operatorId 操作人 ID（用于审计日志）
   * @throws SysException 当参数非法或任务不存在时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateApproveCount(String taskId, Integer approveCount, String operatorId) {
    if (!StringUtils.hasText(taskId) || approveCount == null || approveCount < 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_e1f2a3b4")
          .build();
    }

    FlowRunTaskVO task = taskRepository.findById(taskId).orElse(null);
    if (task == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_c9d0e1f2")
          .params(taskId)
          .build();
    }

    Integer oldCount = task.getApproveCount();
    task.setApproveCount(approveCount);
    taskRepository.update(task);

    log.info(
        "[FlowCountersign] P2-6 动态修改通过人数: taskId={} oldCount={} → newCount={} operator={}",
        taskId,
        oldCount,
        approveCount,
        operatorId);
  }

  /**
   * 动态更新会签通过率阈值
   *
   * <p>运行时调整进行中会签任务的「通过率」完成条件（如把 80% 调到 60%）。
   * 后续会签投票按新阈值判定：达成即推进流程，未达成继续等。
   *
   * <p><b>事务边界：</b>开启 {@code @Transactional(rollbackForException.class)}，
   * 「参数校验 + 任务更新」原子性。
   *
   * @param taskId 任务 ID
   * @param votePassRate 新的通过率阈值（0~1 之间的 BigDecimal）
   * @param operatorId 操作人 ID（用于审计日志）
   * @throws SysException 当参数非法或任务不存在时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateCompletionCondition(String taskId, BigDecimal votePassRate, String operatorId) {
    if (!StringUtils.hasText(taskId) || votePassRate == null
        || votePassRate.compareTo(BigDecimal.ZERO) < 0
        || votePassRate.compareTo(BigDecimal.ONE) > 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_vote_pass_rate_invalid")
          .build();
    }

    FlowRunTaskVO task = taskRepository.findById(taskId).orElse(null);
    if (task == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_c9d0e1f2")
          .params(taskId)
          .build();
    }

    BigDecimal oldRate = task.getVotePassRate();
    task.setVotePassRate(votePassRate);
    taskRepository.update(task);

    log.info(
        "[FlowCountersign] P2-6 动态修改通过率阈值: taskId={} oldRate={} → newRate={} operator={}",
        taskId,
        oldRate,
        votePassRate,
        operatorId);
  }
}
