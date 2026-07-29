package com.njydsz.common.util.id;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Snowflake 健康检查指示器
 *
 * <p>检查 Snowflake ID 生成器的健康状态，包括：
 * <ul>
 *   <li>时钟回拨检测</li>
 *   <li>workerId 有效性</li>
 *   <li>ID 生成能力验证</li>
 * </ul>
 *
 * <p>通过 {@link UtilAutoConfiguration} 中 {@code @Bean} 注册，
 * 不使用 {@code @Component} 注解（项目规范：HealthIndicator 统一在 AutoConfiguration 中注册）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SnowflakeHealthIndicator implements HealthIndicator {

    /**
     * 默认构造器
     *
     * <p>不通过构造器注入 SnowflakeUtils（它是静态单例类，非 Spring Bean），
     * 在 {@link #health()} 方法中通过 {@link SnowflakeUtils#getInstance()} 获取实例。
     */
    public SnowflakeHealthIndicator() {
        // 无参构造器，SnowflakeUtils 通过静态 getInstance() 获取
    }

    @Override
    public Health health() {
        try {
            SnowflakeUtils snowflakeUtils = SnowflakeUtils.getInstance();

            // 检查 workerId 有效性
            long workerId = snowflakeUtils.getWorkerId();
            if (workerId < 0 || workerId > 31) {
                return Health.down()
                        .withDetail("error", "Invalid workerId: " + workerId)
                        .build();
            }

            // 尝试生成一个 ID 验证功能正常
            long testId = snowflakeUtils.nextId();
            if (testId <= 0) {
                return Health.down()
                        .withDetail("error", "Failed to generate test ID")
                        .build();
            }

            // 检查时钟状态
            long currentTimestamp = System.currentTimeMillis();
            long lastTimestamp = snowflakeUtils.getLastTimestamp();
            if (currentTimestamp < lastTimestamp) {
                long offset = lastTimestamp - currentTimestamp;
                return Health.down()
                        .withDetail("error", "Clock moved backwards")
                        .withDetail("offset", offset + "ms")
                        .build();
            }

            return Health.up()
                    .withDetail("workerId", workerId)
                    .withDetail("datacenterId", snowflakeUtils.getDatacenterId())
                    .withDetail("shardCount", snowflakeUtils.getShardCount())
                    .withDetail("lastTimestamp", lastTimestamp)
                    .withDetail("currentTimestamp", currentTimestamp)
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
