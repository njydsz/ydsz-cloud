package com.njydsz.pmis.workflow.server.job;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.core.job.JobHandler;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.service.FlowNotificationService;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GAP-P1: SLA 超时自动化任务处理器
 *
 * <p>定时扫描 pmis_flow_run_task 中已超期（due_at &lt; now）且状态为 PENDING/CLAIMED 的待办任务，
 * 根据节点 sla_config 配置的 action 执行自动化处理：
 * <ul>
 *   <li>REMIND —— 发送催办通知（站内信+邮件，调用 notificationService.notifySlaTimeout）</li>
 *   <li>ESCALATE —— 升级办理人，将任务转交给 sla_config.adminUserId</li>
 *   <li>AUTO_PASS —— 自动通过任务并联动 FlowAdvancer 推进流程（taskService.pass）</li>
 *   <li>AUTO_REJECT —— 自动驳回任务并终止流程实例</li>
 * </ul>
 *
 * <p>sla_config 为空时仅标记任务为 TIMEOUT 并记录日志。
 *
 * <p>容错策略：每个任务独立 try-catch，单个任务处理失败不影响其余任务。
 *
 * <p>Bean 名称 = {@code flowTimeoutJobHandler}，
 * 可在 pmis_job 表配置：handler=flowTimeoutJobHandler, cron="0 0/5 * * * ?"（每 5 分钟扫描一次）。
 *
 * <p>说明：FlowRunTaskMapper 暂无 {@code timeoutTask} 专用方法，
 * 此处复用 {@link FlowRunTaskMapper#completeTask} 以 {@link FlowTaskStatus#TIMEOUT} 状态标记超时，
 * 与 {@code FlowTaskServiceImpl.timeoutTask} 内部实现保持一致。
 *
 * <p>增强：添加子流程超时检测逻辑，扫描 pmis_flow_instance 中 due_at 已超期且状态为 RUNNING 的子流程实例，
 * 自动终止子流程并同步父流程。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowTimeoutJobHandler")
@RequiredArgsConstructor
public class FlowTimeoutJobHandler implements JobHandler {

    /** SLA 动作：催办提醒 */
    private static final String ACTION_REMIND = "REMIND";
    /** SLA 动作：升级办理人 */
    private static final String ACTION_ESCALATE = "ESCALATE";
    /** SLA 动作：自动通过 */
    private static final String ACTION_AUTO_PASS = "AUTO_PASS";
    /** SLA 动作：自动驳回 */
    private static final String ACTION_AUTO_REJECT = "AUTO_REJECT";

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeMapper nodeMapper;
    /** P0-1/P0-2: 任务服务（AUTO_PASS 真正推进流程） */
    private final FlowTaskService taskService;
    /** P0-2: 通知服务（REMIND 真实触达） */
    private final FlowNotificationService notificationService;

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
     * 处理单个超期任务：读取节点 sla_config，按 action 分发
     *
     * @param task 超期任务
     */
    private void handleOverdueTask(FlowRunTaskDO task) {
        String taskId = task.getId();
        String instanceId = task.getInstanceId();
        FlowNodeDO node = safelySelectNode(task);

        // 节点不存在或 sla_config 为空：仅标记超时
        if (node == null || node.getSlaConfig() == null || node.getSlaConfig().isBlank()) {
            markTimeout(task, "SLA超时，节点未配置 sla_config");
            log.info("[FlowTimeout] 任务超时（无 SLA 配置）taskId={} instanceId={} node={}",
                    taskId, instanceId, task.getNodeCode());
            return;
        }

        JSONObject slaConfig;
        try {
            slaConfig = JSON.parseObject(node.getSlaConfig());
        } catch (Exception e) {
            log.warn("[FlowTimeout] sla_config JSON 解析失败 taskId={} node={} raw={} err={}",
                    taskId, task.getNodeCode(), node.getSlaConfig(), e.getMessage());
            markTimeout(task, "SLA超时，sla_config 解析失败");
            return;
        }
        if (slaConfig == null) {
            markTimeout(task, "SLA超时，sla_config 为空对象");
            return;
        }

        String action = slaConfig.getString("action");
        if (action == null || action.isBlank()) {
            markTimeout(task, "SLA超时，未配置 action");
            return;
        }

        switch (action.toUpperCase()) {
            case ACTION_REMIND -> doRemind(task, slaConfig);
            case ACTION_ESCALATE -> doEscalate(task, slaConfig);
            case ACTION_AUTO_PASS -> doAutoPass(task, slaConfig);
            case ACTION_AUTO_REJECT -> doAutoReject(task, slaConfig);
            default -> {
                log.warn("[FlowTimeout] 未知 SLA action={} taskId={} node={}，按默认超时处理",
                        action, taskId, task.getNodeCode());
                markTimeout(task, "SLA超时，未知 action=" + action);
            }
        }
    }

    // ============================== SLA 动作实现 ==============================

    /**
     * REMIND：发送催办通知（P0-2 修复：真实触达）
     *
     * <p>任务保持 PENDING/CLAIMED 不变，办理人仍可继续处理。
     * 通过 FlowNotificationService 发送站内信 + 邮件通知，确保催办消息真实触达办理人。
     * 同时更新任务的 reminder_count 和 last_reminded_at 字段。
     */
    private void doRemind(FlowRunTaskDO task, JSONObject slaConfig) {
        int reminderCount = slaConfig.getIntValue("reminderCount", 1);
        String assigneeId = task.getAssigneeId();

        // P0-2: 真实发送催办通知（站内信 + 邮件），通知服务内部 try-catch 不会拖垮定时任务
        try {
            notificationService.notifySlaTimeout(
                    task.getInstanceId(), task.getId(), assigneeId, ACTION_REMIND);
        } catch (Exception e) {
            log.warn("[FlowTimeout] 催办通知发送失败（不影响后续处理）taskId={} err={}",
                    task.getId(), e.getMessage());
        }

        // 更新任务催办计数与最后催办时间
        try {
            taskMapper.incrementReminderCount(task.getId(),
                    (task.getReminderCount() == null ? 0 : task.getReminderCount()) + 1,
                    LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[FlowTimeout] 更新催办计数失败 taskId={} err={}", task.getId(), e.getMessage());
        }

        log.info("[FlowTimeout] SLA 催办提醒已发送 taskId={} instanceId={} assignee={} reminderCount={}",
                task.getId(), task.getInstanceId(), assigneeId, reminderCount);
    }

    /**
     * ESCALATE：升级办理人
     *
     * <p>将任务办理人切换为 sla_config.adminUserId，任务保持活跃（PENDING/CLAIMED），
     * 由升级后的办理人继续处理。
     */
    private void doEscalate(FlowRunTaskDO task, JSONObject slaConfig) {
        Long adminUserId = slaConfig.getLong("adminUserId");
        if (adminUserId == null) {
            log.warn("[FlowTimeout] ESCALATE 未配置 adminUserId taskId={} node={}，降级为标记超时",
                    task.getId(), task.getNodeCode());
            markTimeout(task, "SLA超时，ESCALATE 缺少 adminUserId");
            return;
        }
        String assigneeId = String.valueOf(adminUserId);
        taskMapper.updateAssignee(task.getId(), assigneeId,
                "SLA_ESCALATE", FlowAssigneeType.USER.name());
        log.info("[FlowTimeout] SLA 升级办理人 taskId={} instanceId={} 原办理人={} → adminUserId={}",
                task.getId(), task.getInstanceId(), task.getAssigneeId(), adminUserId);
    }

    /**
     * AUTO_PASS：自动通过任务并推进流程（P0-1 修复：真正联动 FlowAdvancer）
     *
     * <p>通过 FlowTaskService.pass() 完成任务并推进到下一节点，
     * 确保流程不会因 SLA 超时而卡死。使用系统用户身份执行，
     * 审计日志中记录"SLA 超时自动通过"。
     *
     * <p>容错策略：pass() 失败时降级为仅标记 COMPLETED（不推进），避免定时任务异常中断。
     */
    private void doAutoPass(FlowRunTaskDO task, JSONObject slaConfig) {
        // 二次校验：扫描窗口内任务可能已被人工处理
        FlowRunTaskDO latest = taskMapper.selectById(task.getId());
        if (latest == null) {
            log.warn("[FlowTimeout] AUTO_PASS 任务不存在 taskId={}", task.getId());
            return;
        }
        String status = latest.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            log.info("[FlowTimeout] 任务状态已变更，跳过 AUTO_PASS taskId={} status={}",
                    task.getId(), status);
            return;
        }

        // 构造系统用户操作 DTO，通过 taskService.pass() 完成任务 + 推进流程
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(latest.getId());
        dto.setUserId("0");
        dto.setUserName("SLA系统自动通过");
        dto.setAction("PASS");
        dto.setComment("SLA 超时自动通过");
        dto.setTenantId(latest.getTenantId());

        try {
            taskService.pass(dto);
            log.info("[FlowTimeout] SLA 自动通过并推进成功 taskId={} instanceId={} node={}",
                    latest.getId(), latest.getInstanceId(), latest.getNodeCode());
        } catch (Exception e) {
            log.error("[FlowTimeout] SLA 自动通过推进失败，降级为仅标记完成 taskId={} err={}",
                    latest.getId(), e.getMessage(), e);
            // 降级：至少标记为 COMPLETED，避免任务永久卡在 PENDING
            LocalDateTime now = LocalDateTime.now();
            Long durationMs = calcDuration(latest, now);
            taskMapper.completeTask(latest.getId(), FlowTaskStatus.COMPLETED.name(),
                    "SLA 超时自动通过(降级-推进失败)", now, durationMs);
        }
    }

    /**
     * AUTO_REJECT：自动驳回任务并终止流程实例
     *
     * <p>将当前任务标记为 REJECTED，取消实例下其余 PENDING 任务，
     * 并将实例状态推进为 TERMINATED。
     */
    private void doAutoReject(FlowRunTaskDO task, JSONObject slaConfig) {
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = calcDuration(task, now);
        // 1. 驳回当前任务
        taskMapper.completeTask(task.getId(), FlowTaskStatus.REJECTED.name(),
                "SLA 超时自动驳回", now, durationMs);
        // 2. 取消实例下其余活跃任务
        taskMapper.cancelByInstance(task.getInstanceId(), FlowTaskStatus.CANCELLED.name());
        // 3. 终止流程实例
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        long instanceDurationMs = instance == null || instance.getStartAt() == null
                ? 0L : Duration.between(instance.getStartAt(), now).toMillis();
        instanceMapper.updateStatus(task.getInstanceId(),
                FlowInstanceStatus.TERMINATED.name(),
                null, null, now, instanceDurationMs);
        log.info("[FlowTimeout] SLA 自动驳回并终止实例 taskId={} instanceId={} node={}",
                task.getId(), task.getInstanceId(), task.getNodeCode());
    }

    // ============================== 子流程超时检测 ==============================

    /**
     * 处理子流程实例超时：扫描 pmis_flow_instance 中 due_at 已超期且状态为 RUNNING 的子流程实例
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

    /**
     * 标记任务为 TIMEOUT（用于无 action / 解析失败等场景）
     *
     * <p>FlowRunTaskMapper 暂无 timeoutTask 专用方法，此处复用 completeTask
     * 以 {@link FlowTaskStatus#TIMEOUT} 状态完成，等价于服务层 timeoutTask 的底层操作。
     *
     * @param task   任务
     * @param reason 超时原因
     */
    private void markTimeout(FlowRunTaskDO task, String reason) {
        // 再次校验状态，避免重复处理（扫描窗口内任务可能已被人工处理）
        FlowRunTaskDO latest = taskMapper.selectById(task.getId());
        if (latest == null) {
            return;
        }
        String status = latest.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            log.info("[FlowTimeout] 任务状态已变更，跳过标记 taskId={} status={}",
                    task.getId(), status);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = calcDuration(latest, now);
        taskMapper.completeTask(latest.getId(), FlowTaskStatus.TIMEOUT.name(),
                reason, now, durationMs);
    }

    /** 计算任务耗时（毫秒），createdAt 缺失时返回 0 */
    private Long calcDuration(FlowRunTaskDO task, LocalDateTime now) {
        return task.getCreatedAt() == null ? 0L
                : Duration.between(task.getCreatedAt(), now).toMillis();
    }

    /** 安全查询节点（防御 null） */
    private FlowNodeDO safelySelectNode(FlowRunTaskDO task) {
        if (task.getDefinitionId() == null || task.getNodeCode() == null) {
            return null;
        }
        try {
            return nodeMapper.selectByCode(task.getDefinitionId(), task.getNodeCode());
        } catch (Exception e) {
            log.warn("[FlowTimeout] 查询节点失败 definitionId={} nodeCode={} err={}",
                    task.getDefinitionId(), task.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /** 从 paramsJson 解析 tenantId（可空） */
    private String parseTenantId(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(paramsJson);
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
