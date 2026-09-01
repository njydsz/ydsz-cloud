package com.njydsz.agent.server.trigger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerRepository;

/**
 * 定时触发器调度器。
 *
 * <p>定期扫描所有启用的 CRON 类型触发器，根据 cron 表达式判断是否到达执行时间。
 * 简化实现：每分钟扫描一次，检查 cron 表达式是否匹配当前时间。</p>
 *
 * <p>注意：此为轻量级实现，生产环境建议迁移至 Quartz 或 Spring Scheduling 的动态 cron 注册。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class CronTriggerScheduler {

    private final TriggerRepository triggerRepository;
    private final TriggerEvaluationService evaluationService;

    /** 记录每个触发器上次执行时间，防止同一分钟内重复执行 */
    private final ConcurrentHashMap<String, LocalDateTime> lastExecutionTimes = new ConcurrentHashMap<>();

    /** 最小执行间隔（秒），防止同一分钟内多次触发 */
    private static final long MIN_EXECUTION_INTERVAL_SECONDS = 55;

    /** 标准 cron 表达式字段数量 */
    private static final int CRON_FIELD_COUNT = 5;

    /** cron 表达式分钟字段下标 */
    private static final int CRON_MINUTE_INDEX = 0;

    /** cron 表达式小时字段下标 */
    private static final int CRON_HOUR_INDEX = 1;

    /** cron 表达式日字段下标 */
    private static final int CRON_DAY_OF_MONTH_INDEX = 2;

    /** cron 表达式月字段下标 */
    private static final int CRON_MONTH_INDEX = 3;

    /** cron 表达式周字段下标 */
    private static final int CRON_DAY_OF_WEEK_INDEX = 4;

    /** 分钟字段最大值 */
    private static final int MINUTE_MAX = 59;

    /** 小时字段最大值 */
    private static final int HOUR_MAX = 23;

    /** 日字段最大值 */
    private static final int DAY_OF_MONTH_MAX = 31;

    /** 月字段最大值 */
    private static final int MONTH_MAX = 12;

    /** 周字段最大值 */
    private static final int DAY_OF_WEEK_MAX = 7;

    public CronTriggerScheduler(TriggerRepository triggerRepository,
                                TriggerEvaluationService evaluationService) {
        this.triggerRepository = Objects.requireNonNull(triggerRepository, "triggerRepository 不能为 null");
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService 不能为 null");
    }

    /**
     * 扫描并执行到期的定时触发器。
     *
     * <p>此方法由 @Scheduled 每分钟调用一次。</p>
     */
    public void scanAndExecuteCronTriggers() {
        try {
            List<AgentTrigger> cronTriggers = triggerRepository.findAllEnabledCronTriggers();
            if (cronTriggers.isEmpty()) {
                return;
            }

            log.debug("[CronScheduler] 扫描到 {} 个启用的定时触发器", cronTriggers.size());
            LocalDateTime now = LocalDateTime.now();

            for (AgentTrigger trigger : cronTriggers) {
                if (shouldExecute(trigger, now)) {
                    executeCronTrigger(trigger, now);
                }
            }
        } catch (Exception e) {
            log.error("[CronScheduler] 扫描定时触发器异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断触发器是否应该在当前时间执行。
     *
     * @param trigger 触发器
     * @param now     当前时间
     * @return 是否应该执行
     */
    private boolean shouldExecute(AgentTrigger trigger, LocalDateTime now) {
        String cronExpression = trigger.getCronExpression();
        if (cronExpression == null || cronExpression.isBlank()) {
            return false;
        }

        // 检查执行间隔
        LocalDateTime lastExec = lastExecutionTimes.get(trigger.getTriggerId());
        if (lastExec != null) {
            long secondsSinceLastExec = ChronoUnit.SECONDS.between(lastExec, now);
            if (secondsSinceLastExec < MIN_EXECUTION_INTERVAL_SECONDS) {
                return false;
            }
        }

        return matchesCronExpression(cronExpression, now);
    }

    /**
     * 简化的 cron 表达式匹配。
     *
     * <p>支持标准 5 段 cron 表达式：分 时 日 月 周。
     * 仅支持基本通配符和具体值，不支持复杂表达式如 L、W、# 等。</p>
     *
     * @param cronExpression cron 表达式
     * @param time           时间
     * @return 是否匹配
     */
    private boolean matchesCronExpression(String cronExpression, LocalDateTime time) {
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != CRON_FIELD_COUNT) {
            log.warn("[CronScheduler] 无效的 cron 表达式: {}", cronExpression);
            return false;
        }

        String minuteField = parts[CRON_MINUTE_INDEX];
        String hourField = parts[CRON_HOUR_INDEX];
        String dayOfMonthField = parts[CRON_DAY_OF_MONTH_INDEX];
        String monthField = parts[CRON_MONTH_INDEX];
        String dayOfWeekField = parts[CRON_DAY_OF_WEEK_INDEX];
        return matchesField(minuteField, time.getMinute(), 0, MINUTE_MAX) // 分
                && matchesField(hourField, time.getHour(), 0, HOUR_MAX) // 时
                && matchesField(dayOfMonthField, time.getDayOfMonth(), 1, DAY_OF_MONTH_MAX) // 日
                && matchesField(monthField, time.getMonthValue(), 1, MONTH_MAX) // 月
                && matchesField(dayOfWeekField, time.getDayOfWeek().getValue(), 1, DAY_OF_WEEK_MAX); // 周
    }

    /**
     * 匹配 cron 表达式的单个字段。
     *
     * @param field      字段表达式
     * @param value      当前值
     * @param min        最小值
     * @param max        最大值
     * @return 是否匹配
     */
    private boolean matchesField(String field, int value, int min, int max) {
        // 通配符 * 匹配所有
        if ("*".equals(field)) {
            return true;
        }

        // 处理逗号分隔的列表，如 "1,3,5"
        if (field.contains(",")) {
            for (String part : field.split(",")) {
                if (matchesField(part.trim(), value, min, max)) {
                    return true;
                }
            }
            return false;
        }

        // 处理范围，如 "1-5"
        if (field.contains("-") && !field.startsWith("-")) {
            String[] range = field.split("-");
            if (range.length == 2) {
                try {
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    return value >= start && value <= end;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // 处理步长，如 "*/5"
        if (field.contains("/")) {
            String[] stepParts = field.split("/");
            if (stepParts.length == 2 && "*".equals(stepParts[0].trim())) {
                try {
                    int step = Integer.parseInt(stepParts[1].trim());
                    return (value - min) % step == 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // 精确匹配
        try {
            return Integer.parseInt(field.trim()) == value;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 执行定时触发器。
     *
     * @param trigger 触发器
     * @param now     当前时间
     */
    private void executeCronTrigger(AgentTrigger trigger, LocalDateTime now) {
        String triggerId = trigger.getTriggerId();
        log.info("[CronScheduler] 执行定时触发器: triggerId={}, name={}, cron={}",
                triggerId, trigger.getName(), trigger.getCronExpression());

        lastExecutionTimes.put(triggerId, now);

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("cronExpression", trigger.getCronExpression());
        context.put("scheduledAt", now.toString());

        try {
            evaluationService.evaluateContentTriggers(trigger.getTenantId(), trigger.getCronExpression(), context);
        } catch (Exception e) {
            log.error("[CronScheduler] 定时触发器执行异常: triggerId={}, error={}",
                    triggerId, e.getMessage(), e);
        }
    }

    /**
     * 清理过期的执行记录。
     */
    public void cleanupExecutionRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        lastExecutionTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }
}
