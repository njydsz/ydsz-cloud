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
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ParallelCountersignStrategy implements CountersignStrategy {

  /** 运行时任务仓储，用于乐观锁更新 approveFinished 计数 */
  private final FlowRunTaskRepository taskRepository;

  /** 任务归档服务，会签全部通过后完成 + 归档到历史表 */
  private final FlowTaskArchiveService archiveService;

  @Override
  public FlowPerformType supportedType() {
    return FlowPerformType.PARALLEL;
  }

  @Override
  public void onUserPassed(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
    task.setApproveFinished(finished);
    int updated = taskRepository.update(task) != null ? 1 : 0;
    if (updated == 0) {
      // 乐观锁冲突，抛异常由调用方处理
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_199e8ba1")
          .params(task.getId())
          .build();
    }
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
