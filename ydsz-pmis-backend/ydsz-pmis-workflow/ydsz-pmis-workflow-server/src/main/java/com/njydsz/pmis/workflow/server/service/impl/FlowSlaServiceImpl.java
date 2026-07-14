package com.njydsz.pmis.workflow.server.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.FlowSlaAction;
import com.njydsz.pmis.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.engine.FlowClusterLockHelper;
import com.njydsz.pmis.workflow.server.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.server.service.FlowSlaService;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程 SLA 超时自动策略实现
 *
 * <p>P1-6 实现：
 * <ol>
 *   <li>cronjob 每 60s 扫描所有 PENDING/CLAIMED 且 dueAt 不为空的 task</li>
 *   <li>解析 node.slaConfig 配置：timeoutMinutes / action / reminderIntervalMinutes / maxReminders / escalateUserId</li>
 *   <li>未到 dueAt：跳过；超过 dueAt 但未到最终动作：根据 maxReminders 重复 REMIND</li>
 *   <li>超过 dueAt 且已超出 reminder 容忍窗口：执行最终动作（ESCALATE / AUTO_PASS / AUTO_REJECT）</li>
 *   <li>所有写操作都在 REQUIRES_NEW 子事务中，单条失败不影响扫描主循环</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSlaServiceImpl implements FlowSlaService {

    /** 运行时任务 Mapper，查询超期待办及更新提醒计数 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程节点 Mapper，读取节点 SLA 配置（slaConfig JSON） */
    private final FlowNodeMapper nodeMapper;
    /** P1-6: 用 @Lazy 打破 FlowSlaService ↔ FlowTaskService 循环依赖 */
    @Lazy
    private final FlowTaskService taskService;
    private final FlowNotificationHelper notificationHelper;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** P0-2: 集群调度分布式锁辅助 */
    private final FlowClusterLockHelper clusterLockHelper;

    /** 单次扫描上限（避免大表全表扫描） */
    private static final int SCAN_BATCH_SIZE = 500;

    /** 默认 SLA 配置（节点未配 slaConfig 时使用） */
    private static final int DEFAULT_REMINDER_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_MAX_REMINDERS = 3;
    private static final int DEFAULT_TIMEOUT_MINUTES = 24 * 60;
    private static final String DEFAULT_ADMIN_USER_ID = "1";

    @Override
    public Map<String, Object> parseSlaConfig(String slaConfigJson) {
        if (!StringUtils.hasText(slaConfigJson)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(slaConfigJson);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[FlowSla] 解析 slaConfig 失败: {} err={}", slaConfigJson, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public void applySlaConfig(FlowRunTaskDO task, FlowNodeDO node) {
        if (task == null || node == null) {
            return;
        }
        Map<String, Object> config = parseSlaConfig(node.getSlaConfig());
        if (config.isEmpty()) {
            return; // 未配置 SLA
        }
        Integer timeoutMinutes = readInt(config, "timeoutMinutes", null);
        if (timeoutMinutes == null || timeoutMinutes <= 0) {
            return; // 必须配置 timeoutMinutes 才算开启 SLA
        }
        LocalDateTime dueAt = task.getCreatedAt() == null
                ? LocalDateTime.now().plusMinutes(timeoutMinutes)
                : task.getCreatedAt().plusMinutes(timeoutMinutes);
        task.setDueAt(dueAt);
        // 记录 slaAction 预期值（仅用于审计，不强制）
        String actionStr = (String) config.get("action");
        if (StringUtils.hasText(actionStr)) {
            try {
                FlowSlaAction action = FlowSlaAction.valueOf(actionStr.toUpperCase());
                task.setSlaAction(action.name());
            } catch (IllegalArgumentException e) {
                log.warn("[FlowSla] 未知的 SLA action: nodeCode={} action={}",
                        node.getNodeCode(), actionStr);
            }
        }
        log.info("[FlowSla] 应用 SLA 配置: taskId={} nodeCode={} timeoutMinutes={} action={} dueAt={}",
                task.getId(), node.getNodeCode(), timeoutMinutes,
                config.get("action"), dueAt);
    }

    @Override
    public int scanAndProcess() {
        try {
            List<FlowRunTaskDO> candidates = taskMapper.selectSlaCandidates(SCAN_BATCH_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                return 0;
            }
            LocalDateTime now = LocalDateTime.now();
            int processed = 0;
            for (FlowRunTaskDO task : candidates) {
                try {
                    if (processOverdue(task, now)) {
                        processed++;
                    }
                } catch (Exception e) {
                    log.error("[FlowSla] 单条处理异常: taskId={} err={}",
                            task.getId(), e.getMessage(), e);
                }
            }
            if (processed > 0) {
                log.info("[FlowSla] 本轮扫描处理: count={}", processed);
            }
            return processed;
        } catch (Exception e) {
            log.error("[FlowSla] 扫描异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean processOverdue(FlowRunTaskDO task) {
        return processOverdue(task, LocalDateTime.now());
    }

    /**
     * 内部方法：传入 now 以便测试和复用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean processOverdue(FlowRunTaskDO task, LocalDateTime now) {
        if (task == null || task.getId() == null) {
            return false;
        }
        // 1. 重新查一遍任务，避免读到陈旧数据
        FlowRunTaskDO fresh = taskMapper.selectById(task.getId());
        if (fresh == null) {
            return false;
        }
        if (!"PENDING".equals(fresh.getTaskStatus())
                && !"CLAIMED".equals(fresh.getTaskStatus())) {
            return false; // 已完成
        }
        if (fresh.getDueAt() == null) {
            return false; // 未配置 SLA
        }
        // 2. 未到 dueAt，跳过
        if (fresh.getDueAt().isAfter(now)) {
            return false;
        }
        // 3. 解析节点 SLA 配置
        FlowNodeDO node = nodeMapper.selectByCode(fresh.getDefinitionId(), fresh.getNodeCode());
        Map<String, Object> config = node == null
                ? Collections.emptyMap()
                : parseSlaConfig(node.getSlaConfig());
        // 无配置：默认仅 NOTIFY（但因 FlowSlaService 只对配了 dueAt 的任务扫描，这种情况不应出现）
        if (config.isEmpty()) {
            log.warn("[FlowSla] 任务已超期但无 SLA 配置: taskId={} nodeCode={}",
                    fresh.getId(), fresh.getNodeCode());
            return false;
        }
        String actionStr = ((String) config.getOrDefault("action", "REMIND")).toUpperCase();
        FlowSlaAction action;
        try {
            action = FlowSlaAction.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            log.warn("[FlowSla] 未知 action: taskId={} action={}", fresh.getId(), actionStr);
            return false;
        }
        int maxReminders = readInt(config, "maxReminders", DEFAULT_MAX_REMINDERS);
        int reminderIntervalMin = readInt(config, "reminderIntervalMinutes",
                DEFAULT_REMINDER_INTERVAL_MINUTES);
        int currentReminders = fresh.getReminderCount() == null ? 0 : fresh.getReminderCount();
        LocalDateTime lastRemindedAt = fresh.getLastRemindedAt();
        // 4. 距离最后一次提醒未到间隔，不重复提醒
        if (lastRemindedAt != null
                && Duration.between(lastRemindedAt, now).toMinutes() < reminderIntervalMin) {
            return false;
        }
        // 5. 已达最大提醒次数：执行最终动作
        if (currentReminders >= maxReminders) {
            return executeFinalAction(fresh, node, action, config, now);
        }
        // 6. 未达最大提醒次数：先发一次提醒，再决定
        boolean reminded = sendReminder(fresh, action, currentReminders + 1, maxReminders, now);
        if (reminded) {
            taskMapper.incrementReminderCount(fresh.getId(), currentReminders + 1, now);
        }
        return reminded;
    }

    /**
     * 发送 SLA 提醒
     *
     * @return true=已发送，false=跳过（无 assignee 等）
     */
    private boolean sendReminder(FlowRunTaskDO task, FlowSlaAction action, int newReminderCount,
                                  int maxReminders, LocalDateTime now) {
        try {
            String title = "审批任务即将超时";
            String content = String.format("【%s】%s 已超过截止时间 %s，请尽快处理（第 %d/%d 次提醒）",
                    nullSafe(task.getFlowName()),
                    nullSafe(task.getNodeName()),
                    task.getDueAt(),
                    newReminderCount,
                    maxReminders);
            String receiverId = task.getAssigneeId();
            if (receiverId == null) {
                log.warn("[FlowSla] 无法解析 assigneeId: taskId={} assigneeId={}",
                        task.getId(), task.getAssigneeId());
                return false;
            }
            notificationHelper.notifyTaskTimeout(receiverId, title, content, task.getId());
            log.info("[FlowSla] 发送 SLA 提醒: taskId={} receiver={} count={}/{} action={}",
                    task.getId(), receiverId, newReminderCount, maxReminders, action);
            return true;
        } catch (Exception e) {
            log.warn("[FlowSla] 提醒发送失败: taskId={} err={}", task.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 执行最终动作（NOTIFY / AUTO_PASS / AUTO_REJECT / ESCALATE）
     *
     * <p>P1-3 闭环语义：每个 action 必须有明确终态，禁止"标记 TIMEOUT 但流程卡死"。
     * <ul>
     *   <li>NOTIFY     — 通知管理员介入，任务保持 PENDING（人工处理）</li>
     *   <li>ESCALATE   — 转办给 escalateUserId，任务保持 PENDING（新办理人处理）</li>
     *   <li>AUTO_PASS  — 系统自动通过，流程推进到下一节点</li>
     *   <li>AUTO_REJECT — 系统自动驳回，流程终止</li>
     *   <li>REMIND     — 兼容旧配置，等同于 NOTIFY（不再标记 TIMEOUT）</li>
     * </ul>
     */
    private boolean executeFinalAction(FlowRunTaskDO task, FlowNodeDO node,
                                        FlowSlaAction action, Map<String, Object> config,
                                        LocalDateTime now) {
        log.info("[FlowSla] 触发最终动作: taskId={} action={}", task.getId(), action);
        switch (action) {
            case REMIND:
                // P1-3: 兼容旧配置 — REMIND 作为最终动作时等同于 NOTIFY
                // （不再调用 doAutoTimeout 标记任务为 TIMEOUT，那会让流程卡死）
                return doNotify(task, config, now);
            case NOTIFY:
                return doNotify(task, config, now);
            case AUTO_PASS:
                return doAutoPass(task, config, now);
            case AUTO_REJECT:
                return doAutoReject(task, config, now);
            case ESCALATE:
                return doEscalate(task, config, now);
            default:
                log.warn("[FlowSla] 未知最终动作: action={}", action);
                return false;
        }
    }

    /**
     * 自动通过：以系统身份调用 pass()
     */
    private boolean doAutoPass(FlowRunTaskDO task, Map<String, Object> config, LocalDateTime now) {
        try {
            String comment = (String) config.getOrDefault("autoComment",
                    "系统自动通过：超过 SLA 时限未处理");
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(task.getId());
            dto.setUserId("0"); // 0 = 系统用户
            dto.setComment(comment);
            dto.setVariables(Collections.emptyMap());
            taskService.pass(dto);
            taskMapper.markSlaAction(task.getId(), FlowSlaAction.AUTO_PASS.name(), 0);
            log.info("[FlowSla] 自动通过: taskId={} comment={}", task.getId(), comment);
            // P2-3: Prometheus 指标
            if (flowMetrics != null) {
                flowMetrics.incSlaTimeout(task.getFlowCode(), "AUTO_PASS");
                flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "AUTO_PASS");
            }
            return true;
        } catch (Exception e) {
            log.error("[FlowSla] 自动通过失败: taskId={} err={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 自动驳回：以系统身份调用 reject()
     */
    private boolean doAutoReject(FlowRunTaskDO task, Map<String, Object> config, LocalDateTime now) {
        try {
            String comment = (String) config.getOrDefault("autoComment",
                    "系统自动驳回：超过 SLA 时限未处理");
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(task.getId());
            dto.setUserId("0");
            dto.setComment(comment);
            dto.setVariables(Collections.emptyMap());
            taskService.reject(dto);
            taskMapper.markSlaAction(task.getId(), FlowSlaAction.AUTO_REJECT.name(), 0);
            log.info("[FlowSla] 自动驳回: taskId={} comment={}", task.getId(), comment);
            // P2-3: Prometheus 指标
            if (flowMetrics != null) {
                flowMetrics.incSlaTimeout(task.getFlowCode(), "AUTO_REJECT");
                flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "AUTO_REJECT");
            }
            return true;
        } catch (Exception e) {
            log.error("[FlowSla] 自动驳回失败: taskId={} err={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 升级：转办给 escalateUserId（默认管理员）
     */
    private boolean doEscalate(FlowRunTaskDO task, Map<String, Object> config, LocalDateTime now) {
        try {
            if (task.getSlaEscalated() != null && task.getSlaEscalated() == 1) {
                log.info("[FlowSla] 任务已升级，跳过重复升级: taskId={}", task.getId());
                return false;
            }
            String escalateUserId = readString(config, "escalateUserId", null);
            if (escalateUserId == null) {
                escalateUserId = DEFAULT_ADMIN_USER_ID;
            }
            String comment = String.format("系统升级：原办理人未在 SLA 时限内处理，已转办给用户 %s",
                    escalateUserId);
            // 通过转办接口将任务转给升级用户
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(task.getId());
            dto.setUserId("0");
            dto.setTargetUserId(escalateUserId);
            dto.setComment(comment);
            dto.setVariables(Collections.emptyMap());
            // 标记升级；使用 transfer 接口
            try {
                taskService.transfer(dto);
                taskMapper.markSlaAction(task.getId(), FlowSlaAction.ESCALATE.name(), 1);
                // 转办后：升级后的任务重新计 SLA
                FlowRunTaskDO afterTransfer = taskMapper.selectById(task.getId());
                if (afterTransfer != null) {
                    afterTransfer.setSlaEscalated(1);
                    afterTransfer.setReminderCount(0);
                    afterTransfer.setLastRemindedAt(null);
                    // 给新任务一个新的 dueAt（基于当前时间 + timeoutMinutes）
                    Integer timeoutMinutes = readInt(config, "timeoutMinutes",
                            DEFAULT_TIMEOUT_MINUTES);
                    afterTransfer.setDueAt(now.plusMinutes(timeoutMinutes));
                    taskMapper.updateById(afterTransfer);
                }
                log.info("[FlowSla] 升级成功: taskId={} escalateUserId={}", task.getId(), escalateUserId);
                // P2-3: Prometheus 指标
                if (flowMetrics != null) {
                    flowMetrics.incSlaTimeout(task.getFlowCode(), "ESCALATE");
                    flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "ESCALATE");
                }
                return true;
            } catch (Exception transferEx) {
                // transfer 失败时降级：仅通知目标用户，标记升级
                log.warn("[FlowSla] 转办失败，改用通知: taskId={} err={}",
                        task.getId(), transferEx.getMessage());
                notificationHelper.notifyTaskAssigned(escalateUserId,
                        "审批任务已升级", comment, task.getId(),
                        "WORKFLOW_TASK_ESCALATED", "URGENT");
                taskMapper.markSlaAction(task.getId(), FlowSlaAction.ESCALATE.name(), 1);
                return true;
            }
        } catch (Exception e) {
            log.error("[FlowSla] 升级失败: taskId={} err={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * P1-3: NOTIFY 最终动作 — 通知管理员/升级人介入，任务保持 PENDING（闭环：等人工处理）
     *
     * <p>通知目标解析顺序：
     * <ol>
     *   <li>{@code notifyUserIds} 配置（逗号分隔的多用户，最高优先级）</li>
     *   <li>{@code escalateUserId} 配置（单用户，与 ESCALATE 共用字段）</li>
     *   <li>默认管理员（{@link #DEFAULT_ADMIN_USER_ID}）</li>
     * </ol>
     *
     * <p>任务状态不变（仍为 PENDING/CLAIMED），由人工处理后流程自然推进。
     * 与原 {@code doAutoTimeout} 的区别：不标记 TIMEOUT 终态，避免流程卡死。
     *
     * @param task   超时任务
     * @param config SLA 配置
     * @param now    当前时间
     * @return true=通知已发送；false=发送异常
     */
    private boolean doNotify(FlowRunTaskDO task, Map<String, Object> config, LocalDateTime now) {
        try {
            List<String> targets = resolveNotifyTargets(config);
            String title = "审批任务 SLA 超时需人工介入";
            String content = String.format(
                    "【%s】%s 已超过 SLA 时限未处理（任务 ID=%s，办理人=%s），请尽快介入处理。",
                    nullSafe(task.getFlowName()),
                    nullSafe(task.getNodeName()),
                    task.getId(),
                    nullSafe(task.getAssigneeId()));
            notificationHelper.notifyUrge(targets, title, content, task.getInstanceId());
            // 标记 slaAction=NOTIFY（slaEscalated=0 表示任务仍活跃，未转办）
            taskMapper.markSlaAction(task.getId(), FlowSlaAction.NOTIFY.name(), 0);
            log.info("[FlowSla] NOTIFY 通知已发送: taskId={} targets={} flowCode={} nodeCode={}",
                    task.getId(), targets, task.getFlowCode(), task.getNodeCode());
            // P2-3: Prometheus 指标
            if (flowMetrics != null) {
                flowMetrics.incSlaTimeout(task.getFlowCode(), "NOTIFY");
                flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "NOTIFY");
            }
            return true;
        } catch (Exception e) {
            log.error("[FlowSla] NOTIFY 通知失败: taskId={} err={}",
                    task.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 解析 NOTIFY 通知目标列表
     *
     * <p>优先级：notifyUserIds（逗号分隔）→ escalateUserId → 默认管理员
     */
    private List<String> resolveNotifyTargets(Map<String, Object> config) {
        String notifyUserIds = readString(config, "notifyUserIds", null);
        if (StringUtils.hasText(notifyUserIds)) {
            String[] ids = notifyUserIds.split(",");
            List<String> targets = new ArrayList<>(ids.length);
            for (String id : ids) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    targets.add(trimmed);
                }
            }
            if (!targets.isEmpty()) {
                return targets;
            }
        }
        String escalateUserId = readString(config, "escalateUserId", null);
        if (StringUtils.hasText(escalateUserId)) {
            return Collections.singletonList(escalateUserId);
        }
        return Collections.singletonList(DEFAULT_ADMIN_USER_ID);
    }

    /**
     * 每 60s 扫描一次（与 FlowTimerService 错峰 — FlowTimerService 30s, FlowSlaService 60s）
     *
     * <p>P0-2: 使用 Redisson 分布式锁包装，多节点部署时只有一个节点执行扫描。
     * 锁持有时间 55s（略小于 fixedDelay 60s），保证下次扫描前锁已释放。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 90_000L)
    public void scheduledScan() {
        clusterLockHelper.tryRun("sla:scan", 55, this::scanAndProcess);
    }

    // ============================== 辅助方法 ==============================

    private int readInt(Map<String, Object> config, String key, int defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String readString(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        return String.valueOf(val);
    }

    private Integer readInt(Map<String, Object> config, String key, Integer defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
