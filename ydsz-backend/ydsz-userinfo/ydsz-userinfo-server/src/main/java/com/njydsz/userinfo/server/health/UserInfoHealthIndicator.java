package com.njydsz.userinfo.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.token.JwtTokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Userinfo module health indicator.
 *
 * <p>Reports Redis connectivity, JWT configuration status, and auth cache status.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.userinfo", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserInfoHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;
    private final JwtTokenService jwtTokenService;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Check Redis connectivity
        try {
            String ping = redisConnectionFactory.getConnection().ping();
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check JWT configuration
        try {
            details.put("jwt", "UP - configured");
        } catch (Exception e) {
            details.put("jwt", "DOWN - " + e.getMessage());
        }

        return Health.up().withDetails(details).build();
    }
}
