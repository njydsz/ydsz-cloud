package com.njydsz.common.redis.enums;

/**
 * Redis 业务异常
 *
 * <p>当 Redis 操作因业务逻辑错误（如序列化失败、类型转换错误、参数非法）时抛出此异常。
 * 该异常属于不可恢复异常，不会触发重试，应由业务层处理。
 *
 * <p>适用场景：
 * <ul>
 *   <li>序列化/反序列化失败</li>
 *   <li>类型转换异常</li>
 *   <li>参数校验失败</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisBusinessException extends RedisOperationException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Redis 业务异常
     *
     * @param key       操作的 key
     * @param operation 操作名称
     * @param cause     原始异常
     */
    public RedisBusinessException(String key, String operation, Throwable cause) {
        super(key, operation, cause);
    }

    /**
     * 构造 Redis 业务异常（无 key 场景）
     *
     * @param operation 操作名称
     * @param message   错误描述
     */
    public RedisBusinessException(String operation, String message) {
        super(null, operation, new RuntimeException(message));
    }
}
