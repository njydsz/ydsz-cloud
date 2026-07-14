package com.njydsz.pmis.common.event.health;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

/**
 * Outbox 健康检查指标
 *
 * <p>检查 Outbox 表中的消息积压情况：
 * <ul>
 *   <li>DEAD_LETTER 消息数 > 0 时标记为 DOWN</li>
 *   <li>PENDING 消息数超过阈值时标记为 DEGRADED（自定义 Status）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(OutboxRepository.class)
public class OutboxHealthIndicator implements HealthIndicator {

    private static final long PENDING_WARNING_THRESHOLD = 1000;

    private final OutboxRepository outboxRepository;

    public OutboxHealthIndicator(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Health health() {
        try {
            Map<String, Long> statusCounts = outboxRepository.countByStatus();
            long pending = statusCounts.getOrDefault(OutboxStatus.PENDING.name(), 0L);
            long deadLetter = statusCounts.getOrDefault(OutboxStatus.DEAD_LETTER.name(), 0L);
            long sent = statusCounts.getOrDefault(OutboxStatus.SENT.name(), 0L);

            Health.Builder builder;
            if (deadLetter > 0) {
                builder = Health.down();
            } else if (pending > PENDING_WARNING_THRESHOLD) {
                builder = Health.status("DEGRADED");
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("pending", pending)
                    .withDetail("sent", sent)
                    .withDetail("deadLetter", deadLetter)
                    .withDetail("threshold", PENDING_WARNING_THRESHOLD)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
