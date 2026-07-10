package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.impl.instance.FlowTaskArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 票签策略：通过率达到阈值才推进（默认 50% + 1，可配置）。
 *
 * <p>对标钉钉/飞书"票签"。达到阈值后 skipByNode 跳过剩余 PENDING task。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Component
@RequiredArgsConstructor
public class VoteCountersignStrategy implements CountersignStrategy {

    /** 运行时任务 Mapper，用于乐观锁更新 approveFinished 计数及 skipByNode 跳过剩余 PENDING 任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 任务归档服务，票签达到阈值后完成 + 归档到历史表 */
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.VOTE;
    }

    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        int threshold = (required / 2) + 1; // 默认过半数
        if (task.getVotePassRate() != null) {
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                threshold = (int) Math.ceil(required * rate);
                if (threshold < 1) {
                    threshold = 1;
                }
            }
        }
        return finished >= threshold;
    }

    @Override
    public void onAdvance(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 票签达到阈值后跳过同节点剩余 PENDING 任务
        taskMapper.skipByNode(task.getInstanceId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
    }
}
