package com.njydsz.pmis.workflow.server.service.impl.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.server.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.server.engine.FlowClusterLockHelper;
import com.njydsz.pmis.workflow.server.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.entity.integration.FlowTimerDO;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.integration.FlowTimerMapper;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowInstanceServiceImpl;
import com.njydsz.pmis.workflow.server.service.instance.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.integration.FlowTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定时器服务实现
 *
 * <p>P1-2: 内部每 30s 扫描到点的 PENDING 定时器并触发。
 * <p>中间定时器触发：调用 advancer.advance 推进流程到下一节点。
 * <p>边界定时器触发：取消 userTask（视为超时未完成），推进到边界定时器下游节点。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTimerServiceImpl implements FlowTimerService {

    /** 定时器 Mapper，管理 pmis_flow_timer 表 */
    private final FlowTimerMapper timerMapper;
    /** 流程实例 Mapper，查询定时器关联的实例 */
    private final FlowInstanceMapper instanceMapper;
    /** 运行时任务 Mapper，定时器触发后创建/更新任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程节点 Mapper，查询 boundaryEvent 节点配置 */
    private final FlowNodeMapper nodeMapper;
    /** 流程推进引擎，定时器触发后推进流程 */
    private final FlowAdvancer advancer;
    private final FlowNotificationHelper notificationHelper;
    /** P0-2: 集群调度分布式锁辅助 */
    private final FlowClusterLockHelper clusterLockHelper;

    /** 单次扫描上限，避免大表全表扫描 */
    private static final int SCAN_BATCH_SIZE = 200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scheduleIntermediate(String instanceId, String nodeCode, Duration delay) {
        if (instanceId == null || nodeCode == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "instanceId/nodeCode 不能为空");
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        FlowNodeDO node = nodeMapper.selectByCode(instance.getDefinitionId(), nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "节点不存在: " + nodeCode);
        }
        FlowTimerDO timer = new FlowTimerDO();
        timer.setTenantId(instance.getTenantId());
        timer.setInstanceId(instanceId);
        timer.setDefinitionId(instance.getDefinitionId());
        timer.setFlowCode(instance.getFlowCode());
        timer.setNodeCode(nodeCode);
        timer.setNodeName(node.getNodeName());
        timer.setTimerType("INTERMEDIATE");
        timer.setFireAt(LocalDateTime.now().plus(delay));
        timer.setTimerStatus("PENDING");
        timer.setProviderTraceId(instance.getProviderTraceId());
        timerMapper.insert(timer);
        log.info("[FlowTimer] 创建中间定时器: instanceId={} nodeCode={} fireAt={}",
                instanceId, nodeCode, timer.getFireAt());
        return timer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scheduleBoundary(String taskId, String instanceId, String nodeCode, Duration delay) {
        if (taskId == null || instanceId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "taskId/instanceId 不能为空");
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        FlowNodeDO node = nodeCode != null
                ? nodeMapper.selectByCode(instance.getDefinitionId(), nodeCode) : null;
        FlowTimerDO timer = new FlowTimerDO();
        timer.setTenantId(instance.getTenantId());
        timer.setInstanceId(instanceId);
        timer.setDefinitionId(instance.getDefinitionId());
        timer.setFlowCode(instance.getFlowCode());
        timer.setNodeCode(nodeCode);
        timer.setNodeName(node == null ? null : node.getNodeName());
        timer.setTimerType("BOUNDARY");
        timer.setBoundaryTaskId(taskId);
        timer.setFireAt(LocalDateTime.now().plus(delay));
        timer.setTimerStatus("PENDING");
        timer.setProviderTraceId(instance.getProviderTraceId());
        timerMapper.insert(timer);
        log.info("[FlowTimer] 创建边界定时器: taskId={} instanceId={} fireAt={}",
                taskId, instanceId, timer.getFireAt());
        return timer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean fire(FlowTimerDO timer) {
        if (timer == null) {
            return false;
        }
        // CAS 标记 FIRED，避免多节点并发扫描时重复触发
        int updated = timerMapper.markFired(timer.getId(), LocalDateTime.now());
        if (updated == 0) {
            log.debug("[FlowTimer] 定时器已被处理: id={}", timer.getId());
            return false;
        }
        try {
            if ("INTERMEDIATE".equalsIgnoreCase(timer.getTimerType())) {
                // 中间定时器：推进流程
                FlowInstanceDO instance = instanceMapper.selectById(timer.getInstanceId());
                if (instance == null) {
                    log.warn("[FlowTimer] 实例不存在: id={}", timer.getInstanceId());
                    return true;
                }
                if (!"RUNNING".equalsIgnoreCase(instance.getFlowStatus())
                        && !"SUSPENDED".equalsIgnoreCase(instance.getFlowStatus())) {
                    log.info("[FlowTimer] 实例非运行态，跳过推进: id={} status={}",
                            instance.getId(), instance.getFlowStatus());
                    return true;
                }
                Map<String, Object> variables = parseVariables(instance.getVariable());
                List<FlowNodeDO> nextNodes = advancer.advance(instance, timer.getNodeCode(),
                        "PASS", null, variables);
                if (nextNodes.isEmpty()) {
                    log.info("[FlowTimer] 中间定时器触发后无下游节点: instanceId={}",
                            timer.getInstanceId());
                    return true;
                }
                ((FlowInstanceServiceImpl) instanceService()).generateTasksForNodes(
                        timer.getInstanceId(), nextNodes, variables);
                FlowNodeDO first = nextNodes.get(0);
                instanceMapper.updateStatus(timer.getInstanceId(), instance.getFlowStatus(),
                        first.getNodeCode(), first.getNodeName(), null, null);
                log.info("[FlowTimer] 中间定时器触发: timerId={} instanceId={} → next={}",
                        timer.getId(), timer.getInstanceId(), first.getNodeCode());
            } else if ("BOUNDARY".equalsIgnoreCase(timer.getBoundaryTaskId() == null
                    ? "" : "BOUNDARY")) {
                // 边界定时器：userTask 未在 fire_at 前完成则触发
                fireBoundary(timer);
            }
            return true;
        } catch (Exception e) {
            log.error("[FlowTimer] 触发失败 timerId={} type={} err={}",
                    timer.getId(), timer.getTimerType(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 边界定时器触发：取消 userTask，触发"超时分支"（节点 ext 中标记的 boundarySkip）
     */
    private void fireBoundary(FlowTimerDO timer) {
        FlowRunTaskDO task = taskMapper.selectById(timer.getBoundaryTaskId());
        if (task == null) {
            log.info("[FlowTimer] 边界定时器对应 userTask 已删除: timerId={}", timer.getId());
            return;
        }
        // userTask 还在 PENDING/CLAIMED 才算超时
        if ("COMPLETED".equalsIgnoreCase(task.getTaskStatus())
                || "REJECTED".equalsIgnoreCase(task.getTaskStatus())
                || "CANCELLED".equalsIgnoreCase(task.getTaskStatus())
                || "TIMEOUT".equalsIgnoreCase(task.getTaskStatus())) {
            log.info("[FlowTimer] userTask 已完成，跳过边界触发: taskId={} status={}",
                    task.getId(), task.getTaskStatus());
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(timer.getInstanceId());
        if (instance == null) {
            return;
        }
        // 1. 取消 userTask
        LocalDateTime now = LocalDateTime.now();
        taskMapper.completeTask(task.getId(), "TIMEOUT", "边界定时器触发超时", now,
                task.getCreatedAt() == null ? null
                        : Duration.between(task.getCreatedAt(), now).toMillis());
        log.info("[FlowTimer] 边界定时器超时 userTask: timerId={} taskId={}",
                timer.getId(), task.getId());
        // 2. 通知原办理人
        try {
            if (task.getAssigneeId() != null) {
                notificationHelper.notifyTaskAssigned(task.getAssigneeId(),
                        "审批超时",
                        String.format("【%s】%s 已超时，请尽快处理",
                                nullSafe(instance.getFlowName()),
                                nullSafe(task.getNodeName())),
                        task.getId(), "WORKFLOW_TASK_TIMEOUT", "WARN");
            }
        } catch (Exception e) {
            log.warn("[FlowTimer] 超时通知失败: {}", e.getMessage());
        }
        // 3. 推进到下一节点（按 PASS 流程走，但 task 已被标记为 TIMEOUT）
        Map<String, Object> variables = parseVariables(instance.getVariable());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, variables);
        if (!nextNodes.isEmpty()) {
            ((FlowInstanceServiceImpl) instanceService()).generateTasksForNodes(
                    timer.getInstanceId(), nextNodes, variables);
            FlowNodeDO first = nextNodes.get(0);
            instanceMapper.updateStatus(timer.getInstanceId(), instance.getFlowStatus(),
                    first.getNodeCode(), first.getNodeName(), null, null);
        }
    }

    @Override
    public int scanAndFire() {
        try {
            List<FlowTimerDO> dueList = timerMapper.selectDueTimers(
                    LocalDateTime.now(), SCAN_BATCH_SIZE);
            if (dueList.isEmpty()) {
                return 0;
            }
            int fired = 0;
            for (FlowTimerDO t : dueList) {
                try {
                    if (fire(t)) {
                        fired++;
                    }
                } catch (Exception e) {
                    log.error("[FlowTimer] 单条触发异常 timerId={}: {}",
                            t.getId(), e.getMessage(), e);
                }
            }
            if (fired > 0) {
                log.info("[FlowTimer] 本轮扫描触发: count={}", fired);
            }
            return fired;
        } catch (Exception e) {
            log.error("[FlowTimer] 扫描异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int cancelByTask(String taskId) {
        if (taskId == null) {
            return 0;
        }
        return timerMapper.cancelByTask(taskId, "userTask 完成");
    }

    @Override
    public int cancelByInstance(String instanceId, String reason) {
        if (instanceId == null) {
            return 0;
        }
        return timerMapper.cancelByInstance(instanceId,
                reason == null ? "实例结束" : reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowTimerDO> listByInstance(String instanceId) {
        return timerMapper.selectList(new QueryWrapper<FlowTimerDO>()
                .eq("instance_id", instanceId)
                .eq("deleted", 0)
                .orderByDesc("created_at"));
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(String instanceId) {
        return timerMapper.countPendingByInstance(instanceId);
    }

    /**
     * 每 30s 扫描一次（在 workflow 模块自身启用，
     * workflow 模块需配 {@code @EnableScheduling} 或在公共配置中开启）。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
    public void scheduledScan() {
        clusterLockHelper.tryRun("timer:scan", 25, this::scanAndFire);
    }

    // ============== 内部辅助 ==============

    /** 复用 FlowInstanceServiceImpl.generateTasksForNodes（包内访问） */
    private FlowInstanceService instanceService() {
        return advancer.getInstanceService();
    }

    private Map<String, Object> parseVariables(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return new HashMap<>();
        }
        Map<String, Object> map = JSON.parseObject(variableJson);
        return map == null ? new HashMap<>() : map;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
