package com.njydsz.common.feign.circuitbreaker;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import com.njydsz.common.redis.service.RedisService;

/**
 * 断路器状态持久化组件。
 *
 * <p>将熔断状态写入 Redis，应用重启后从 Redis 恢复熔断状态。
 * 支持按服务维度持久化熔断器状态和状态切换时间戳。
 *
 * <p><b>Redis 存储格式：</b>
 * <pre>
 * ydsz:feign:circuit:{serviceName} -> "OPEN|1716624000000"
 * </pre>
 *
 * <p><b>TTL 管理：</b>依赖 Redis 自身的 TTL 自动过期，不进行双重过期检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConditionalOnClass(RedisService.class)
public class CircuitBreakerStatePersistence {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerStatePersistence.class);

    private static final String KEY_PREFIX = "ydsz:feign:circuit:";

    private final RedisService redisService;
    private final Duration ttl;

    /**
     * 使用默认 TTL（1 小时）构造。
     *
     * @param redisServiceProvider Redis 服务提供者（可选）
     */
    public CircuitBreakerStatePersistence(ObjectProvider<RedisService> redisServiceProvider) {
        this(redisServiceProvider, Duration.ofHours(1));
    }

    /**
     * 使用自定义 TTL 构造。
     *
     * @param redisServiceProvider Redis 服务提供者（可选）
     * @param ttl                  状态持久化 TTL
     */
    public CircuitBreakerStatePersistence(ObjectProvider<RedisService> redisServiceProvider, Duration ttl) {
        this.redisService = redisServiceProvider.getIfAvailable();
        this.ttl = ttl != null ? ttl : Duration.ofHours(1);
    }

    /**
     * 持久化熔断器状态到 Redis。
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
     * 从 Redis 恢复熔断器状态。
     *
     * <p>依赖 Redis TTL 自动过期，不做双重过期检查。
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
                log.info("从 Redis 恢复熔断器状态, service={}, state={}", serviceName, state);
                return state;
            }
            return null;
        } catch (Exception e) {
            log.warn("从 Redis 恢复熔断器状态失败, service={}", serviceName, e);
            return null;
        }
    }

    /**
     * 清除指定服务的熔断器状态记录。
     *
     * @param serviceName 服务名称
     */
    public void clearState(String serviceName) {
        if (redisService == null) {
            return;
        }
        try {
            redisService.del(buildKey(serviceName));
        } catch (Exception e) {
            log.debug("清除熔断器状态记录失败, service={}", serviceName, e);
        }
    }

    private String buildKey(String serviceName) {
        return KEY_PREFIX + serviceName;
    }
}
