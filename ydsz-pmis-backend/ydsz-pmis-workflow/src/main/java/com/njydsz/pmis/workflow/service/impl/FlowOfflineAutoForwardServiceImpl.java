package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowDelegateAuthMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.FlowOfflineAutoForwardService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 离线代理自动转发服务实现（P2-5）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowOfflineAutoForwardServiceImpl implements FlowOfflineAutoForwardService {

    private final FlowDelegateAuthMapper delegateAuthMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowTaskService taskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoForwardByAuth(String authId) {
        if (!StringUtils.hasText(authId)) {
            return 0;
        }
        FlowDelegateAuthDO auth = delegateAuthMapper.selectById(authId);
        if (auth == null) {
            log.warn("[OfflineForward] 代理授权不存在: authId={}", authId);
            return 0;
        }
        // 校验授权状态
        if (!"ACTIVE".equals(auth.getAuthStatus())) {
            log.info("[OfflineForward] 代理授权非激活状态，跳过: authId={} status={}",
                    authId, auth.getAuthStatus());
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (auth.getStartTime() != null && now.isBefore(auth.getStartTime())) {
            log.info("[OfflineForward] 代理授权未生效: authId={} startTime={}", authId, auth.getStartTime());
            return 0;
        }
        if (auth.getEndTime() != null && now.isAfter(auth.getEndTime())) {
            log.info("[OfflineForward] 代理授权已过期: authId={} endTime={}", authId, auth.getEndTime());
            return 0;
        }

        return forwardTasks(auth.getOwnerUserId(), auth.getDelegateUserId(),
                auth.getDelegateUserName(), auth.getFlowCode(), auth.getTenantId(),
                "AUTO_FORWARD", auth.getOwnerUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int manualForward(String userId, String delegateUserId, String operatorId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(delegateUserId)) {
            log.warn("[OfflineForward] 参数缺失: userId={} delegateUserId={}", userId, delegateUserId);
            return 0;
        }
        if (userId.equals(delegateUserId)) {
            log.warn("[OfflineForward] 不可转发给自己: userId={}", userId);
            return 0;
        }
        return forwardTasks(userId, delegateUserId, null, null, null,
                "MANUAL_FORWARD", operatorId);
    }

    // ============================== 内部辅助 ==============================

    /**
     * 执行批量转办
     *
     * @param userId           原办理人 ID
     * @param delegateUserId   代理人 ID
     * @param delegateUserName 代理人姓名
     * @param flowCode         流程编码（可空，空表示全部流程）
     * @param tenantId         租户 ID
     * @param reason           转办原因
     * @param operatorId       操作人 ID
     * @return 成功转发的任务数
     */
    private int forwardTasks(String userId, String delegateUserId, String delegateUserName,
                             String flowCode, String tenantId, String reason, String operatorId) {
        // 查询原办理人名下的待办
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, userId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, "TODO", "CLAIMED");
        if (StringUtils.hasText(flowCode)) {
            wrapper.eq(FlowRunTaskDO::getFlowCode, flowCode);
        }
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(FlowRunTaskDO::getTenantId, tenantId);
        }

        List<FlowRunTaskDO> tasks = taskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            log.info("[OfflineForward] 无待办需要转发: userId={} flowCode={}", userId, flowCode);
            return 0;
        }

        int successCount = 0;
        for (FlowRunTaskDO task : tasks) {
            try {
                FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                dto.setTaskId(task.getId());
                dto.setUserId(operatorId != null ? operatorId : userId);
                dto.setComment("[" + reason + "] 离线代理自动转发");
                dto.setTargetUserId(delegateUserId);
                dto.setTargetUserName(delegateUserName);
                taskService.transfer(dto);
                successCount++;
                log.info("[OfflineForward] 转发成功: taskId={} from={} to={} reason={}",
                        task.getId(), userId, delegateUserId, reason);
            } catch (Exception e) {
                log.warn("[OfflineForward] 转发失败: taskId={} err={}", task.getId(), e.getMessage());
            }
        }

        log.info("[OfflineForward] 批量转发完成: userId={} delegateUserId={} total={} success={} reason={}",
                userId, delegateUserId, tasks.size(), successCount, reason);
        return successCount;
    }
}
