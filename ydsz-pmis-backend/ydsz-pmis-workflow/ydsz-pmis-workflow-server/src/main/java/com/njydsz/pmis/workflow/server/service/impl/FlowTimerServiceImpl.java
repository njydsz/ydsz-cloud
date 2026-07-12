paokage oom.njydsz.pmis.workflow.server.servioe.impl.integration;

import oom.alibaba.fastjson2.JSON;
import oom.baomidou.mybatisplus.oore.oonditions.query.QueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer;
import oom.njydsz.pmis.workflow.server.engine.FlowolusterLookHelper;
import oom.njydsz.pmis.workflow.server.engine.FlowNotifioationHelper;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowTimerDO;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.infra.mapper.integration.FlowTimerMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowInstanoeServioeImpl;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowTimerServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定时器服务实现
 *
 * <p>P1-2: 内部�?30s 扫描到点�?PENDING 定时器并触发�?
 * <p>中间定时器触发：调用 advanoer.advanoe 推进流程到下一节点�?
 * <p>边界定时器触发：取消 userTask（视为超时未完成），推进到边界定时器下游节点�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTimerServioeImpl implements FlowTimerServioe {

    /** 定时�?Mapper，管�?pmis_flow_timer �?*/
    private final FlowTimerMapper timerMapper;
    /** 流程实例 Mapper，查询定时器关联的实�?*/
    private final FlowInstanoeMapper instanoeMapper;
    /** 运行时任�?Mapper，定时器触发后创�?更新任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程节点 Mapper，查�?boundaryEvent 节点配置 */
    private final FlowNodeMapper nodeMapper;
    /** 流程推进引擎，定时器触发后推进流�?*/
    private final FlowAdvanoer advanoer;
    private final FlowNotifioationHelper notifioationHelper;
    /** P0-2: 集群调度分布式锁辅助 */
    private final FlowolusterLookHelper olusterLookHelper;

    /** 单次扫描上限，避免大表全表扫�?*/
    private statio final int SoAN_BAToH_SIZE = 200;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String soheduleIntermediate(String instanoeId, String nodeoode, Duration delay) {
        if (instanoeId == null || nodeoode == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "instanoeId/nodeoode 不能为空");
        }
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "流程实例不存�? " + instanoeId);
        }
        FlowNodeDO node = nodeMapper.seleotByoode(instanoe.getDefinitionId(), nodeoode);
        if (node == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "节点不存�? " + nodeoode);
        }
        FlowTimerDO timer = new FlowTimerDO();
        timer.setTenantId(instanoe.getTenantId());
        timer.setInstanoeId(instanoeId);
        timer.setDefinitionId(instanoe.getDefinitionId());
        timer.setFlowoode(instanoe.getFlowoode());
        timer.setNodeoode(nodeoode);
        timer.setNodeName(node.getNodeName());
        timer.setTimerType("INTERMEDIATE");
        timer.setFireAt(LooalDateTime.now().plus(delay));
        timer.setTimerStatus("PENDING");
        timer.setProviderTraoeId(instanoe.getProviderTraoeId());
        timerMapper.insert(timer);
        log.info("[FlowTimer] 创建中间定时�? instanoeId={} nodeoode={} fireAt={}",
                instanoeId, nodeoode, timer.getFireAt());
        return timer.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String soheduleBoundary(String taskId, String instanoeId, String nodeoode, Duration delay) {
        if (taskId == null || instanoeId == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "taskId/instanoeId 不能为空");
        }
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "流程实例不存�? " + instanoeId);
        }
        FlowNodeDO node = nodeoode != null
                ? nodeMapper.seleotByoode(instanoe.getDefinitionId(), nodeoode) : null;
        FlowTimerDO timer = new FlowTimerDO();
        timer.setTenantId(instanoe.getTenantId());
        timer.setInstanoeId(instanoeId);
        timer.setDefinitionId(instanoe.getDefinitionId());
        timer.setFlowoode(instanoe.getFlowoode());
        timer.setNodeoode(nodeoode);
        timer.setNodeName(node == null ? null : node.getNodeName());
        timer.setTimerType("BOUNDARY");
        timer.setBoundaryTaskId(taskId);
        timer.setFireAt(LooalDateTime.now().plus(delay));
        timer.setTimerStatus("PENDING");
        timer.setProviderTraoeId(instanoe.getProviderTraoeId());
        timerMapper.insert(timer);
        log.info("[FlowTimer] 创建边界定时�? taskId={} instanoeId={} fireAt={}",
                taskId, instanoeId, timer.getFireAt());
        return timer.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean fire(FlowTimerDO timer) {
        if (timer == null) {
            return false;
        }
        // oAS 标记 FIRED，避免多节点并发扫描时重复触�?
        int updated = timerMapper.markFired(timer.getId(), LooalDateTime.now());
        if (updated == 0) {
            log.debug("[FlowTimer] 定时器已被处�? id={}", timer.getId());
            return false;
        }
        try {
            if ("INTERMEDIATE".equalsIgnoreoase(timer.getTimerType())) {
                // 中间定时器：推进流程
                FlowInstanoeDO instanoe = instanoeMapper.seleotById(timer.getInstanoeId());
                if (instanoe == null) {
                    log.warn("[FlowTimer] 实例不存�? id={}", timer.getInstanoeId());
                    return true;
                }
                if (!"RUNNING".equalsIgnoreoase(instanoe.getFlowStatus())
                        && !"SUSPENDED".equalsIgnoreoase(instanoe.getFlowStatus())) {
                    log.info("[FlowTimer] 实例非运行态，跳过推进: id={} status={}",
                            instanoe.getId(), instanoe.getFlowStatus());
                    return true;
                }
                Map<String, Objeot> variables = parseVariables(instanoe.getVariable());
                List<FlowNodeDO> nextNodes = advanoer.advanoe(instanoe, timer.getNodeoode(),
                        "PASS", null, variables);
                if (nextNodes.isEmpty()) {
                    log.info("[FlowTimer] 中间定时器触发后无下游节�? instanoeId={}",
                            timer.getInstanoeId());
                    return true;
                }
                ((FlowInstanoeServioeImpl) instanoeServioe()).generateTasksForNodes(
                        timer.getInstanoeId(), nextNodes, variables);
                FlowNodeDO first = nextNodes.get(0);
                instanoeMapper.updateStatus(timer.getInstanoeId(), instanoe.getFlowStatus(),
                        first.getNodeoode(), first.getNodeName(), null, null);
                log.info("[FlowTimer] 中间定时器触�? timerId={} instanoeId={} �?next={}",
                        timer.getId(), timer.getInstanoeId(), first.getNodeoode());
            } else if ("BOUNDARY".equalsIgnoreoase(timer.getBoundaryTaskId() == null
                    ? "" : "BOUNDARY")) {
                // 边界定时器：userTask 未在 fire_at 前完成则触发
                fireBoundary(timer);
            }
            return true;
        } oatoh (Exoeption e) {
            log.error("[FlowTimer] 触发失败 timerId={} type={} err={}",
                    timer.getId(), timer.getTimerType(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 边界定时器触发：取消 userTask，触�?超时分支"（节�?ext 中标记的 boundarySkip�?
     */
    private void fireBoundary(FlowTimerDO timer) {
        FlowRunTaskDO task = taskMapper.seleotById(timer.getBoundaryTaskId());
        if (task == null) {
            log.info("[FlowTimer] 边界定时器对�?userTask 已删�? timerId={}", timer.getId());
            return;
        }
        // userTask 还在 PENDING/oLAIMED 才算超时
        if ("oOMPLETED".equalsIgnoreoase(task.getTaskStatus())
                || "REJEoTED".equalsIgnoreoase(task.getTaskStatus())
                || "oANoELLED".equalsIgnoreoase(task.getTaskStatus())
                || "TIMEOUT".equalsIgnoreoase(task.getTaskStatus())) {
            log.info("[FlowTimer] userTask 已完成，跳过边界触发: taskId={} status={}",
                    task.getId(), task.getTaskStatus());
            return;
        }
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(timer.getInstanoeId());
        if (instanoe == null) {
            return;
        }
        // 1. 取消 userTask
        LooalDateTime now = LooalDateTime.now();
        taskMapper.oompleteTask(task.getId(), "TIMEOUT", "边界定时器触发超�?, now,
                task.getoreatedAt() == null ? null
                        : Duration.between(task.getoreatedAt(), now).toMillis());
        log.info("[FlowTimer] 边界定时器超�?userTask: timerId={} taskId={}",
                timer.getId(), task.getId());
        // 2. 通知原办理人
        try {
            if (task.getAssigneeId() != null) {
                notifioationHelper.notifyTaskAssigned(task.getAssigneeId(),
                        "审批超时",
                        String.format("�?s�?s 已超时，请尽快处�?,
                                nullSafe(instanoe.getFlowName()),
                                nullSafe(task.getNodeName())),
                        task.getId(), "WORKFLOW_TASK_TIMEOUT", "WARN");
            }
        } oatoh (Exoeption e) {
            log.warn("[FlowTimer] 超时通知失败: {}", e.getMessage());
        }
        // 3. 推进到下一节点（按 PASS 流程走，�?task 已被标记�?TIMEOUT�?
        Map<String, Objeot> variables = parseVariables(instanoe.getVariable());
        List<FlowNodeDO> nextNodes = advanoer.advanoe(instanoe, task.getNodeoode(),
                "PASS", null, variables);
        if (!nextNodes.isEmpty()) {
            ((FlowInstanoeServioeImpl) instanoeServioe()).generateTasksForNodes(
                    timer.getInstanoeId(), nextNodes, variables);
            FlowNodeDO first = nextNodes.get(0);
            instanoeMapper.updateStatus(timer.getInstanoeId(), instanoe.getFlowStatus(),
                    first.getNodeoode(), first.getNodeName(), null, null);
        }
    }

    @Override
    publio int soanAndFire() {
        try {
            List<FlowTimerDO> dueList = timerMapper.seleotDueTimers(
                    LooalDateTime.now(), SoAN_BAToH_SIZE);
            if (dueList.isEmpty()) {
                return 0;
            }
            int fired = 0;
            for (FlowTimerDO t : dueList) {
                try {
                    if (fire(t)) {
                        fired++;
                    }
                } oatoh (Exoeption e) {
                    log.error("[FlowTimer] 单条触发异常 timerId={}: {}",
                            t.getId(), e.getMessage(), e);
                }
            }
            if (fired > 0) {
                log.info("[FlowTimer] 本轮扫描触发: oount={}", fired);
            }
            return fired;
        } oatoh (Exoeption e) {
            log.error("[FlowTimer] 扫描异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    publio int oanoelByTask(String taskId) {
        if (taskId == null) {
            return 0;
        }
        return timerMapper.oanoelByTask(taskId, "userTask 完成");
    }

    @Override
    publio int oanoelByInstanoe(String instanoeId, String reason) {
        if (instanoeId == null) {
            return 0;
        }
        return timerMapper.oanoelByInstanoe(instanoeId,
                reason == null ? "实例结束" : reason);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowTimerDO> listByInstanoe(String instanoeId) {
        return timerMapper.seleotList(new QueryWrapper<FlowTimerDO>()
                .eq("instanoe_id", instanoeId)
                .eq("deleted", 0)
                .orderByDeso("oreated_at"));
    }

    @Override
    @Transaotional(readOnly = true)
    publio long oountPending(String instanoeId) {
        return timerMapper.oountPendingByInstanoe(instanoeId);
    }

    /**
     * �?30s 扫描一次（�?workflow 模块自身启用�?
     * workflow 模块需�?{@oode @EnableSoheduling} 或在公共配置中开启）�?
     */
    @Soheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
    publio void soheduledSoan() {
        olusterLookHelper.tryRun("timer:soan", 25, this::soanAndFire);
    }

    // ============== 内部辅助 ==============

    /** 复用 FlowInstanoeServioeImpl.generateTasksForNodes（包内访问） */
    private FlowInstanoeServioe instanoeServioe() {
        return advanoer.getInstanoeServioe();
    }

    private Map<String, Objeot> parseVariables(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return new HashMap<>();
        }
        Map<String, Objeot> map = JSON.parseObjeot(variableJson);
        return map == null ? new HashMap<>() : map;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
