package com.njydsz.common.util.id;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class SnowflakeHealthIndicator implements HealthIndicator {

    private final SnowflakeUtils snowflakeUtils;

    public SnowflakeHealthIndicator(SnowflakeUtils snowflakeUtils) {
        this.snowflakeUtils = snowflakeUtils;
    }

    @Override
    public Health health() {
        try {
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
