package com.njydsz.pmis.cronjob.server.core.scheduler;

import com.njydsz.pmis.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.pmis.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * P2-13: 事件驱动调度（MQ 消息触发）。
 *
 * <p>通过 MQ 消息（RocketMQ/Kafka）触发任务执行，实现事件驱动的调度模式。
 * 与 CRON 定时调度互补，适用于"上游完成即触发"的场景。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>任务配置 {@code schedule_type=EVENT}，在 {@code params_json} 中配置 MQ topic/tag</li>
 *   <li>MQ Consumer 收到消息后，调用 {@link #triggerByEvent} 查找并派发任务</li>
 *   <li>任务执行流程与 CRON 触发一致（分布式锁、日志、重试、告警）</li>
 * </ol>
 *
 * <h3>消息格式</h3>
 * <pre>{@code
 * {
 *   "topic": "order-created",
 *   "tag": "payment",
 *   "jobKey": "sync-payment-record",
 *   "payload": {"orderId": "12345", "amount": 99.00}
 * }
 * }</pre>
 *
 * <h3>去重保证</h3>
 * <ul>
 *   <li>使用 Redis SETNX 实现消息级去重（TTL 5 分钟）</li>
 *   <li>Key: {@code pmis:job:event:dedup:{jobKey}:{msgId}}</li>
 *   <li>重复消息在 TTL 内只会触发一次任务执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDrivenScheduler {

    private final JobMapper jobMapper;
    private final TaskDispatcher taskDispatcher;
    private final StringRedisTemplate redisTemplate;

    /** 事件去重 key 前缀 */
    private static final String EVENT_DEDUP_PREFIX = "pmis:job:event:dedup:";

    /** 去重 TTL（分钟） */
    private static final long DEDUP_TTL_MINUTES = 5;

    /**
     * 通过 MQ 事件触发任务执行。
     *
     * <p>由 MQ Consumer 调用，根据 jobKey 查找配置了 EVENT 调度类型的任务，
     * 在去重通过后通过 DefaultTaskDispatcher 派发执行。
     *
     * @param jobKey   任务 KEY
     * @param msgId    消息 ID（用于去重，可为 null 表示不去重）
     * @param payload  消息负载（JSON 字符串，可覆盖任务默认 paramsJson）
     * @return true 触发成功；false 任务不存在、去重失败或调度类型不匹配
     */
    public boolean triggerByEvent(String jobKey, String msgId, String payload) {
        if (jobKey == null || jobKey.isBlank()) {
            log.warn("[EventScheduler] jobKey 为空, 跳过");
            return false;
        }

        // 去重检查
        if (msgId != null && !msgId.isBlank()) {
            if (!acquireDedupLock(jobKey, msgId)) {
                log.debug("[EventScheduler] 消息已处理, 跳过: jobKey={} msgId={}", jobKey, msgId);
                return false;
            }
        }

        // 查找任务
        JobDO job = jobMapper.selectByJobKey(jobKey);
        if (job == null) {
            log.warn("[EventScheduler] 任务不存在: jobKey={}", jobKey);
            return false;
        }

        // 校验调度类型
        if (!"EVENT".equals(job.getScheduleType())) {
            log.warn("[EventScheduler] 任务调度类型非 EVENT: jobKey={} scheduleType={}",
                    jobKey, job.getScheduleType());
            return false;
        }

        // 覆盖 paramsJson（如果 payload 非空）
        if (payload != null && !payload.isBlank()) {
            job.setParamsJson(payload);
        }

        log.info("[EventScheduler] 事件触发任务: jobKey={} msgId={}", jobKey, msgId);
        // P0-4 修复：补全派发闭环，直接调用 TaskDispatcher.dispatch
        // EVENT 触发走异步派发路径（非 MANUAL），dispatch 返回 null 表示异步执行中
        String logId = taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_EVENT);
        if (logId != null) {
            log.info("[EventScheduler] 事件任务同步派发完成: jobKey={} logId={}", jobKey, logId);
        } else {
            log.debug("[EventScheduler] 事件任务异步派发中: jobKey={}", jobKey);
        }
        return true;
    }

    /**
     * 获取事件去重锁。
     *
     * @param jobKey 任务 KEY
     * @param msgId  消息 ID
     * @return true 获取成功（首次处理）；false 已处理过（重复消息）
     */
    private boolean acquireDedupLock(String jobKey, String msgId) {
        try {
            String key = EVENT_DEDUP_PREFIX + jobKey + ":" + msgId;
            // 使用 setIfAbsent(K, V, Duration) 替代已弃用的 setIfAbsent(K, V, long, TimeUnit)（Spring Data Redis 4.1+）
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", java.time.Duration.ofMinutes(DEDUP_TTL_MINUTES));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("[EventScheduler] 去重锁获取异常, 放行: jobKey={} msgId={} reason={}",
                    jobKey, msgId, e.getMessage());
            return true; // Redis 异常时放行，避免丢失消息
        }
    }
}
