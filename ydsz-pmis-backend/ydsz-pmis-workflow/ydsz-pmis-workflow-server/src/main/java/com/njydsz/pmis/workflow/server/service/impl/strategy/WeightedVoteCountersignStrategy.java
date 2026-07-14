package com.njydsz.pmis.workflow.server.service.impl.strategy;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.entity.FlowUserDO;
import com.njydsz.pmis.workflow.domain.enums.FlowPerformType;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowTaskArchiveService;

import lombok.RequiredArgsConstructor;

/**
 * 加权票签策略：按办理人 weight 累加，权重达到阈值才推进。
 *
 * <p>对标用友/金蝶"加权会签"。每个办理人有 weight 属性，累计通过权重达到阈值推进。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Component
@RequiredArgsConstructor
public class WeightedVoteCountersignStrategy implements CountersignStrategy {

    /** 运行时任务 Mapper，用于乐观锁更新 approveFinished 计数及 skipByNode 跳过剩余 PENDING 任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 办理人 Mapper，查询含 weight 属性的办理人列表并标记已处理状态 */
    private final FlowUserMapper userMapper;
    /** 任务归档服务，加权票签达到阈值后完成 + 归档到历史表 */
    private final FlowTaskArchiveService archiveService;

    @Override
    public FlowPerformType supportedType() {
        return FlowPerformType.WEIGHTED_VOTE;
    }

    @Override
    public void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 标记当前用户已处理
        if (dto.getUserId() != null) {
            userMapper.markProcessed(task.getId(), String.valueOf(dto.getUserId()),
                    dto.getComment(), LocalDateTime.now());
        }
        // 累加 approveFinished
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysException(BaseResultCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        archiveService.completeAndArchive(task, dto.getComment());
    }

    @Override
    public boolean shouldAdvance(FlowRunTaskDO task) {
        // 查询所有办理人含 weight
        List<FlowUserDO> users = userMapper.selectByTaskId(task.getId());
        if (users == null || users.isEmpty()) {
            // 无扩展数据：回退到简单票签
            int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
            int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
            return finished >= (required / 2 + 1);
        }
        int totalWeight = users.stream()
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        int passedWeight = users.stream()
                .filter(u -> Integer.valueOf(1).equals(u.getProcessed()))
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        int threshold = (totalWeight / 2) + 1;
        if (task.getVotePassRate() != null) {
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                threshold = (int) Math.ceil(totalWeight * rate);
                if (threshold < 1) threshold = 1;
            }
        }
        return passedWeight >= threshold;
    }

    @Override
    public void onAdvance(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 跳过同节点剩余 PENDING
        taskMapper.skipByNode(task.getInstanceId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
    }
}
