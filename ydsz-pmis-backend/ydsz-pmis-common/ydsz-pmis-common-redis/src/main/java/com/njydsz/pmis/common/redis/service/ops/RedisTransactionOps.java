package com.njydsz.pmis.common.redis.service.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Redis 事务操作组件
 *
 * <p>提供 Redis 事务操作接口，包括：
 * <ul>
 *   <li>事务执行（executeInTransaction）</li>
 * </ul>
 *
 * <p>使用 RedisTemplate 的 {@link SessionCallback} 实现事务，
 * 所有在回调中的操作将作为一个原子事务执行。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * redisTransactionOps.executeInTransaction(operations -> {
 *     operations.opsForValue().set("key1", "value1");
 *     operations.opsForValue().set("key2", "value2");
 *     operations.opsForHash().put("hash1", "field1", "value1");
 *     return true;
 * });
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
@Component
public class RedisTransactionOps {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTransactionOps(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate 不能为 null");
    }

    /**
     * 在 Redis 事务中执行操作
     *
     * <p>使用 MULTI/EXEC 包裹回调中的所有操作，保证原子性。
     * 回调中通过传入的 {@link RedisTemplate} 执行的所有命令将在一个事务中执行。
     *
     * <p><b>注意：</b>事务中的命令不会立即执行，而是在 EXEC 时批量执行，
     * 因此事务中无法获取中间结果（如 GET 的返回值）。
     *
     * @param callback 事务回调函数，参数为 RedisTemplate 实例
     * @param <T>      回调返回值类型
     * @return 事务执行结果列表（EXEC 返回值），失败时返回 null
     */
    public <T> List<Object> executeInTransaction(Function<RedisTemplate<String, Object>, T> callback) {
        if (callback == null) {
            log.warn("【Redis】事务执行失败：回调函数不能为空");
            return null;
        }
        try {
            return redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(@NonNull RedisOperations operations) {
                    operations.multi();
                    try {
                        callback.apply(redisTemplate);
                    } catch (Exception e) {
                        operations.discard();
                        throw e;
                    }
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.error("【Redis】事务执行失败 | error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 在 Redis 事务中执行操作（无返回值版本）
     *
     * <p>使用 MULTI/EXEC 包裹回调中的所有操作，保证原子性。
     * 适用于不需要处理事务返回结果的场景。
     *
     * @param callback 事务回调函数，参数为 RedisTemplate 实例
     * @return true-事务执行成功，false-事务执行失败
     */
    public boolean executeInTransaction(Runnable callback) {
        if (callback == null) {
            log.warn("【Redis】事务执行失败：回调函数不能为空");
            return false;
        }
        try {
            List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(@NonNull RedisOperations operations) {
                    operations.multi();
                    try {
                        callback.run();
                    } catch (Exception e) {
                        operations.discard();
                        throw e;
                    }
                    return operations.exec();
                }
            });
            return results != null;
        } catch (Exception e) {
            log.error("【Redis】事务执行失败 | error={}", e.getMessage());
            return false;
        }
    }
}
