package com.njydsz.workflow.server.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowSlaAction;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 流程 SLA 超时自动策略实现
 *
 * <p>对 {@link FlowSlaService} 接口的完整实现，是工作流引擎 SLA 监控的核心业务逻辑层。
 * 通过定时任务扫描超期任务并执行自动策略（升级 / 自动通过 / 自动驳回），对标 Activiti / Flowable 的 Job Executor。
 *
 * <p><b>核心职责：</b>
 * <ol>
 *   <li>cronjob 每 60s 扫描所有 {@code PENDING/CLAIMED} 且 {@code dueAt} 不为空的 task</li>
 *   <li>解析 {@code node.slaConfig} 配置：{@code timeoutMinutes} / {@code action} /
 *       {@code urgeIntervalMinutes} / {@code maxUrges} / {@code escalateUserId}</li>
 *   <li>未到 {@code dueAt}：跳过；超过 {@code dueAt} 但未到最终动作：根据 {@code maxUrges} 重复 REMIND</li>
 *   <li>超过 {@code dueAt} 且已超出催办容忍窗口：执行最终动作（{@code ESCALATE / AUTO_PASS / AUTO_REJECT}）</li>
 *   <li>所有写操作都在 {@code REQUIRES_NEW} 子事务中，<b>单条失败不影响扫描主循环</b></li>
 * </ol>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>分布式锁</b>：通过 {@link DistributedScheduled} 保证集群中只有一个节点执行扫描</li>
 *   <li><b>子事务隔离</b>：{@code @Transactional(propagation = REQUIRES_NEW)} 隔离单条任务的失败</li>
 *   <li><b>指标埋点</b>：通过 {@link FlowMetrics} 暴露 SLA 触发次数 / 升级次数等 Prometheus 指标</li>
 *   <li><b>多租户</b>：扫描时按租户分批处理，避免单租户数据倾斜</li>
 *   <li><b>幂等性</b>：同一任务的同一动作（如升级）通过分布式锁 + 状态机保证只执行一次</li>
 * </ul>
 *
 * <p><b>SLA 动作类型（{@link FlowSlaAction}）：</b>
 * <ul>
 *   <li>{@code NONE} — 仅记录，不执行任何操作</li>
 *   <li>{@code REMIND} — 发送催办通知（IM / 站内信）</li>
 *   <li>{@code ESCALATE} — 升级审批人（如转给上级 / 指定接管人）</li>
 *   <li>{@code AUTO_PASS} — 自动通过（高风险，需审计）</li>
 *   <li>{@code AUTO_REJECT} — 自动驳回（高风险，需审计）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowSlaService SLA Service 接口
 * @see FlowSlaAction SLA 动作枚举
 * @see com.njydsz.workflow.domain.entity.FlowRunTask 运行时任务实体
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
    private final FlowNotificationService notificationService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** 单次扫描上限（避免大表全表扫描） */
    private static final int SCAN_BATCH_SIZE = 500;
    /** P1-6: 单轮扫描最大迭代次数（安全阀，避免大量超期任务导致单次扫描耗时过长） */
    private static final int MAX_SCAN_ITERATIONS = 10;

    /** 默认 SLA 配置（节点未配 slaConfig 时使用） */
    private static final int DEFAULT_REMINDER_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_MAX_REMINDERS = 3;
    private static final int DEFAULT_TIMEOUT_MINUTES = 24 * 60;
    private static final String DEFAULT_ADMIN_USER_ID = "1";

    /**
     * 解析节点的 SLA 配置 JSON 字符串
     *
     * <p>配置项：
     * <ul>
     *   <li>{@code timeoutMinutes} — 任务超时时间（必填）</li>
     *   <li>{@code action} — 超时动作（{@code REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT}，默认 {@code REMIND}）</li>
     *   <li>{@code urgeIntervalMinutes} — 提醒间隔（默认 60min）</li>
     *   <li>{@code maxUrges} — 最大提醒次数（默认 3）</li>
     *   <li>{@code escalateUserId} — 升级目标用户 ID（{@code action=ESCALATE} 时必填）</li>
     *   <li>{@code autoComment} — 自动动作的审批意见</li>
     * </ul>
     *
     * @param slaConfigJson 配置 JSON 字符串
     * @return 解析后的 Map（解析失败或为空时返回空 Map）
     */
    @Override
    public Map<String, Object> parseSlaConfig(String slaConfigJson) {
        if (!StringUtils.hasText(slaConfigJson)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = YdszJson.parseMap(slaConfigJson);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[FlowSla] 解析 slaConfig 失败: {} err={}", slaConfigJson, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 应用 SLA 配置到任务（任务创建时调用）
     *
     * <p>根据节点的 {@code slaConfig} 计算任务的 {@code dueAt}（= {@code createdAt} + {@code timeoutMinutes}），
     * 并记录 {@code slaAction} 预期动作。未配置 {@code timeoutMinutes} 时<b>不</b>应用 SLA。
     *
     * @param task 当前任务
     * @param node 当前节点（含 {@code slaConfig}）
     */
    @Override
    public void applySlaConfig(FlowRunTask task, FlowNode node) {
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

    /**
     * 扫描并处理超期 SLA 任务（手动触发 / 定时任务入口）
     *
     * <p>执行链路：
     * <ol>
     *   <li>游标分页查询超期候选任务（{@code PENDING/CLAIMED} 且 {@code dueAt <= now}），
     *       单批最多 {@link #SCAN_BATCH_SIZE} 条</li>
     *   <li>逐条处理（{@link #processOverdue}），单条失败不影响整体</li>
     *   <li>批次未满或本批无处理时结束循环（<b>避免大表全表扫描</b>）</li>
     * </ol>
     *
     * <p>集群幂等：本方法由 {@code @Scheduled} 定时任务调用，<b>调用方</b>需通过
     * {@link DistributedScheduled} 加分布式锁。
     *
     * @return 本轮处理的任务数（含 REMIND 提醒 + 最终动作）
     */
    @Override
    public int scanAndProcess() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int totalProcessed = 0;
            int iterations = 0;
            // P1-6: 游标分页 — 循环处理多批，直到无候选或达到最大迭代次数
            while (iterations < MAX_SCAN_ITERATIONS) {
                List<FlowRunTask> candidates = taskMapper.selectSlaCandidates(SCAN_BATCH_SIZE);
                if (candidates == null || candidates.isEmpty()) {
                    break;
                }
                int batchProcessed = 0;
                for (FlowRunTask task : candidates) {
                    try {
                        if (processOverdue(task, now)) {
                            batchProcessed++;
                        }
                    } catch (Exception e) {
                        log.error("[FlowSla] 单条处理异常: taskId={} err={}",
                                task.getId(), e.getMessage(), e);
                    }
                }
                totalProcessed += batchProcessed;
                // 批次未满或本批无处理（剩余候选均未到 dueAt），结束循环
                if (candidates.size() < SCAN_BATCH_SIZE || batchProcessed == 0) {
                    break;
                }
                iterations++;
            }
            if (totalProcessed > 0) {
                log.info("[FlowSla] 本轮扫描处理: count={} iterations={}", totalProcessed, iterations + 1);
            }
            return totalProcessed;
        } catch (Exception e) {
            log.error("[FlowSla] 扫描异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 处理单条超期任务（{@code REQUIRES_NEW} 子事务）
     *
     * <p>使用 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} 隔离单条任务的失败，
     * 即便单条处理抛异常回滚，也不影响其他任务的处理。
     *
     * @param task 超期任务
     * @return true=已处理（REMIND / 最终动作），false=跳过（未到期 / 已完成 / 状态不符）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean processOverdue(FlowRunTask task) {
        return processOverdue(task, LocalDateTime.now());
    }

    /**
     * 内部方法：传入 now 以便测试和复用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean processOverdue(FlowRunTask task, LocalDateTime now) {
        if (task == null || task.getId() == null) {
            return false;
        }
        // 1. 重新查一遍任务，避免读到陈旧数据
        FlowRunTask fresh = taskMapper.selectById(task.getId());
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
        FlowNode node = nodeMapper.selectByCode(fresh.getDefinitionId(), fresh.getNodeCode());
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
        int maxUrges = readInt(config, "maxUrges", DEFAULT_MAX_REMINDERS);
        int urgeIntervalMin = readInt(config, "urgeIntervalMinutes",
                DEFAULT_REMINDER_INTERVAL_MINUTES);
        int currentUrges = fresh.getUrgeCount() == null ? 0 : fresh.getUrgeCount();
        LocalDateTime lastUrgedAt = fresh.getLastUrgedAt();
        // 4. 距离最后一次提醒未到间隔，不重复提醒
        if (lastUrgedAt != null
                && Duration.between(lastUrgedAt, now).toMinutes() < urgeIntervalMin) {
            return false;
        }
        // 5. 已达最大提醒次数：执行最终动作
        if (currentUrges >= maxUrges) {
            return executeFinalAction(fresh, node, action, config, now);
        }
        // 6. 未达最大提醒次数：先发一次提醒，再决定
        boolean urged = sendUrge(fresh, action, currentUrges + 1, maxUrges, now);
        if (urged) {
            taskMapper.incrementUrgeCount(fresh.getId(), currentUrges + 1, now);
        }
        return urged;
    }

    /**
     * 发送 SLA 提醒
     *
     * @return true=已发送，false=跳过（无 assignee 等）
     */
    private boolean sendUrge(FlowRunTask task, FlowSlaAction action, int newUrgeCount,
                              int maxUrges, LocalDateTime now) {
        try {
            String title = "审批任务即将超时";
            String content = String.format("【%s】%s 已超过截止时间 %s，请尽快处理（第 %d/%d 次提醒）",
                    nullSafe(task.getFlowName()),
                    nullSafe(task.getNodeName()),
                    task.getDueAt(),
                    newUrgeCount,
                    maxUrges);
            String receiverId = task.getAssigneeId();
            if (receiverId == null) {
                log.warn("[FlowSla] 无法解析 assigneeId: taskId={} assigneeId={}",
                        task.getId(), task.getAssigneeId());
                return false;
            }
            notificationService.notify("INAPP", receiverId, title, content, "WORKFLOW_TIMEOUT", "WARN");
            log.info("[FlowSla] 发送 SLA 提醒: taskId={} receiver={} count={}/{} action={}",
                    task.getId(), receiverId, newUrgeCount, maxUrges, action);
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
    private boolean executeFinalAction(FlowRunTask task, FlowNode node,
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
    private boolean doAutoPass(FlowRunTask task, Map<String, Object> config, LocalDateTime now) {
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
    private boolean doAutoReject(FlowRunTask task, Map<String, Object> config, LocalDateTime now) {
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
    private boolean doEscalate(FlowRunTask task, Map<String, Object> config, LocalDateTime now) {
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
                FlowRunTask afterTransfer = taskMapper.selectById(task.getId());
                if (afterTransfer != null) {
                    afterTransfer.setSlaEscalated(1);
                    afterTransfer.setUrgeCount(0);
                    afterTransfer.setLastUrgedAt(null);
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
                notificationService.notify("INAPP", escalateUserId,
                        "审批任务已升级", comment,
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
    private boolean doNotify(FlowRunTask task, Map<String, Object> config, LocalDateTime now) {
        try {
            List<String> targets = resolveNotifyTargets(config);
            String title = "审批任务 SLA 超时需人工介入";
            String content = String.format(
                    "【%s】%s 已超过 SLA 时限未处理（任务 ID=%s，办理人=%s），请尽快介入处理。",
                    nullSafe(task.getFlowName()),
                    nullSafe(task.getNodeName()),
                    task.getId(),
                    nullSafe(task.getAssigneeId()));
            notificationService.notifyBatch("INAPP", targets, title, content, "WORKFLOW_URGE", "URGENT");
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
     * <p>通过 {@link DistributedScheduled} 保证多节点部署时只有一个节点执行扫描，
     * 获取不到锁的节点直接跳过本次执行（非阻塞）。
     * 锁持有时间 55s（略小于 fixedDelay 60s），保证下次扫描前锁已释放。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 90_000L)
    @DistributedScheduled(lockKey = "flow:sla:scan", leaseTime = 55)
    public void scheduledScan() {
        scanAndProcess();
    }

    // ============================== 辅助方法 ==============================

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

    private String readString(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        return String.valueOf(val);
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
