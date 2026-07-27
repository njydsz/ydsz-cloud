package com.njydsz.workflow.server.service.impl.instance;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-6: 会签动态完成条件服务
 *
 * <p>对标 Camunda multiInstance completionCondition。
 * 支持在审批运行时动态修改会签通过阈值（VOTE/WEIGHTED_VOTE 模式）。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCountersignDynamicService {

    private final FlowRunTaskMapper taskMapper;

    /**
     * 动态更新会签任务的通过阈值。
     *
     * @param taskId       任务 ID
     * @param votePassRate 新的通过率阈值（0~1）
     * @param operatorId   操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCompletionCondition(String taskId, BigDecimal votePassRate, String operatorId) {
        if (!StringUtils.hasText(taskId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_a7b8c9d0");
        }
        if (votePassRate == null || votePassRate.compareTo(BigDecimal.ZERO) < 0
                || votePassRate.compareTo(BigDecimal.ONE) > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_b8c9d0e1");
        }

        FlowRunTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c9d0e1f2", taskId);
        }

        // 仅 VOTE / WEIGHTED_VOTE 模式允许动态修改
        String performType = task.getPerformType();
        if (!"VOTE".equals(performType) && !"WEIGHTED_VOTE".equals(performType)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d0e1f2a3");
        }

        BigDecimal oldRate = task.getVotePassRate();
        task.setVotePassRate(votePassRate);
        taskMapper.updateById(task);

        log.info("[FlowCountersign] P2-6 动态修改完成条件: taskId={} oldRate={} → newRate={} operator={}",
                taskId, oldRate, votePassRate, operatorId);
    }

    /**
     * 动态更新会签所需通过人数。
     *
     * @param taskId        任务 ID
     * @param approveCount  新的所需通过人数
     * @param operatorId    操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateApproveCount(String taskId, Integer approveCount, String operatorId) {
        if (!StringUtils.hasText(taskId) || approveCount == null || approveCount < 1) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_e1f2a3b4");
        }

        FlowRunTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c9d0e1f2", taskId);
        }

        Integer oldCount = task.getApproveCount();
        task.setApproveCount(approveCount);
        taskMapper.updateById(task);

        log.info("[FlowCountersign] P2-6 动态修改通过人数: taskId={} oldCount={} → newCount={} operator={}",
                taskId, oldCount, approveCount, operatorId);
    }
}
