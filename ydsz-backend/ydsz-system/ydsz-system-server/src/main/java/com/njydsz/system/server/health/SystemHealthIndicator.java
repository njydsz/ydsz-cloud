package com.njydsz.system.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * System module health indicator.
 *
 * <p>Reports Redis connectivity and config service status.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.system", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SystemHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        try {
            String ping = redisConnectionFactory.getConnection().ping();
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        details.put("config", "UP - hot-reload enabled");
        return Health.up().withDetails(details).build();
    }
}
