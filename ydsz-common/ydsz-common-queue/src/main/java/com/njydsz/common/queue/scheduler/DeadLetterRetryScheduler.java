package com.njydsz.common.queue.scheduler;

import org.springframework.scheduling.annotation.Scheduled;

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
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DeadLetterRetryScheduler {

    private final DeadLetterQueueService deadLetterQueueService;
    private final QueueProperties queueProperties;

    public DeadLetterRetryScheduler(DeadLetterQueueService deadLetterQueueService,
                                    QueueProperties queueProperties) {
        this.deadLetterQueueService = deadLetterQueueService;
        this.queueProperties = queueProperties;
    }

    /**
     * 定时扫描并重试死信队列中的消息
     * <p>执行间隔由 queueProperties.deadLetterRetryInterval 配置决定
     */
    @Scheduled(fixedDelayString = "${ydsz.queue.deadLetterRetryInterval:60000}",
               initialDelayString = "${ydsz.queue.deadLetterRetryInterval:60000}")
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
}
