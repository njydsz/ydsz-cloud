package com.njydsz.pmis.workflow.server.service.impl.strategy;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.workflow.domain.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.domain.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowTaskArchiveService;
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

    /**
     * 返回该策略支持的办理类型
     *
     * @return VOTE（票签）
     */
    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.VOTE;
    }

    /**
     * 票签用户通过处理
     *
     * <p>递增已通过计数，完成当前用户任务并归档。
     *
     * @param task 运行时任务
     * @param dto  任务操作 DTO（含审批意见）
     * @throws SysException 乐观锁更新失败时抛出
     */
    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysException(StandardResultCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        archiveService.completeAndArchive(task, dto.getComment());
    }

    /**
     * 判断票签是否应该推进到下一节点
     *
     * <p>通过阈值计算：默认过半数（50% + 1），可由 votePassRate 配置。
     *
     * @param task 运行时任务
     * @return true 表示已通过人数达到阈值
     */
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

    /**
     * 票签达到阈值后的推进处理
     *
     * <p>跳过同节点剩余 PENDING 任务（状态置为 SKIPPED）。
     *
     * @param task 运行时任务
     * @param dto  任务操作 DTO
     */
    @Override
    public void onAdvance(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 票签达到阈值后跳过同节点剩余 PENDING 任务
        taskMapper.skipByNode(task.getInstanceId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
    }
}
