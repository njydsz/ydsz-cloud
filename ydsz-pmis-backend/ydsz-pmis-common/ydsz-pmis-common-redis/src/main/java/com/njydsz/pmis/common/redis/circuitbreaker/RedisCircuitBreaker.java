package com.njydsz.pmis.common.redis.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 熔断器（CLOSED / OPEN / HALF_OPEN 三态机）
 *
 * <p>当 Redis 连续失败达到阈值时自动熔断，避免每次请求都等待超时，
 * 保障应用整体可用性。熔断恢复后自动进入半开状态探测，探测成功则恢复正常。
 *
 * <p><b>状态流转：</b>
 * <ul>
 *   <li>CLOSED → OPEN：连续失败达到 {@code failureThreshold} 次</li>
 *   <li>OPEN → HALF_OPEN：熔断后经过 {@code recoveryTimeoutMs} 时间</li>
 *   <li>HALF_OPEN → CLOSED：连续成功达到 {@code halfOpenMaxRequests} 次</li>
 *   <li>HALF_OPEN → OPEN：任意一次失败</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 注入熔断器（需配置 ydsz.redis.circuit-breaker.enabled=true）
 * RedisCircuitBreaker circuitBreaker;
 *
 * // 包装 Redis 操作
 * String value = circuitBreaker.execute(() -> redisTemplate.opsForValue().get("key"));
 * }</pre>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   redis:
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 5
 *       recovery-timeout-ms: 30000
 *       half-open-max-requests: 3
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ydsz.redis.circuit-breaker.enabled", havingValue = "true")
public class RedisCircuitBreaker {

    /**
     * 熔断器状态
     */
    public enum State {
        /** 关闭状态：所有请求放行 */
        CLOSED,
        /** 开启状态：所有请求拒绝 */
        OPEN,
        /** 半开状态：有限探测请求放行 */
        HALF_OPEN
    }

    /**
     * 熔断器开启时抛出的异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }

    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private volatile long lastFailureTime;

    private final int failureThreshold;
    private final long recoveryTimeoutMs;
    private final int halfOpenMaxRequests;

    public RedisCircuitBreaker(RedisProperties redisProperties) {
        this(
                redisProperties.getCircuitBreaker().getFailureThreshold(),
                redisProperties.getCircuitBreaker().getRecoveryTimeoutMs(),
                redisProperties.getCircuitBreaker().getHalfOpenMaxRequests()
        );
    }

    public RedisCircuitBreaker(int failureThreshold, long recoveryTimeoutMs, int halfOpenMaxRequests) {
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = 0L;
        this.failureThreshold = failureThreshold;
        this.recoveryTimeoutMs = recoveryTimeoutMs;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
        log.info("【RedisCircuitBreaker】熔断器初始化 | failureThreshold={} | recoveryTimeoutMs={} | halfOpenMaxRequests={}",
                failureThreshold, recoveryTimeoutMs, halfOpenMaxRequests);
    }

    /**
     * 通过熔断器执行操作
     *
     * @param operation 要执行的操作
     * @param <T>       返回值类型
     * @return 操作结果
     * @throws CircuitBreakerOpenException 当熔断器处于 OPEN 状态时
     */
    public <T> T execute(Supplier<T> operation) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException(
                    "Redis 熔断器处于 OPEN 状态，拒绝请求");
        }
        try {
            T result = operation.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    /**
     * 检查是否允许请求通过
     *
     * @return true-允许，false-拒绝
     */
    public boolean allowRequest() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime >= recoveryTimeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    successCount.set(0);
                    log.info("【RedisCircuitBreaker】状态转换 OPEN → HALF_OPEN");
                }
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 记录操作成功
     */
    public void recordSuccess() {
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= halfOpenMaxRequests) {
                state.set(State.CLOSED);
                failureCount.set(0);
                log.info("【RedisCircuitBreaker】状态转换 HALF_OPEN → CLOSED（探测成功 {} 次）", successes);
            }
        } else if (currentState == State.CLOSED) {
            failureCount.set(0);
        }
    }

    /**
     * 记录操作失败
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            state.set(State.OPEN);
            log.warn("【RedisCircuitBreaker】状态转换 HALF_OPEN → OPEN（探测失败）");
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                state.set(State.OPEN);
                log.warn("【RedisCircuitBreaker】状态转换 CLOSED → OPEN（连续失败 {} 次）", failures);
            }
        }
    }

    /**
     * 获取当前熔断器状态
     *
     * @return 熔断器状态
     */
    public State getState() {
        return state.get();
    }

    /**
     * 重置熔断器到 CLOSED 状态
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime = 0L;
        log.info("【RedisCircuitBreaker】熔断器已重置为 CLOSED 状态");
    }
}
