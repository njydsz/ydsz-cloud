package com.njydsz.workflow.server.service.impl.strategy;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

import lombok.RequiredArgsConstructor;

/**
 * 并行会签策略：所有办理人全部通过才推进。
 *
 * <p>对标钉钉/飞书"会签"。N 个办理人共享 1 条 task + N 条 FlowUserDO，
 * approveFinished 计数聚合在单 task 上。乐观锁防并发。
 *
 * @since 1.7.0
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
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
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
    public boolean shouldAdvance(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        return finished >= required;
    }
}
