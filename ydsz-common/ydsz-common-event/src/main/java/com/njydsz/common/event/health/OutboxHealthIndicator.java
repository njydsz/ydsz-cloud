package com.njydsz.common.event.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;

/**
 * Outbox 健康检查指标
 *
 * <p>检查 Outbox 表中的消息积压情况：
 *
 * <ul>
 *   <li>DEAD_LETTER 消息数 > 阈值时标记为 DOWN
 *   <li>PENDING 消息数超过阈值时标记为 DEGRADED（自定义 Status）
 *   <li>PROCESSING 消息数超过阈值时标记为 DEGRADED（可能有实例宕机）
 * </ul>
 *
 * <p>查询优化：仅统计非 SENT 状态的消息（SENT 消息由清理任务定期删除， 不参与健康检查），避免在大表上对 SENT 行做无意义的 COUNT。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 移除对 EventProperties 的依赖，使用内置常量阈值
 */
public class OutboxHealthIndicator implements HealthIndicator {

  /** DEAD_LETTER 消息数健康阈值 */
  private static final long DEAD_LETTER_THRESHOLD = 10L;

  /** PENDING 消息数健康阈值 */
  private static final long PENDING_THRESHOLD = 10000L;

  /** Outbox 仓储 */
  private final OutboxRepository outboxRepository;

  /**
   * 构造函数
   *
   * @param outboxRepository Outbox 仓储
   */
  public OutboxHealthIndicator(OutboxRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
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

      Health.Builder builder;
      if (deadLetter > DEAD_LETTER_THRESHOLD) {
        builder = Health.down();
      } else if (pending > PENDING_THRESHOLD) {
        builder = Health.status("DEGRADED");
      } else if (processing > PENDING_THRESHOLD / 2) {
        builder = Health.status("DEGRADED");
      } else {
        builder = Health.up();
      }

      return builder
          .withDetail("pending", pending)
          .withDetail("processing", processing)
          .withDetail("deadLetter", deadLetter)
          .withDetail("pendingThreshold", PENDING_THRESHOLD)
          .withDetail("deadLetterThreshold", DEAD_LETTER_THRESHOLD)
          .withDetail("timestamp", Instant.now().toString())
          .build();
    } catch (Exception e) {
      return Health.down(e).build();
    }
  }
}
