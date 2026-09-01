package com.njydsz.agent.server.trigger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerRepository;
import com.njydsz.agent.domain.trigger.TriggerType;

/**
 * 触发器评估服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据事件类型匹配已注册的触发器</li>
 *   <li>执行内容模式匹配（正则/包含）</li>
 *   <li>限速控制（每小时最大执行次数）</li>
 *   <li>去重守卫（同一事件不重复触发）</li>
 *   <li>递归防护（防止触发器链无限循环）</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 Triggers 系统设计，确保事件驱动架构的健壮性。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
public class TriggerEvaluationService {

    private final TriggerRepository triggerRepository;
    private final TriggerExecutionService executionService;

    /** 触发器每小时执行计数：triggerId -> 计数窗口 */
    private final ConcurrentHashMap<String, HourlyCounter> triggerCounters = new ConcurrentHashMap<>();

    /** 去重集合：记录已处理的事件指纹 */
    private final Set<String> deduplicationSet = ConcurrentHashMap.newKeySet();

    /** 递归深度跟踪：executionId -> 深度 */
    private final ConcurrentHashMap<String, AtomicInteger> recursionDepthMap = new ConcurrentHashMap<>();

    /** 最大递归深度 */
    private static final int MAX_RECURSION_DEPTH = 3;

    /** 去重窗口（分钟） */
    private static final int DEDUPLICATION_WINDOW_MINUTES = 5;

    public TriggerEvaluationService(TriggerRepository triggerRepository,
                                    TriggerExecutionService executionService) {
        this.triggerRepository = Objects.requireNonNull(triggerRepository, "triggerRepository 不能为 null");
        this.executionService = Objects.requireNonNull(executionService, "executionService 不能为 null");
    }

    /**
     * 评估内容匹配类型触发器。
     *
     * @param tenantId 租户 ID
     * @param content  待匹配的内容
     * @param context  触发上下文（用于去重和日志）
     */
    public void evaluateContentTriggers(String tenantId, String content, Map<String, Object> context) {
        List<AgentTrigger> triggers = triggerRepository.findByTenantAndType(tenantId, TriggerType.CONTENT_MATCH);
        for (AgentTrigger trigger : triggers) {
            evaluateAndExecute(trigger, content, context);
        }
    }

    /**
     * 评估 Agent 生命周期事件触发器。
     *
     * @param tenantId    租户 ID
     * @param eventType   事件类型（如 AGENT_EXECUTION_COMPLETED）
     * @param executionId 执行 ID
     * @param metadata    事件元数据
     */
    public void evaluateAgentLifecycleTriggers(String tenantId, String eventType,
                                                String executionId, Map<String, Object> metadata) {
        List<AgentTrigger> triggers = triggerRepository.findByTenantAndType(tenantId, TriggerType.AGENT_LIFECYCLE);
        for (AgentTrigger trigger : triggers) {
            if (matchesPattern(trigger, eventType)) {
                metadata.put("sourceExecutionId", executionId);
                metadata.put("sourceEventType", eventType);
                evaluateAndExecute(trigger, eventType, metadata);
            }
        }
    }

    /**
     * 评估渠道消息触发器。
     *
     * @param tenantId    租户 ID
     * @param channelType 渠道类型
     * @param message     消息内容
     * @param context     上下文信息
     */
    public void evaluateChannelMessageTriggers(String tenantId, String channelType,
                                                String message, Map<String, Object> context) {
        List<AgentTrigger> triggers = triggerRepository.findByTenantAndType(tenantId, TriggerType.CHANNEL_MESSAGE);
        for (AgentTrigger trigger : triggers) {
            if (matchesPattern(trigger, channelType + ":" + message)) {
                context.put("channelType", channelType);
                evaluateAndExecute(trigger, message, context);
            }
        }
    }

    /**
     * 评估 Webhook 触发器。
     *
     * @param tenantId    租户 ID
     * @param webhookPath Webhook 路径
     * @param payload     请求载荷
     */
    public void evaluateWebhookTrigger(String tenantId, String webhookPath, Map<String, Object> payload) {
        List<AgentTrigger> triggers = triggerRepository.findByTenantAndType(tenantId, TriggerType.WEBHOOK);
        for (AgentTrigger trigger : triggers) {
            if (matchesPattern(trigger, webhookPath)) {
                evaluateAndExecute(trigger, webhookPath, payload);
            }
        }
    }

    /**
     * 评估工作流完成触发器。
     *
     * @param tenantId      租户 ID
     * @param workflowId    工作流 ID
     * @param executionId   执行 ID
     * @param resultPayload 结果载荷
     */
    public void evaluateWorkflowCompletionTriggers(String tenantId, String workflowId,
                                                     String executionId, Map<String, Object> resultPayload) {
        List<AgentTrigger> triggers = triggerRepository.findByTenantAndType(tenantId, TriggerType.WORKFLOW_COMPLETION);
        for (AgentTrigger trigger : triggers) {
            if (matchesPattern(trigger, workflowId)) {
                resultPayload.put("workflowId", workflowId);
                resultPayload.put("sourceExecutionId", executionId);
                evaluateAndExecute(trigger, workflowId, resultPayload);
            }
        }
    }

    /**
     * 评估并执行触发器（核心方法）。
     *
     * @param trigger 待评估的触发器
     * @param input   输入内容
     * @param context 执行上下文
     */
    private void evaluateAndExecute(AgentTrigger trigger, String input, Map<String, Object> context) {
        String triggerId = trigger.getTriggerId();

        // 1. 启用状态检查
        if (!trigger.isEnabled()) {
            log.debug("[Trigger] 触发器已禁用，跳过: triggerId={}", triggerId);
            return;
        }

        // 2. 去重检查
        if (isDuplicate(triggerId, input)) {
            log.debug("[Trigger] 重复事件，跳过: triggerId={}", triggerId);
            return;
        }

        // 3. 限速检查
        if (isRateLimited(trigger)) {
            log.warn("[Trigger] 触发器超过限速阈值: triggerId={}, limit={}/h",
                    triggerId, trigger.getMaxExecutionsPerHour());
            return;
        }

        // 4. 递归深度检查
        if (isRecursionLimitReached(triggerId)) {
            log.warn("[Trigger] 递归深度超限: triggerId={}, maxDepth={}",
                    triggerId, MAX_RECURSION_DEPTH);
            return;
        }

        // 5. 执行触发器
        log.info("[Trigger] 触发器匹配成功: triggerId={}, name={}, input={}",
                triggerId, trigger.getName(), truncate(input, 100));

        incrementRecursionDepth(triggerId);
        try {
            executionService.executeTrigger(trigger, context);
            recordExecution(triggerId);
        } finally {
            decrementRecursionDepth(triggerId);
        }
    }

    /**
     * 检查输入是否匹配触发器的模式。
     *
     * @param trigger 触发器
     * @param input   输入内容
     * @return 是否匹配
     */
    private boolean matchesPattern(AgentTrigger trigger, String input) {
        String pattern = trigger.getMatchPattern();
        if (pattern == null || pattern.isBlank()) {
            return true; // 无模式则匹配所有
        }
        if (input == null) {
            return false;
        }
        try {
            return Pattern.compile(pattern).matcher(input).find();
        } catch (PatternSyntaxException e) {
            log.warn("[Trigger] 无效的正则表达式: triggerId={}, pattern={}",
                    trigger.getTriggerId(), pattern);
            return false;
        }
    }

    /**
     * 检查是否为重复事件。
     *
     * @param triggerId 触发器 ID
     * @param input     输入内容
     * @return 是否重复
     */
    private boolean isDuplicate(String triggerId, String input) {
        String fingerprint = triggerId + ":" + input.hashCode();
        return !deduplicationSet.add(fingerprint);
    }

    /**
     * 检查是否超过限速阈值。
     *
     * @param trigger 触发器
     * @return 是否被限速
     */
    private boolean isRateLimited(AgentTrigger trigger) {
        HourlyCounter counter = triggerCounters.computeIfAbsent(
                trigger.getTriggerId(), k -> new HourlyCounter());
        return counter.getCount() >= trigger.getMaxExecutionsPerHour();
    }

    /**
     * 记录一次执行。
     *
     * @param triggerId 触发器 ID
     */
    private void recordExecution(String triggerId) {
        HourlyCounter counter = triggerCounters.computeIfAbsent(
                triggerId, k -> new HourlyCounter());
        counter.increment();
    }

    /**
     * 检查递归深度是否超限。
     *
     * @param triggerId 触发器 ID
     * @return 是否超限
     */
    private boolean isRecursionLimitReached(String triggerId) {
        AtomicInteger depth = recursionDepthMap.get(triggerId);
        return depth != null && depth.get() >= MAX_RECURSION_DEPTH;
    }

    /**
     * 增加递归深度。
     *
     * @param triggerId 触发器 ID
     */
    private void incrementRecursionDepth(String triggerId) {
        recursionDepthMap.computeIfAbsent(triggerId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 减少递归深度。
     *
     * @param triggerId 触发器 ID
     */
    private void decrementRecursionDepth(String triggerId) {
        AtomicInteger depth = recursionDepthMap.get(triggerId);
        if (depth != null && depth.decrementAndGet() <= 0) {
            recursionDepthMap.remove(triggerId);
        }
    }

    /**
     * 清理过期的去重记录（由定时任务调用）。
     */
    public void cleanupDeduplicationSet() {
        // 简化实现：直接清空，实际可基于时间窗口清理
        if (deduplicationSet.size() > 10000) {
            log.info("[Trigger] 清理去重集合，当前大小: {}", deduplicationSet.size());
            deduplicationSet.clear();
        }
    }

    /**
     * 清理过期的限速计数器（由定时任务调用）。
     */
    public void cleanupCounters() {
        // 简化实现：每小时清理一次
        triggerCounters.clear();
    }

    /**
     * 截断字符串用于日志输出。
     *
     * @param str    原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    /**
     * 每小时计数器。
     */
    private static class HourlyCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private LocalDateTime windowStart = LocalDateTime.now();

        public int getCount() {
            // 检查是否需要重置窗口
            if (ChronoUnit.HOURS.between(windowStart, LocalDateTime.now()) >= 1) {
                count.set(0);
                windowStart = LocalDateTime.now();
            }
            return count.get();
        }

        public void increment() {
            count.incrementAndGet();
        }
    }
}
