package com.remisoft.cronjob.server.core;

import java.time.Duration;

import com.remisoft.common.redis.service.RedisService;
import org.springframework.stereotype.Component;

import com.remisoft.cronjob.domain.entity.job.Job;
import com.remisoft.cronjob.infra.mapper.job.JobMapper;
import com.remisoft.cronjob.server.service.job.JobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 事件驱动调度器。
 *
 * <p>接收外部事件（如 MQ 消息）并触发对应的定时任务执行。
 * 使用 Redis SETNX 进行消息去重，确保同一事件不会重复触发。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDrivenScheduler {

    private static final String DEDUP_KEY_PREFIX = "remi:job:event:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(30);

    private final RedisService redisService;
    private final JobMapper jobMapper;
    private final JobService jobService;

    /**
     * 通过事件触发任务执行。
     *
     * <p>使用 Redis SETNX 进行去重，同一 msgId 在 TTL 内不会重复触发。
     *
     * @param jobKey  任务 Key
     * @param msgId   消息 ID（用于去重）
     * @param payload 负载数据
     * @return true 表示触发成功，false 表示已去重或触发失败
     */
    public boolean triggerByEvent(String jobKey, String msgId, String payload) {
        if (jobKey == null || jobKey.isBlank()) {
            log.warn("[EventScheduler] jobKey 为空, 跳过触发");
            return false;
        }

        String dedupKey = DEDUP_KEY_PREFIX + (msgId != null ? msgId : jobKey + ":" + System.currentTimeMillis());
        Boolean acquired = redisService.setIfAbsent(dedupKey, "1", DEDUP_TTL.toSeconds());
        if (Boolean.FALSE.equals(acquired)) {
            log.info("[EventScheduler] 事件已去重, 跳过触发: jobKey={} msgId={}", jobKey, msgId);
            return false;
        }

        try {
            Job job = jobMapper.selectByJobKey(jobKey);
            if (job == null) {
                log.warn("[EventScheduler] jobKey 不存在: {}", jobKey);
                return false;
            }
            String logId = jobService.trigger(job.getId());
            log.info("[EventScheduler] 事件触发任务成功: jobKey={} msgId={} logId={}", jobKey, msgId, logId);
            return true;
        } catch (Exception e) {
            log.error("[EventScheduler] 事件触发任务失败: jobKey={} msgId={} err={}",
                    jobKey, msgId, e.getMessage(), e);
            return false;
        }
    }
}
