package com.njydsz.workflow.server.service.impl.strategy;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

/**
 * 并行会签策略。
 *
 * <p>所有候选人会签任务并行创建，
 *
 * <p>按策略规则（全部通过/任一通过/票决）决定整体结果。
 *
 * <p><b>GAP-A1 并发修复：</b>会签计数改为数据库侧原子自增
 * （{@link FlowRunTaskRepository#incrementApproveFinished}），
 * 消除"读取 VO → 内存加一 → 整行回写"在多办理人并发提交时的丢失更新；
 * 计数成功后以数据库权威值回填 VO，保证 {@link #shouldAdvance} 判定的准确性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ParallelCountersignStrategy implements CountersignStrategy {

  /** 运行时任务仓储，用于会签计数原子自增 */
  private final FlowRunTaskRepository taskRepository;

  /** 任务归档服务，会签全部通过后完成 + 归档到历史表 */
  private final FlowTaskArchiveService archiveService;

  @Override
  public FlowPerformType supportedType() {
    return FlowPerformType.PARALLEL;
  }

  @Override
  public void onUserPassed(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    // GAP-A1: 数据库侧原子自增 + 饱和守卫（approve_finished < approve_count），
    // 受影响行数为 0 表示任务不存在或计数已越过上限（重复提交），抛冲突异常由调用方处理
    int updated = taskRepository.incrementApproveFinished(task.getId());
    if (updated == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.countersign.optimistic.lock")
          .params(task.getId())
          .build();
    }
    // GAP-A1: 以数据库权威值回填（并发下本地 VO 的 finished 已可能过期）
    taskRepository
        .findById(task.getId())
        .ifPresent(fresh -> task.setApproveFinished(fresh.getApproveFinished()));
    // P2-1: 支持穿越时空补录审批
    LocalDateTime effectiveTime =
        Boolean.TRUE.equals(dto.getBackdated()) ? dto.getEffectiveTime() : null;
    archiveService.completeAndArchive(task, dto.getComment(), effectiveTime);
  }

  @Override
  public boolean shouldAdvance(FlowRunTaskVO task) {
    int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
    int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
    return finished >= required;
  }
}
