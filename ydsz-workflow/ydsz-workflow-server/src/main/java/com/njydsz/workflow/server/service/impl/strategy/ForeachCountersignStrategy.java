package com.njydsz.workflow.server.service.impl.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

/**
 * 迭代会签策略。
 *
 * <p>逐个遍历会签候选人，每人都需审批，所有人通过则通过，
 *
 * <p>任一拒绝则整体拒绝。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Component
@RequiredArgsConstructor
public class ForeachCountersignStrategy implements CountersignStrategy {

    /** 运行时任务 Mapper，用于查询同节点 PENDING 任务数以判断是否全部完成 */
    private final FlowRunTaskMapper taskMapper;
    /** 任务归档服务，完成单条 task 后归档到历史表 */
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.FOREACH_PARALLEL;
    }

    @Override
    public void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto) {
        // 完成当前 task（每条独立）
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTask task) {
        // 查询同 nodeCode 的 PENDING task 数
        int pendingCount = taskMapper.countPendingByNode(task.getInstanceId(), task.getNodeCode());
        return pendingCount == 0;
    }
}
