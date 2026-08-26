package com.njydsz.workflow.server.service.impl.strategy;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

/**
 * 或签策略。
 *
 * <p>候选人中任一人审批即视为通过，未通过的候选人自动跳过，
 *
 * <p>任一拒绝则整体拒绝。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class OrCountersignStrategy implements CountersignStrategy {

  /** 任务归档服务，或签通过后完成 + 归档到历史表 */
  private final FlowTaskArchiveService archiveService;

  @Override
  public FlowPerformType supportedType() {
    return FlowPerformType.OR;
  }

  @Override
  public void onUserPassed(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    // 完成 + 归档（P2-1: 支持穿越时空补录审批）
    LocalDateTime effectiveTime =
        Boolean.TRUE.equals(dto.getBackdated()) ? dto.getEffectiveTime() : null;
    archiveService.completeAndArchive(task, dto.getComment(), effectiveTime);
  }

  @Override
  public boolean shouldAdvance(FlowRunTaskVO task) {
    // OR 模式：一人通过即推进
    return true;
  }
}
