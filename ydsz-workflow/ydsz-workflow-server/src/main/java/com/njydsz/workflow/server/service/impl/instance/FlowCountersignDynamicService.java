package com.njydsz.workflow.server.service.impl.instance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

/**
 * P2-6: 会签动态完成条件服务
 *
 * <p>对标 Camunda <b>multiInstance completionCondition</b> 特性， 支持在审批运行时<b>动态修改</b>会签通过人数阈值。
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
 * @see FlowRunTask 运行时任务实体（持有 approveCount 字段）
 * @see CountersignStrategy 会签策略接口
 * @see SysException 业务异常
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCountersignDynamicService {

  // ============================== 依赖注入 ==============================

  /** 运行时任务 Mapper，负责 {@code ydsz_flow_run_task} 表的查询与更新 */
  private final FlowRunTaskMapper taskMapper;

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
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_e1f2a3b4")
          .build();
    }

    FlowRunTask task = taskMapper.selectById(taskId);
    if (task == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .key("error.workflow.msg_c9d0e1f2")
          .params(taskId)
          .build();
    }

    Integer oldCount = task.getApproveCount();
    task.setApproveCount(approveCount);
    taskMapper.updateById(task);

    log.info(
        "[FlowCountersign] P2-6 动态修改通过人数: taskId={} oldCount={} → newCount={} operator={}",
        taskId,
        oldCount,
        approveCount,
        operatorId);
  }
}
