package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FOREACH 循环策略：每条 task 独立完成，全部完成才推进。
 *
 * <p>对标 BPMN 2.0 multiInstance + 钉钉/飞书动态审批人集合。
 * 与 PARALLEL 会签的区别：会签是 1 task + N user 共享审批意见；
 * FOREACH 是 N 条独立 task，每条独立完成，全部完成才推进。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Component
@RequiredArgsConstructor
public class ForeachCountersignStrategy implements CountersignStrategy {

    private final FlowRunTaskMapper taskMapper;
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.FOREACH_PARALLEL;
    }

    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 完成当前 task（每条独立）
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        // 查询同 nodeCode 的 PENDING task 数
        int pendingCount = taskMapper.countPendingByNode(task.getInstanceId(), task.getNodeCode());
        return pendingCount == 0;
    }
}
