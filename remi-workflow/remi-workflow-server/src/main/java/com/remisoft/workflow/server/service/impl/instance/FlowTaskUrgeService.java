package com.remisoft.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.workflow.domain.entity.FlowInstance;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.infra.mapper.FlowInstanceMapper;
import com.remisoft.workflow.infra.mapper.FlowRunTaskMapper;
import com.remisoft.workflow.server.engine.FlowUrgeLimiter;
import com.remisoft.workflow.server.metrics.FlowMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程任务催办服务实现。
 *
 * <p>向当前审批人发送催办通知（站内信/IM），支持催办次数限制、
 *
 * <p>催办升级（催办 N 次后自动转办给上级）、催办抑制（防骚扰）。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskUrgeService {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskSupport support;
    private final FlowUrgeLimiter urgeLimiter;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    /**
     * P1-9: 实例级催办 — 通知当前节点所有待办处理人。
     *
     * <p>P0-2: 同一催办人对同一实例 30 分钟内只允许一次。
     *
     * @return 被催办人 ID 列表
     */
    public List<String> urge(String instanceId, String operatorId, String comment) {
        if (operatorId != null && instanceId != null
                && !urgeLimiter.tryAcquire(operatorId, Long.parseLong(instanceId), "INSTANCE")) {
            throw new SysException(BaseResultCode.TOO_MANY_REQUESTS, "error.workflow.msg_75474a57");
        }
        List<FlowRunTask> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        List<String> urged = new ArrayList<>();
        for (FlowRunTask task : pendingTasks) {
            urged.add(task.getAssigneeId());
            support.audit(task, "URGE", operatorId, null, comment);
        }
        log.info("[Flow] 催办: instanceId={} 被催办人={}", instanceId, urged);
        recordUrgeMetrics(instanceId);
        return urged;
    }

    /**
     * 节点级催办 — 仅通知指定节点的待办处理人。
     *
     * <p>nodeCode 为空时退化为实例级催办。
     * P0-2: 节点级限流（同一催办人对该节点 30 分钟内只允许一次）。
     */
    public List<String> urgeByNode(String instanceId, String nodeCode, String operatorId, String comment) {
        if (nodeCode == null || nodeCode.isBlank()) {
            return urge(instanceId, operatorId, comment);
        }
        if (operatorId != null && instanceId != null) {
            String nodeTarget = instanceId + ":" + nodeCode;
            if (!urgeLimiter.tryAcquire(operatorId, nodeTarget.hashCode() & Long.MAX_VALUE, "NODE")) {
                throw new SysException(BaseResultCode.TOO_MANY_REQUESTS, "error.workflow.msg_75474a57");
            }
        }
        List<FlowRunTask> pendingTasks = taskMapper.selectPendingByNode(instanceId, nodeCode);
        List<String> urged = new ArrayList<>();
        for (FlowRunTask task : pendingTasks) {
            urged.add(task.getAssigneeId());
            support.audit(task, "URGE", operatorId, null, comment);
            // P2-3: 节点级催办事件
            support.fireEvent(l -> l.onTaskUrged(instanceId, task.getId()), task.getId());
            support.publishWorkflowEvent("TASK_URGED", instanceId, task.getId());
        }
        log.info("[Flow] 节点级催办: instanceId={} nodeCode={} 被催办人={}",
                instanceId, nodeCode, urged);
        recordUrgeMetrics(instanceId);
        return urged;
    }

    /**
     * 记录催办指标（按 flowCode 维度）
     */
    private void recordUrgeMetrics(String instanceId) {
        if (flowMetrics == null) {
            return;
        }
        try {
            FlowInstance ins = instanceMapper.selectById(instanceId);
            flowMetrics.incTaskUrged(ins != null ? ins.getFlowCode() : "unknown");
        } catch (Exception e) {
            flowMetrics.incTaskUrged("unknown");
        }
    }
}
