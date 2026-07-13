package com.njydsz.pmis.workflow.server.service.impl.strategy;

import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowTaskArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OR 或签策略：任一办理人通过即推进。
 *
 * <p>对标钉钉/飞书"或签"语义。一人通过 → 立即完成+推进。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
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
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 完成 + 归档
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        // OR 模式：一人通过即推进
        return true;
    }
}
