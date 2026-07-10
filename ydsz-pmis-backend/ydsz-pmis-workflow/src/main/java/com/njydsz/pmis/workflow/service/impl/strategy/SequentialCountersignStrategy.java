package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.definition.FlowAssigneeType;
import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.mapper.integration.FlowUserMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 顺序会签策略：按序逐一处理，全部通过才推进。
 *
 * <p>对标钉钉/飞书"顺序会签"。当前人通过后切换到下一个人，乐观锁防并发。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Component
@RequiredArgsConstructor
public class SequentialCountersignStrategy implements CountersignStrategy {

    private final FlowRunTaskMapper taskMapper;
    private final FlowUserMapper userMapper;
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.SEQUENTIAL;
    }

    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        if (finished < required) {
            // 切换下一个未处理的人
            List<com.njydsz.pmis.workflow.entity.FlowUserDO> unprocessed =
                    userMapper.selectUnprocessedByInstanceAndNode(
                            task.getInstanceId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                com.njydsz.pmis.workflow.entity.FlowUserDO next = unprocessed.get(0);
                taskMapper.updateAssignee(task.getId(), next.getUserId(), next.getUserName(),
                        FlowAssigneeType.USER.name());
            }
        } else {
            archiveService.completeAndArchive(task, dto.getComment());
        }
    }

    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        return finished >= required;
    }
}
