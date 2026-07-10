package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
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
