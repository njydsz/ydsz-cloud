package com.remisoft.common.event.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.event.config.EventProperties;
import com.remisoft.common.event.model.OutboxStatus;
import com.remisoft.common.event.repository.OutboxRepository;

/**
 * Outbox 健康检查指标
 *
 * <p>检查 Outbox 表中的消息积压情况：
 * <ul>
 *   <li>DEAD_LETTER 消息数 > 阈值时标记为 DOWN</li>
 *   <li>PENDING 消息数超过阈值时标记为 DEGRADED（自定义 Status）</li>
 *   <li>PROCESSING 消息数超过阈值时标记为 DEGRADED（可能有实例宕机）</li>
 * </ul>
 *
 * <p>查询优化：仅统计非 SENT 状态的消息（SENT 消息由清理任务定期删除，
 * 不参与健康检查），避免在大表上对 SENT 行做无意义的 COUNT。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class OutboxHealthIndicator implements HealthIndicator {

    /** Outbox 仓储 */
    private final OutboxRepository outboxRepository;

    /** 事件配置属性 */
    private final EventProperties properties;

    /**
     * 构造函数
     *
     * @param outboxRepository Outbox 仓储
     * @param properties       事件配置属性（用于读取告警阈值）
     */
    public OutboxHealthIndicator(OutboxRepository outboxRepository, EventProperties properties) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
    }

    /**
     * 执行 Outbox 健康检查
     *
     * <p>根据各状态消息数量与配置阈值比较，返回健康状态：
     * <ul>
     *   <li>UP - 消息积压在正常范围内</li>
     *   <li>DEGRADED - PENDING 或 PROCESSING 消息数超过阈值</li>
     *   <li>DOWN - DEAD_LETTER 消息数超过阈值</li>
     * </ul>
     *
     * @return 健康检查结果，包含各状态消息数和阈值详情
     */
    @Override
    public Health health() {
        try {
            Map<String, Long> statusCounts = outboxRepository.countByStatus();
            long pending = statusCounts.getOrDefault(OutboxStatus.PENDING.name(), 0L);
            long processing = statusCounts.getOrDefault(OutboxStatus.PROCESSING.name(), 0L);
            long deadLetter = statusCounts.getOrDefault(OutboxStatus.DEAD_LETTER.name(), 0L);

            long deadLetterThreshold = properties.getDeadLetterAlertThreshold();
            long pendingThreshold = properties.getPendingAlertThreshold();

            Health.Builder builder;
            if (deadLetter > deadLetterThreshold) {
                builder = Health.down();
            } else if (pending > pendingThreshold) {
                builder = Health.status("DEGRADED");
            } else if (processing > pendingThreshold / 2) {
                builder = Health.status("DEGRADED");
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("pending", pending)
                    .withDetail("processing", processing)
                    .withDetail("deadLetter", deadLetter)
                    .withDetail("pendingThreshold", pendingThreshold)
                    .withDetail("deadLetterThreshold", deadLetterThreshold)
                    .withDetail("timestamp", Instant.now().toString())
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
