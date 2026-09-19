package com.njydsz.common.event.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.event.config.EventProperties;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;

/**
 * Outbox 健康检查指标
 *
 * <p>检查 Outbox 表中的消息积压情况，阈值通过 {@link EventProperties.Health} 可配：
 *
 * <ul>
 *   <li>DEAD_LETTER 消息数 > {@code deadLetterThreshold} 时标记为 DOWN
 *   <li>PENDING 消息数 > {@code pendingThreshold} 时标记为 DEGRADED（自定义 Status）
 *   <li>PROCESSING 消息数 > {@code processingThreshold} 时标记为 DEGRADED（可能有实例宕机）
 * </ul>
 *
 * <p>查询优化：从仓储层使用缓存版本的 countByStatus（缓存时间由 {@code statusCountCacheSeconds} 配置）， 减少高频
 * /actuator/health 端点带来的全表 COUNT 压力。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   event:
 *     outbox:
 *       health:
 *         pending-threshold: 10000
 *         processing-threshold: 5000
 *         dead-letter-threshold: 10
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.19 E-3 阈值可配：移除硬编码常量，改为从 EventProperties.Health 读取
 * @since 26.09.19 移除对 EventProperties 的依赖（由构造注入改为配置注入）
 */
public class OutboxHealthIndicator implements HealthIndicator {

  /** Outbox 仓储 */
  private final OutboxRepository outboxRepository;

  /** 健康检查阈值配置 */
  private final EventProperties.Health healthConfig;

  /**
   * 构造函数
   *
   * @param outboxRepository Outbox 仓储
   * @param properties 事件配置属性（提取 Health 配置用于阈值判断）
   */
  public OutboxHealthIndicator(OutboxRepository outboxRepository, EventProperties properties) {
    this.outboxRepository = outboxRepository;
    this.healthConfig = properties != null ? properties.getHealth() : new EventProperties.Health();
  }

  /**
   * 执行 Outbox 健康检查
   *
   * <p>根据各状态消息数量与阈值比较，返回健康状态：
   *
   * <ul>
   *   <li>UP - 消息积压在正常范围内
   *   <li>DEGRADED - PENDING 或 PROCESSING 消息数超过阈值
   *   <li>DOWN - DEAD_LETTER 消息数超过阈值
   * </ul>
   *
   * @return 健康检查结果，包含各状态消息数和阈值详情
   */
  @Override
  public Health health() {
    try {
      // 使用缓存版本减少全表 COUNT 对 /actuator/health 端点的响应时间
      Map<String, Long> statusCounts = outboxRepository.countByStatus(true);
      long pending = statusCounts.getOrDefault(OutboxStatus.PENDING.name(), 0L);
      long processing = statusCounts.getOrDefault(OutboxStatus.PROCESSING.name(), 0L);
      long deadLetter = statusCounts.getOrDefault(OutboxStatus.DEAD_LETTER.name(), 0L);

      long pendingThreshold = healthConfig.getPendingThreshold();
      long processingThreshold = healthConfig.getProcessingThreshold();
      long deadLetterThreshold = healthConfig.getDeadLetterThreshold();

      Health.Builder builder;
      if (deadLetter > deadLetterThreshold) {
        builder = Health.down();
      } else if (pending > pendingThreshold) {
        builder = Health.status("DEGRADED");
      } else if (processing > processingThreshold) {
        builder = Health.status("DEGRADED");
      } else {
        builder = Health.up();
      }

      return builder
          .withDetail("pending", pending)
          .withDetail("processing", processing)
          .withDetail("deadLetter", deadLetter)
          .withDetail("pendingThreshold", pendingThreshold)
          .withDetail("processingThreshold", processingThreshold)
          .withDetail("deadLetterThreshold", deadLetterThreshold)
          .withDetail("timestamp", Instant.now().toString())
          .build();
    } catch (Exception e) {
      return Health.down(e).build();
    }
  }
}
