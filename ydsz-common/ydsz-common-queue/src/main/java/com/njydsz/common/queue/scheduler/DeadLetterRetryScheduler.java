package com.njydsz.common.queue.scheduler;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.service.DeadLetterQueueService;

import lombok.extern.slf4j.Slf4j;

/**
 * 死信队列自动重试调度器
 *
 * <p>定期扫描死信队列并自动重试失败消息。当消息超过最大重试次数后将被永久删除。
 *
 * <p><b>调度策略：</b>
 * <ul>
 *   <li>默认每 60 秒执行一次扫描（可通过 deadLetterRetryInterval 配置）</li>
 *   <li>每次扫描附加随机抖动（0~30% 的间隔时间），避免多实例同时扫描造成惊群</li>
 *   <li>每次扫描遍历所有主题的死信消息</li>
 *   <li>超过最大重试次数的消息将被永久删除</li>
 *   <li>记录每次重试的详细日志</li>
 * </ul>
 *
 * <p><b>配置项：</b>
 * <pre>{@code
 * ydsz.queue:
 *   deadLetterRetryEnabled: true    # 是否启用自动重试
 *   deadLetterMaxRetries: 3         # 最大重试次数
 *   deadLetterRetryInterval: 60000  # 重试间隔（毫秒）
 *   deadLetterRetryJitterPercent: 30 # 抖动百分比（0-100，0=无抖动）
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DeadLetterRetryScheduler implements DisposableBean {

    /** 默认抖动百分比 */
    private static final int DEFAULT_JITTER_PERCENT = 30;

    private final DeadLetterQueueService deadLetterQueueService;
    private final QueueProperties queueProperties;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private volatile ScheduledFuture<?> scheduledFuture;

    public DeadLetterRetryScheduler(DeadLetterQueueService deadLetterQueueService,
                                    QueueProperties queueProperties,
                                    ScheduledExecutorService scheduler) {
        this.deadLetterQueueService = deadLetterQueueService;
        this.queueProperties = queueProperties;
        this.scheduler = scheduler;
        this.random = new Random();
        scheduleNext();
    }

    /**
     * 调度下一次扫描任务
     *
     * <p>使用 programmatic scheduling 替代 {@code @Scheduled}，
     * 以便在每次执行后动态计算带抖动的下次执行时间。
     */
    private void scheduleNext() {
        long interval = queueProperties.resolvedDeadLetterRetryInterval();
        long jitter = calculateJitter(interval);
        long delay = interval + jitter;

        scheduledFuture = scheduler.schedule(this::scanAndRetryWithReschedule, delay, TimeUnit.MILLISECONDS);
        log.debug("[DeadLetterRetryScheduler] 下次扫描延迟 {}ms (基础={}ms, 抖动={}ms)", delay, interval, jitter);
    }

    /**
     * 计算随机抖动值
     *
     * <p>抖动范围为 {@code [0, interval * jitterPercent / 100]}，
     * 多实例部署时各实例的抖动值不同，避免同时扫描造成惊群。
     *
     * @param interval 基础间隔（毫秒）
     * @return 抖动值（毫秒，>= 0）
     */
    private long calculateJitter(long interval) {
        int jitterPercent = queueProperties.getDeadLetterRetryJitterPercent();
        if (jitterPercent <= 0) {
            return 0L;
        }
        int effectivePercent = Math.min(jitterPercent, 100);
        long maxJitter = interval * effectivePercent / 100;
        if (maxJitter <= 0) {
            return 0L;
        }
        return (long) (random.nextDouble() * maxJitter);
    }

    /**
     * 执行扫描并在完成后调度下一次
     */
    private void scanAndRetryWithReschedule() {
        try {
            scanAndRetry();
        } catch (Exception e) {
            log.error("[DeadLetterRetryScheduler] 扫描执行异常: {}", e.getMessage(), e);
        } finally {
            // 无论成功失败，都调度下一次
            scheduleNext();
        }
    }

    /**
     * 定时扫描并重试死信队列中的消息
     */
    public void scanAndRetry() {
        if (!queueProperties.resolvedDeadLetterRetryEnabled()) {
            log.debug("[DeadLetterRetryScheduler] 死信队列自动重试已禁用，跳过扫描");
            return;
        }

        try {
            int totalSuccess = deadLetterQueueService.retryAll();
            if (totalSuccess > 0) {
                log.info("[DeadLetterRetryScheduler] 死信队列扫描完成，成功重试 {} 条消息", totalSuccess);
            } else {
                log.debug("[DeadLetterRetryScheduler] 死信队列扫描完成，无需要重试的消息");
            }
        } catch (Exception e) {
            log.error("[DeadLetterRetryScheduler] 死信队列扫描失败: {}", e.getMessage(), e);
        }
    }

    /**
     * Spring 容器关闭时取消调度任务
     */
    @Override
    public void destroy() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("[DeadLetterRetryScheduler] 调度任务已取消");
        }
    }
}
