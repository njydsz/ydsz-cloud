package com.njydsz.pmis.workflow.flow.job;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
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
 * <p>定时扫描 pmis_flow_task 中已超期（due_at &lt; now）且状态为 PENDING/CLAIMED 的待办任务，
 * 根据节点 sla_config 配置的 action 执行自动化处理：
 * <ul>
 *   <li>REMIND —— 发送催办通知（占位实现，记录审计日志）</li>
 *   <li>ESCALATE —— 升级办理人，将任务转交给 sla_config.adminUserId</li>
 *   <li>AUTO_PASS —— 自动通过任务并尝试推进（占位记录，真实推进需联动 FlowAdvancer）</li>
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
 * <p>说明：FlowTaskMapper 暂无 {@code timeoutTask} 专用方法，
 * 此处复用 {@link FlowTaskMapper#completeTask} 以 {@link FlowTaskStatus#TIMEOUT} 状态标记超时，
 * 与 {@code FlowTaskServiceImpl.timeoutTask} 内部实现保持一致。
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

    private final FlowTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowHisTaskMapper hisTaskMapper;

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
        Long tenantId = parseTenantId(paramsJson);

        // selectOverdue 已内置 deleted=0、task_status IN ('PENDING','CLAIMED')、due_at < now 过滤
        List<FlowTaskDO> overdueTasks;
        try {
            overdueTasks = taskMapper.selectOverdue(null, tenantId);
        } catch (Exception e) {
            log.error("[FlowTimeout] 查询超期任务失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (overdueTasks == null || overdueTasks.isEmpty()) {
            log.info("[FlowTimeout] 当前无超期任务");
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("processed", 0);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int processed = 0;
        int errors = 0;
        for (FlowTaskDO task : overdueTasks) {
            try {
                handleOverdueTask(task);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("[FlowTimeout] 处理超期任务异常 taskId={} instanceId={} err={}",
                        task.getId(), task.getInstanceId(), e.getMessage(), e);
            }
        }

        log.info("FlowTimeoutJobHandler: processed {} overdue tasks", processed);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("total", overdueTasks.size());
        result.put("processed", processed);
        result.put("errors", errors);
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    // ============================== 单任务处理 ==============================

    /**
     * 处理单个超期任务：读取节点 sla_config，按 action 分发
     *
     * @param task 超期任务
     */
    private void handleOverdueTask(FlowTaskDO task) {
        Long taskId = task.getId();
        Long instanceId = task.getInstanceId();
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
     * REMIND：发送催办通知（占位实现）
     *
     * <p>任务保持 PENDING/CLAIMED 不变，办理人仍可继续处理。
     * 此处仅记录审计日志与通知占位，生产环境应接入消息中心。
     */
    private void doRemind(FlowTaskDO task, JSONObject slaConfig) {
        int reminderCount = slaConfig.getIntValue("reminderCount", 1);
        // 占位：记录催办通知日志（生产环境接入消息中心 / 站内信 / 邮件）
        log.info("[FlowTimeout] SLA 催办提醒 taskId={} instanceId={} assignee={} reminderCount={}",
                task.getId(), task.getInstanceId(), task.getAssigneeId(), reminderCount);
        // 任务保持活跃，不标记超时，便于办理人继续处理
    }

    /**
     * ESCALATE：升级办理人
     *
     * <p>将任务办理人切换为 sla_config.adminUserId，任务保持活跃（PENDING/CLAIMED），
     * 由升级后的办理人继续处理。
     */
    private void doEscalate(FlowTaskDO task, JSONObject slaConfig) {
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
     * AUTO_PASS：自动通过任务
     *
     * <p>完成任务（COMPLETED）并记录。
     * 真实推进到下一节点需联动 FlowAdvancer / FlowInstanceService，
     * 此处完成占位并记录日志，避免在定时任务中引入复杂的事务与递归推进。
     */
    private void doAutoPass(FlowTaskDO task, JSONObject slaConfig) {
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = calcDuration(task, now);
        taskMapper.completeTask(task.getId(), FlowTaskStatus.COMPLETED.name(),
                "SLA 超时自动通过", now, durationMs);
        log.info("[FlowTimeout] SLA 自动通过 taskId={} instanceId={} node={} （后续推进需联动 Advancer）",
                task.getId(), task.getInstanceId(), task.getNodeCode());
    }

    /**
     * AUTO_REJECT：自动驳回任务并终止流程实例
     *
     * <p>将当前任务标记为 REJECTED，取消实例下其余 PENDING 任务，
     * 并将实例状态推进为 TERMINATED。
     */
    private void doAutoReject(FlowTaskDO task, JSONObject slaConfig) {
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

    // ============================== 私有辅助 ==============================

    /**
     * 标记任务为 TIMEOUT（用于无 action / 解析失败等场景）
     *
     * <p>FlowTaskMapper 暂无 timeoutTask 专用方法，此处复用 completeTask
     * 以 {@link FlowTaskStatus#TIMEOUT} 状态完成，等价于服务层 timeoutTask 的底层操作。
     *
     * @param task   任务
     * @param reason 超时原因
     */
    private void markTimeout(FlowTaskDO task, String reason) {
        // 再次校验状态，避免重复处理（扫描窗口内任务可能已被人工处理）
        FlowTaskDO latest = taskMapper.selectById(task.getId());
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
    private Long calcDuration(FlowTaskDO task, LocalDateTime now) {
        return task.getCreatedAt() == null ? 0L
                : Duration.between(task.getCreatedAt(), now).toMillis();
    }

    /** 安全查询节点（防御 null） */
    private FlowNodeDO safelySelectNode(FlowTaskDO task) {
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
    private Long parseTenantId(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(paramsJson);
            if (obj == null) {
                return null;
            }
            return obj.getLong("tenantId");
        } catch (Exception e) {
            log.warn("[FlowTimeout] 参数 JSON 解析失败，忽略 tenantId: {}", e.getMessage());
            return null;
        }
    }
}
