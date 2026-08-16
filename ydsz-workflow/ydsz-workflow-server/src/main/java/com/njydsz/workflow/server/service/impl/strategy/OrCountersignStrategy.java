package com.njydsz.workflow.server.service.impl.strategy;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
  public void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto) {
    // 完成 + 归档
    archiveService.completeAndArchive(task, dto.getComment());
  }

  @Override
  public boolean shouldAdvance(FlowRunTask task) {
    // OR 模式：一人通过即推进
    return true;
  }
}
