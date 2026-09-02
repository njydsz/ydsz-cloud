package com.njydsz.common.util.id;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Snowflake 健康检查指示器。
 *
 * <p>检查 Snowflake ID 生成器的健康状态，包括：
 *
 * <ul>
 *   <li>时钟回拨检测
 *   <li>workerId 有效性
 *   <li>Snowflake 状态健康检查（不调用 nextId，仅校验配置与时间戳）
 * </ul>
 *
 * <p>自 2.0.0 起，通过构造器注入 {@link SnowflakeIdGenerator} Spring Bean， 不再依赖静态单例。Bean 不可用时（如禁用状态）返回健康状态
 * unknown。
 *
 * <p>通过 {@link UtilAutoConfiguration} 中 {@code @Bean} 注册， 不使用 {@code @Component}
 * 注解（项目规范：HealthIndicator 统一在 AutoConfiguration 中注册）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SnowflakeHealthIndicator implements HealthIndicator {

  private final ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider;

  /**
   * 构造器注入 {@link SnowflakeIdGenerator} Bean。
   *
   * <p>使用 ObjectProvider 实现可选依赖：当 Snowflake 被禁用（enabled=false）或 Bean 不存在时，health 方法返回 unknown 而非报错。
   *
   * @param idGeneratorProvider SnowflakeIdGenerator Bean provider（可选）
   */
  public SnowflakeHealthIndicator(ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider) {
    this.idGeneratorProvider = idGeneratorProvider;
  }

  @Override
  public Health health() {
    try {
      SnowflakeIdGenerator idGenerator = idGeneratorProvider.getIfAvailable();
      if (idGenerator == null) {
        return Health.unknown()
            .withDetail(
                "reason", "SnowflakeIdGenerator bean not available (disabled or not configured)")
            .build();
      }

      // 检查 workerId 有效性
      long workerId = idGenerator.getWorkerId();
      if (workerId < 0 || workerId > SnowflakeIdGenerator.getMaxWorkerId()) {
        return Health.down().withDetail("error", "Invalid workerId: " + workerId).build();
      }

      // 检查时钟状态（不调用 nextId()，避免健康检查消耗真实 ID）
      long currentTimestamp = System.currentTimeMillis();
      long lastTimestamp = idGenerator.getLastTimestamp();
      if (currentTimestamp < lastTimestamp) {
        long offset = lastTimestamp - currentTimestamp;
        return Health.down()
            .withDetail("error", "Clock moved backwards")
            .withDetail("offset", offset + "ms")
            .build();
      }

      return Health.up()
          .withDetail("workerId", workerId)
          .withDetail("datacenterId", idGenerator.getDatacenterId())
          .withDetail("lastTimestamp", lastTimestamp)
          .withDetail("currentTimestamp", currentTimestamp)
          .build();

    } catch (Exception e) {
      return Health.down().withException(e).build();
    }
  }
}
