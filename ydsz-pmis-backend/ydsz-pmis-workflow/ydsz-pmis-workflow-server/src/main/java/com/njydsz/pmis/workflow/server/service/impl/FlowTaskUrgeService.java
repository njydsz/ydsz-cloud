package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.server.engine.FlowUrgeLimiter;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务催办服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"催办"职责。
 * 集中处理：
 * <ul>
 *   <li>{@link #urge} — 实例级催办（按 30 分钟 Redis Lua 冷却）</li>
 *   <li>{@link #urgeByNode} — 节点级催办（同样限流）</li>
 * </ul>
 *
 * <p>催办对每个待办任务写入审计日志、触发 onTaskUrged 事件、累计 Prometheus 指标。
 * 限流通过 {@link FlowUrgeLimiter} 实现，限流命中时抛 RATE_LIMIT 异常。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
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
            throw new SysException(StandardResultCode.RATE_LIMIT, "error.workflow.msg_75474a57");
        }
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        List<String> urged = new ArrayList<>();
        for (FlowRunTaskDO task : pendingTasks) {
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
                throw new SysException(StandardResultCode.RATE_LIMIT, "error.workflow.msg_75474a57");
            }
        }
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByNode(instanceId, nodeCode);
        List<String> urged = new ArrayList<>();
        for (FlowRunTaskDO task : pendingTasks) {
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
            FlowInstanceDO ins = instanceMapper.selectById(instanceId);
            flowMetrics.incTaskUrged(ins != null ? ins.getFlowCode() : "unknown");
        } catch (Exception e) {
            flowMetrics.incTaskUrged("unknown");
        }
    }
}
