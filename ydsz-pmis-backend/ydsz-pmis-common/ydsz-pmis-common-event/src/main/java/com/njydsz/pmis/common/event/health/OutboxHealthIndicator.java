package com.njydsz.pmis.common.event.health;

import java.util.Map;

import org.springframework.boot.health.healthcontributor.HealthIndicator;

import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

/**
 * Outbox 健康检查指标
 *
 * <p>检查 Outbox 表中的消息积压情况：
 * <ul>
 *   <li>DEAD_LETTER 消息数 > 0 时标记为 DOWN</li>
 *   <li>PENDING 消息数超过阈值时标记为 DEGRADED</li>
 * </ul>
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
public class OutboxHealthIndicator implements HealthIndicator {

    private static final long PENDING_WARNING_THRESHOLD = 1000;

    private final OutboxRepository outboxRepository;

    public OutboxHealthIndicator(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public org.springframework.boot.health.Health health() {
        try {
            Map<String, Long> statusCounts = outboxRepository.countByStatus();
            long pending = statusCounts.getOrDefault(OutboxStatus.PENDING.name(), 0L);
            long deadLetter = statusCounts.getOrDefault(OutboxStatus.DEAD_LETTER.name(), 0L);
            long sent = statusCounts.getOrDefault(OutboxStatus.SENT.name(), 0L);

            org.springframework.boot.health.Health.Builder builder;
            if (deadLetter > 0) {
                builder = org.springframework.boot.health.Health.down();
            } else if (pending > PENDING_WARNING_THRESHOLD) {
                builder = new org.springframework.boot.health.Health.Builder("DEGRADED");
            } else {
                builder = org.springframework.boot.health.Health.up();
            }

            return builder
                    .withDetail("pending", pending)
                    .withDetail("sent", sent)
                    .withDetail("deadLetter", deadLetter)
                    .withDetail("threshold", PENDING_WARNING_THRESHOLD)
                    .build();
        } catch (Exception e) {
            return org.springframework.boot.health.Health.down(e).build();
        }
    }
}
