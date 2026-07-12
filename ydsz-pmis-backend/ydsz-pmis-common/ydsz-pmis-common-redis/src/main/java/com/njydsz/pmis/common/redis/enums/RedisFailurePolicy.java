package com.njydsz.pmis.common.redis.enums;

/**
 * Redis 故障处理策略统一接口
 *
 * <p>抽象 Redis 操作失败时的处理行为，统一各组件（限流器、布隆过滤器、缓存守卫、
 * 延时队列等）的异常处理方式，避免各处硬编码 {@code catch (Exception e)} 后各自决定返回值。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #onGetFailure} — 读操作失败时的处理</li>
 *   <li>{@link #onWriteFailure} — 写操作失败时的处理</li>
 * </ul>
 *
 * <p><b>内置实现：</b>
 * <ul>
 *   <li>{@link #FAIL_OPEN} — 故障放行：读返回 null，写返回 false</li>
 *   <li>{@link #FAIL_CLOSED} — 故障拒绝：读返回 null，写返回 false（语义上拒绝，由调用方判断）</li>
 *   <li>{@link #FAIL_THROW} — 故障抛异常：读/写均抛出 {@link RedisOperationException}</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public String safeGet(String key) {
 *     try {
 *         return redisTemplate.opsForValue().get(key);
 *     } catch (Exception e) {
 *         return failurePolicy.onGetFailure(key, "GET", e, null);
 *     }
 * }
 * }</pre>
 *
 * <p><b>自定义实现：</b>
 * <pre>{@code
 * public class LoggingFailPolicy implements RedisFailurePolicy {
 *     @Override
 *     public <T> T onGetFailure(String key, String operation, Exception cause, T fallback) {
 *         log.warn("Redis GET 失败 key={} cause={}", key, cause.getMessage());
 *         return fallback;
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public interface RedisFailurePolicy {

    /**
     * 读操作失败时的处理
     *
     * @param key      操作的 Redis key（可为 null，如 pipeline 操作）
     * @param operation 操作名称（如 "GET", "HGET", "PIPELINE_GET"）
     * @param cause     异常原因
     * @param fallback  默认降级值（由调用方提供，如 null/emptyList/0）
     * @param <T>       返回值类型
     * @return 降级返回值（FAIL_THROW 实现会抛异常，不返回）
     * @throws RedisOperationException 当策略为 FAIL_THROW 时抛出
     */
    <T> T onGetFailure(String key, String operation, Exception cause, T fallback);

    /**
     * 写操作失败时的处理
     *
     * @param key       操作的 Redis key（可为 null）
     * @param operation 操作名称（如 "SET", "HSET", "DEL"）
     * @param cause     异常原因
     * @param fallback  默认降级值（由调用方提供，如 false/0/null）
     * @param <T>       返回值类型
     * @return 降级返回值（FAIL_THROW 实现会抛异常，不返回）
     * @throws RedisOperationException 当策略为 FAIL_THROW 时抛出
     */
    <T> T onWriteFailure(String key, String operation, Exception cause, T fallback);

    /**
     * 故障放行策略：读返回 fallback，写返回 fallback
     *
     * <p>适用于对可用性要求高于一致性的场景（如缓存读、限流）。
     * Redis 故障时服务继续可用，但可能返回旧数据或放行请求。
     */
    RedisFailurePolicy FAIL_OPEN = new RedisFailurePolicy() {
        @Override
        public <T> T onGetFailure(String key, String operation, Exception cause, T fallback) {
            return fallback;
        }

        @Override
        public <T> T onWriteFailure(String key, String operation, Exception cause, T fallback) {
            return fallback;
        }

        @Override
        public String toString() {
            return "FAIL_OPEN";
        }
    };

    /**
     * 故障拒绝策略：读返回 fallback（通常为 null），写返回 fallback（通常为 false）
     *
     * <p>语义上表示"拒绝继续操作"，由调用方根据 fallback 值决定后续行为。
     * 适用于安全敏感场景（如布隆过滤器校验、限流器判断）。
     */
    RedisFailurePolicy FAIL_CLOSED = new RedisFailurePolicy() {
        @Override
        public <T> T onGetFailure(String key, String operation, Exception cause, T fallback) {
            return fallback;
        }

        @Override
        public <T> T onWriteFailure(String key, String operation, Exception cause, T fallback) {
            return fallback;
        }

        @Override
        public String toString() {
            return "FAIL_CLOSED";
        }
    };

    /**
     * 故障抛异常策略：读/写均抛出 {@link RedisOperationException}
     *
     * <p>适用于需要明确感知 Redis 故障的场景，让调用方统一处理异常。
     */
    RedisFailurePolicy FAIL_THROW = new RedisFailurePolicy() {
        @Override
        public <T> T onGetFailure(String key, String operation, Exception cause, T fallback) {
            throw new RedisOperationException(key, operation, cause);
        }

        @Override
        public <T> T onWriteFailure(String key, String operation, Exception cause, T fallback) {
            throw new RedisOperationException(key, operation, cause);
        }

        @Override
        public String toString() {
            return "FAIL_THROW";
        }
    };

    /**
     * 根据 {@link FailOpenPolicy} 枚举获取对应的 {@link RedisFailurePolicy} 实现
     *
     * @param policy 枚举策略（null 时默认返回 FAIL_OPEN）
     * @return 对应的策略实现
     */
    static RedisFailurePolicy from(FailOpenPolicy policy) {
        if (policy == null) {
            return FAIL_OPEN;
        }
        return switch (policy) {
            case FAIL_OPEN -> FAIL_OPEN;
            case FAIL_CLOSED -> FAIL_CLOSED;
            case FAIL_THROW -> FAIL_THROW;
        };
    }
}
