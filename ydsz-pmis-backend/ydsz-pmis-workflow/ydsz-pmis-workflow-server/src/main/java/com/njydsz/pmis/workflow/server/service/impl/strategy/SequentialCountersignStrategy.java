package com.njydsz.pmis.workflow.server.service.impl.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.entity.FlowUserDO;
import com.njydsz.pmis.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.domain.enums.FlowPerformType;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowTaskArchiveService;

import lombok.RequiredArgsConstructor;

/**
 * 顺序会签策略：按序逐一处理，全部通过才推进。
 *
 * <p>对标钉钉/飞书"顺序会签"。当前人通过后切换到下一个人，乐观锁防并发。
 *
 * @since 1.7.0
 */
@Component
@RequiredArgsConstructor
public class SequentialCountersignStrategy implements CountersignStrategy {

    /** 运行时任务 Mapper，用于乐观锁更新 approveFinished 计数及切换下一办理人 */
    private final FlowRunTaskMapper taskMapper;
    /** 办理人 Mapper，查询同节点未处理用户列表以确定下一顺序办理人 */
    private final FlowUserMapper userMapper;
    /** 任务归档服务，顺序会签全部通过后完成 + 归档到历史表 */
    private final FlowTaskArchiveService archiveService;

    /**
     * 返回该策略支持的办理类型
     *
     * @return SEQUENTIAL（顺序会签）
     */
    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.SEQUENTIAL;
    }

    /**
     * 顺序会签用户通过处理
     *
     * <p>递增已通过计数，若未达到总人数则切换到下一个未处理用户，
     * 若已全部通过则完成并归档任务。
     *
     * @param task 运行时任务
     * @param dto  任务操作 DTO（含审批意见）
     * @throws SysException 乐观锁更新失败时抛出
     */
    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysException(BaseResultCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        if (finished < required) {
            // 切换下一个未处理的人
            List<FlowUserDO> unprocessed =
                    userMapper.selectUnprocessedByInstanceAndNode(
                            task.getInstanceId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                FlowUserDO next = unprocessed.get(0);
                taskMapper.updateAssignee(task.getId(), next.getUserId(), next.getUserName(),
                        FlowAssigneeType.USER.name());
            }
        } else {
            archiveService.completeAndArchive(task, dto.getComment());
        }
    }

    /**
     * 判断顺序会签是否应该推进到下一节点
     *
     * @param task 运行时任务
     * @return true 表示已通过人数达到要求总数
     */
    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        return finished >= required;
    }
}
