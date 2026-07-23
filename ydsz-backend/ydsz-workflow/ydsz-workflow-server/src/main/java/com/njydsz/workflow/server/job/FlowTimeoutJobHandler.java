package com.njydsz.workflow.server.job;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.job.JobHandler;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowSlaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GAP-P1: SLA 超时自动化任务处理器
 *
 * <p>P1-3 重构后职责拆分：
 * <ul>
 *   <li><b>单任务 SLA 处理</b> — 委托给 {@link FlowSlaService#processOverdue}，由 FlowSlaServiceImpl
 *       统一实现完整闭环（NOTIFY/ESCALATE/AUTO_PASS/AUTO_REJECT），包括提醒计数、间隔控制、
 *       最终动作分发。消除原先两套实现不一致的问题（旧 markTimeout 会标记 TIMEOUT 终态导致流程卡死）。</li>
 *   <li><b>子流程超时检测</b> — 扫描 ydsz_flow_instance 中 due_at 已超期且状态为 RUNNING 的子流程实例，
 *       自动终止子流程并同步终止父流程。此项为本 Handler 独有职责，不委托。</li>
 * </ul>
 *
 * <p>容错策略：每个任务/实例独立 try-catch，单个处理失败不影响其余。
 *
 * <p>Bean 名称 = {@code flowTimeoutJobHandler}，
 * 可在 ydsz_job 表配置：handler=flowTimeoutJobHandler, cron="0 0/5 * * * ?"（每 5 分钟扫描一次）。
 *
 * @since 1.0.0
 */
@Slf4j
@Component("flowTimeoutJobHandler")
@RequiredArgsConstructor
public class FlowTimeoutJobHandler implements JobHandler {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    /**
     * P1-3: SLA 服务 — 单任务 SLA 处理委托给 FlowSlaService，统一闭环语义
     *
     * <p>FlowSlaServiceImpl 已实现完整的 SLA 闭环（NOTIFY/ESCALATE/AUTO_PASS/AUTO_REJECT），
     * 此处委托可避免两套实现不一致（如旧 markTimeout 会标记 TIMEOUT 终态导致流程卡死）。
     * FlowTimeoutJobHandler 仅保留"子流程超时检测"独有职责。
     */
    private final FlowSlaService slaService;

    /**
     * 扫描并处理超期任务
     *
     * @param paramsJson 参数 JSON（预留，可传 tenantId 缩小扫描范围），可空
     * @return 执行结果摘要：processed/skipped/error 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[FlowTimeout] 开始扫描超期任务 params={}", paramsJson);

        // 解析可选参数：tenantId（可空，为空时扫描全租户）
        String tenantId = parseTenantId(paramsJson);

        // selectOverdue 已内置 deleted=0、task_status IN ('PENDING','CLAIMED')、due_at < now 过滤
        List<FlowRunTaskDO> overdueTasks;
        try {
            overdueTasks = taskMapper.selectOverdue(null, tenantId);
        } catch (Exception e) {
            log.error("[FlowTimeout] 查询超期任务失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        // 处理任务超时
        int processed = 0;
        int errors = 0;
        if (overdueTasks != null && !overdueTasks.isEmpty()) {
            for (FlowRunTaskDO task : overdueTasks) {
                try {
                    handleOverdueTask(task);
                    processed++;
                } catch (Exception e) {
                    errors++;
                    log.error("[FlowTimeout] 处理超期任务异常 taskId={} instanceId={} err={}",
                            task.getId(), task.getInstanceId(), e.getMessage(), e);
                }
            }
        }

        // 子流程超时检测
        int subProcessProcessed = 0;
        int subProcessErrors = 0;
        try {
            Map<String, Object> subResult = handleSubProcessTimeout(tenantId);
            subProcessProcessed = (int) subResult.getOrDefault("processed", 0);
            subProcessErrors = (int) subResult.getOrDefault("errors", 0);
        } catch (Exception e) {
            log.error("[FlowTimeout] 子流程超时检测异常: {}", e.getMessage(), e);
            subProcessErrors = 1;
        }

        log.info("FlowTimeoutJobHandler: processed {} overdue tasks, {} subProcess timeouts",
                processed, subProcessProcessed);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("total", overdueTasks == null ? 0 : overdueTasks.size());
        result.put("processed", processed);
        result.put("errors", errors);
        result.put("subProcessProcessed", subProcessProcessed);
        result.put("subProcessErrors", subProcessErrors);
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    // ============================== 单任务处理 ==============================

    /**
     * 处理单个超期任务：委托给 FlowSlaService 统一处理
     *
     * <p>P1-3 闭环重构：原先在 JobHandler 中重复实现 SLA 动作分发（doRemind/doEscalate/
     * doAutoPass/doAutoReject/markTimeout），与 FlowSlaServiceImpl 存在两套实现，行为不一致。
     * 尤其 markTimeout 会将任务标记为 TIMEOUT 终态（流程卡死），违反 SLA 闭环原则。
     *
     * <p>现统一委托给 {@link FlowSlaService#processOverdue(FlowRunTaskDO)}，由 FlowSlaServiceImpl
     * 负责完整的 SLA 闭环处理（NOTIFY/ESCALATE/AUTO_PASS/AUTO_REJECT），包括：
     * <ul>
     *   <li>提醒计数与间隔控制（reminderCount / lastRemindedAt / maxReminders）</li>
     *   <li>最终动作分发（NOTIFY 保持任务活跃 / ESCALATE 转办 / AUTO_PASS 推进 / AUTO_REJECT 终止）</li>
     *   <li>REQUIRES_NEW 子事务隔离，单条失败不影响主循环</li>
     * </ul>
     *
     * @param task 超期任务
     */
    private void handleOverdueTask(FlowRunTaskDO task) {
        boolean processed = slaService.processOverdue(task);
        if (processed) {
            log.debug("[FlowTimeout] SLA 处理完成: taskId={} instanceId={}",
                    task.getId(), task.getInstanceId());
        }
    }

    // ============================== 子流程超时检测 ==============================

    /**
     * 处理子流程实例超时：扫描 ydsz_flow_instance 中 due_at 已超期且状态为 RUNNING 的子流程实例
     *
     * <p>对于超时子流程，自动终止子流程实例并取消其下活跃任务，
     * 同时回调父流程推进（通过 onSubProcessTerminated 终止父流程）。
     *
     * @param tenantId 租户 ID（可空）
     * @return 处理计数
     */
    private Map<String, Object> handleSubProcessTimeout(String tenantId) {
        List<FlowInstanceDO> overdueInstances = instanceMapper.selectOverdueInstances(tenantId);
        int processed = 0;
        int errors = 0;
        if (overdueInstances == null || overdueInstances.isEmpty()) {
            log.info("[FlowTimeout] 当前无超期子流程实例");
            Map<String, Object> result = new HashMap<>();
            result.put("processed", 0);
            result.put("errors", 0);
            return result;
        }
        for (FlowInstanceDO instance : overdueInstances) {
            try {
                handleOverdueSubProcess(instance);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("[FlowTimeout] 处理超期子流程异常 instanceId={} err={}",
                        instance.getId(), e.getMessage(), e);
            }
        }
        log.info("[FlowTimeout] 子流程超时处理完成: processed={} errors={}", processed, errors);
        Map<String, Object> result = new HashMap<>();
        result.put("processed", processed);
        result.put("errors", errors);
        return result;
    }

    /**
     * 处理单个超期子流程实例：终止子流程，取消其任务，并同步终止父流程
     *
     * @param instance 超期子流程实例
     */
    private void handleOverdueSubProcess(FlowInstanceDO instance) {
        String instanceId = instance.getId();
        LocalDateTime now = LocalDateTime.now();
        // 二次校验：避免并发的状态变更
        FlowInstanceDO latest = instanceMapper.selectById(instanceId);
        if (latest == null || !FlowInstanceStatus.RUNNING.name().equals(latest.getFlowStatus())) {
            log.info("[FlowTimeout] 子流程实例状态已变更，跳过: instanceId={} status={}",
                    instanceId, latest == null ? "null" : latest.getFlowStatus());
            return;
        }
        // 终止子流程实例
        long instanceDurationMs = latest.getStartAt() == null
                ? 0L : Duration.between(latest.getStartAt(), now).toMillis();
        instanceMapper.updateStatus(instanceId,
                FlowInstanceStatus.TERMINATED.name(),
                null, null, now, instanceDurationMs);
        // 取消子流程下所有活跃任务
        taskMapper.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
        log.info("[FlowTimeout] 子流程超时，已终止子流程实例: instanceId={} flowCode={} dueAt={}",
                instanceId, latest.getFlowCode(), latest.getDueAt());
        // 清除 dueAt 标记
        instanceMapper.updateDueAt(instanceId, null);
        // 如果存在父流程，同步终止父流程
        String parentId = latest.getParentInstanceId();
        if (parentId != null) {
            FlowInstanceDO parent = instanceMapper.selectById(parentId);
            if (parent != null && FlowInstanceStatus.RUNNING.name().equals(parent.getFlowStatus())) {
                long parentDurationMs = parent.getStartAt() == null
                        ? 0L : Duration.between(parent.getStartAt(), now).toMillis();
                instanceMapper.updateStatus(parentId,
                        FlowInstanceStatus.TERMINATED.name(),
                        null, null, now, parentDurationMs);
                taskMapper.cancelByInstance(parentId, FlowTaskStatus.CANCELLED.name());
                log.info("[FlowTimeout] 子流程超时触发父流程终止: parentId={} childId={}",
                        parentId, instanceId);
            }
        }
    }

    // ============================== 私有辅助 ==============================

    /** 从 paramsJson 解析 tenantId（可空） */
    private String parseTenantId(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> obj = YdszJson.parseMap(paramsJson);
            if (obj == null) {
                return null;
            }
            return obj.getString("tenantId");
        } catch (Exception e) {
            log.warn("[FlowTimeout] 参数 JSON 解析失败，忽略 tenantId: {}", e.getMessage());
            return null;
        }
    }
}
