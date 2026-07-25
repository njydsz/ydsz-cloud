package com.njydsz.system.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.infra.mapper.DictItemMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * System module health indicator.
 *
 * <p>Reports Redis connectivity, config table reachability, and dict table reachability.
 *
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.system", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SystemHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final ConfigMapper configMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Check Redis connectivity (using execute to ensure connection is released)
        try {
            String ping = redisTemplate.execute(conn -> conn.ping(), true);
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check config table reachability
        try {
            configMapper.selectCount(null);
            details.put("config", "UP - table reachable");
        } catch (Exception e) {
            details.put("config", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check dict table reachability
        try {
            dictItemMapper.selectCount(null);
            details.put("dict", "UP - table reachable");
        } catch (Exception e) {
            details.put("dict", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }
}
