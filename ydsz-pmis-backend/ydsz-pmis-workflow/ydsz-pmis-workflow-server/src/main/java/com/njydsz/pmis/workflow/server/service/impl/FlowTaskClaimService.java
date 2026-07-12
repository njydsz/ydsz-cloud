package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务签收服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"任务签收"职责。
 * 签收将 PENDING 任务标记为 CLAIMED，记录签收时间和办理人。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskClaimService {

    private final FlowRunTaskMapper taskMapper;
    private final FlowTaskSupport support;
    private final FlowTaskAuditService auditService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    /**
     * 签收任务。
     *
     * <p>校验任务当前状态必须为 PENDING，写入 assigneeId/claimAt 后状态变为 CLAIMED。
     * 若任务处于其他状态，抛 BAD_REQUEST 异常。
     *
     * @param taskId 任务 ID
     * @param userId 签收人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(String taskId, String userId) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_5873f2ae", task.getTaskStatus());
        }
        applyClaim(task, userId);
        taskMapper.updateById(task);
        support.audit(task, "CLAIM", userId, null, null);
        // P1-4: 记录代理签收日志
        auditService.logDelegateOperation(task, "CLAIM", "ACT");
        log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskClaimed(task.getFlowCode(), task.getNodeCode());
        }
    }

    /**
     * 将任务设置为已签收状态（不持久化）。
     */
    private FlowRunTaskDO applyClaim(FlowRunTaskDO src, String userId) {
        src.setAssigneeId(String.valueOf(userId));
        src.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        src.setClaimAt(LocalDateTime.now());
        return src;
    }
}
