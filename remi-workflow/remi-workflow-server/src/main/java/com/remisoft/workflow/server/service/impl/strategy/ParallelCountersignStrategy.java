package com.remisoft.workflow.server.service.impl.strategy;

import org.springframework.stereotype.Component;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.workflow.domain.dto.FlowTaskOperateDTO;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.domain.enums.FlowPerformType;
import com.remisoft.workflow.infra.mapper.FlowRunTaskMapper;
import com.remisoft.workflow.server.service.impl.CountersignStrategy;
import com.remisoft.workflow.server.service.impl.instance.FlowTaskArchiveService;

import lombok.RequiredArgsConstructor;

/**
 * 并行会签策略。
 *
 * <p>所有候选人会签任务并行创建，
 *
 * <p>按策略规则（全部通过/任一通过/票决）决定整体结果。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Component
@RequiredArgsConstructor
public class ParallelCountersignStrategy implements CountersignStrategy {

    /** 运行时任务 Mapper，用于乐观锁更新 approveFinished 计数 */
    private final FlowRunTaskMapper taskMapper;
    /** 任务归档服务，会签全部通过后完成 + 归档到历史表 */
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.PARALLEL;
    }

    @Override
    public void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            // 乐观锁冲突，抛异常由调用方处理
            throw new SysException(
                    BaseResultCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTask task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        return finished >= required;
    }
}
