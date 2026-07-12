package com.njydsz.pmis.common.feign.circuitbreaker;

import com.njydsz.pmis.common.redis.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.time.Duration;

/**
 * 断路器状态持久化组件。
 *
 * <p>将熔断状态写入 Redis，应用重启后从 Redis 恢复熔断状态。
 * 支持按服务维度持久化熔断器状态和状态切换时间戳。
 *
 * <p><b>Redis 存储格式：</b>
 * <pre>
 * remi:feign:circuit:{serviceName} -> "OPEN|1716624000000"
 * remi:feign:circuit:ttl -> 3600（秒）
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@ConditionalOnClass(RedisService.class)
public class CircuitBreakerStatePersistence {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerStatePersistence.class);

    private static final String KEY_PREFIX = "remi:feign:circuit:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final RedisService redisService;
    private final Duration ttl;

    public CircuitBreakerStatePersistence(org.springframework.beans.factory.ObjectProvider<RedisService> redisServiceProvider) {
        this.redisService = redisServiceProvider.getIfAvailable();
        this.ttl = DEFAULT_TTL;
    }

    /**
     * 持久化熔断器状态到 Redis
     *
     * @param serviceName 服务名称
     * @param state       熔断器状态
     */
    public void persistState(String serviceName, FeignCircuitBreakerStrategy.CircuitBreakerState state) {
        if (redisService == null) {
            log.debug("RedisService 未提供，跳过熔断器状态持久化");
            return;
        }
        if (state == FeignCircuitBreakerStrategy.CircuitBreakerState.CLOSED
                || state == FeignCircuitBreakerStrategy.CircuitBreakerState.DISABLED) {
            // CLOSED 和 DISABLED 状态不需要持久化，直接删除已有记录
            clearState(serviceName);
            return;
        }
        try {
            String key = buildKey(serviceName);
            String value = state.name() + "|" + System.currentTimeMillis();
            redisService.set(key, value, ttl);
            log.debug("熔断器状态已持久化到 Redis, service={}, state={}", serviceName, state);
        } catch (Exception e) {
            log.warn("熔断器状态持久化失败, service={}", serviceName, e);
        }
    }

    /**
     * 从 Redis 恢复熔断器状态
     *
     * @param serviceName 服务名称
     * @return 持久化的状态，无记录时返回 null
     */
    public FeignCircuitBreakerStrategy.CircuitBreakerState restoreState(String serviceName) {
        if (redisService == null) {
            return null;
        }
        try {
            String key = buildKey(serviceName);
            Object value = redisService.get(key);
            if (value == null) {
                return null;
            }
            String valueStr = String.valueOf(value);
            String[] parts = valueStr.split("\\|");
            if (parts.length >= 1) {
                FeignCircuitBreakerStrategy.CircuitBreakerState state =
                        FeignCircuitBreakerStrategy.CircuitBreakerState.valueOf(parts[0]);
                long savedTimestamp = parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
                long elapsed = System.currentTimeMillis() - savedTimestamp;
                // 如果记录已过期（超过 ttl），则清除并返回 null
                if (elapsed > ttl.toMillis()) {
                    clearState(serviceName);
                    return null;
                }
                log.info("从 Redis 恢复熔断器状态, service={}, state={}, elapsed={}ms",
                        serviceName, state, elapsed);
                return state;
            }
            return null;
        } catch (Exception e) {
            log.warn("从 Redis 恢复熔断器状态失败, service={}", serviceName, e);
            return null;
        }
    }

    /**
     * 清除指定服务的熔断器状态记录
     *
     * @param serviceName 服务名称
     */
    public void clearState(String serviceName) {
        if (redisService == null) {
            return;
        }
        try {
            String key = buildKey(serviceName);
            redisService.del(key);
        } catch (Exception e) {
            log.debug("清除熔断器状态记录失败, service={}", serviceName, e);
        }
    }

    private String buildKey(String serviceName) {
        return KEY_PREFIX + serviceName;
    }
}
