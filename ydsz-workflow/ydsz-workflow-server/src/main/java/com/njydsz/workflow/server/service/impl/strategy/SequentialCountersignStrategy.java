package com.njydsz.workflow.server.service.impl.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.entity.FlowUser;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowUserMapper;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;

import lombok.RequiredArgsConstructor;

/**
 * 顺序会签策略。
 *
 * <p>按候选人顺序串行审批，前一人通过后才进入下一人，
 *
 * <p>任一拒绝则整体拒绝。
 *
 * @author ydsz-team
 * @since 1.0.0
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
    public void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        if (finished < required) {
            // 切换下一个未处理的人
            List<FlowUser> unprocessed =
                    userMapper.selectUnprocessedByInstanceAndNode(
                            task.getInstanceId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                FlowUser next = unprocessed.get(0);
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
    public boolean shouldAdvance(FlowRunTask task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        return finished >= required;
    }
}
