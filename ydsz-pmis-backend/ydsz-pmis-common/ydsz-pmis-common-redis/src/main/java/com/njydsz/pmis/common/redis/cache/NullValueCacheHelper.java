package com.njydsz.pmis.common.redis.cache;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * 缓存空值保护工具类
 *
 * <p>防止缓存穿透：当数据库查询返回 null 时，缓存一个空占位符，
 * 避免后续请求直接打到数据库。
 *
 * <p>用法：
 * <pre>{@code
 * Object value = NullValueCacheHelper.getOrLoad(cache, key,
 *     () -> queryFromDatabase(id), Duration.ofMinutes(5), User.class);
 * }</pre>
 *
 * <p>与 {@link com.njydsz.pmis.common.redis.config.RedisProperties#getKeyPrefix()} 配合使用：
 * 如果配置了 keyPrefix，所有缓存的 key 都会自动加上前缀。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class NullValueCacheHelper {

    /**
     * 空值占位符：缓存中以该字符串表示"数据库无此记录"
     */
    private static final String NULL_VALUE_PLACEHOLDER = "__NULL__";

    /**
     * 防穿透互斥锁默认租约时间（分钟）
     * <p>短暂持有后即过期，避免异常情况下的死锁
     */
    private static final int NULL_LOCK_TTL_MINUTES = 5;

    /**
     * 从缓存获取数据（带类型转换），如果缓存未命中则加载并缓存
     *
     * <p><b>并发保护：</b>使用 {@code setIfAbsent} 原子写入空值占位符，
     * 避免多线程同时回源导致并发穿透。</p>
     *
     * <p><b>类型安全：</b>使用传入的 {@code clazz} 进行反序列化，
     * 避免发生 {@link ClassCastException}。</p>
     *
     * @param cache StringRedisTemplate 实例
     * @param key 缓存 Key
     * @param loader 数据加载函数
     * @param ttl 缓存有效期
     * @param clazz 目标类型
     * @param <T> 数据类型
     * @return 缓存的数据
     */
    public static <T> T getOrLoad(StringRedisTemplate cache, String key,
            Supplier<T> loader, Duration ttl, Class<T> clazz) {
        String cached = cache.opsForValue().get(key);
        if (cached != null) {
            if (NULL_VALUE_PLACEHOLDER.equals(cached)) {
                return null;
            }
            try {
                return YdszJson.toObject(cached, clazz);
            } catch (Exception e) {
                log.warn("【NullValueCacheHelper】反序列化失败，将回源加载 | key={} | targetClass={} | error={}",
                        key, clazz.getName(), e.getMessage());
            }
        }

        T value = loader.get();
        if (value == null) {
            // 使用 setIfAbsent 原子写入空占位符，避免多线程同时回源后并发写入
            // 设置较短 TTL（5 分钟），防止 null 值长期占用缓存
            cache.opsForValue().setIfAbsent(key, NULL_VALUE_PLACEHOLDER, Duration.ofMinutes(5));
        } else {
            cache.opsForValue().set(key, YdszJson.toJson(value), ttl);
        }
        return value;
    }

    /**
     * 删除空值占位符缓存
     *
     * <p>当数据库中新创建了数据后，可以调用此方法清除空值缓存，
     * 确保下次读取能获取到最新数据。
     *
     * @param cache StringRedisTemplate 实例
     * @param key 缓存 Key
     */
    public static void evictNullCache(StringRedisTemplate cache, String key) {
        String cached = cache.opsForValue().get(key);
        if (NULL_VALUE_PLACEHOLDER.equals(cached)) {
            cache.delete(key);
        }
    }
}
