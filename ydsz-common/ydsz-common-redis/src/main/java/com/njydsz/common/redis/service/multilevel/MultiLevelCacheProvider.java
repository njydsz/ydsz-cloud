package com.njydsz.common.redis.service.multilevel;

import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.service.CacheProvider;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import lombok.extern.slf4j.Slf4j;

/**
 * 多级缓存提供者（本地 L1 + Redis L2）
 *
 * <p>实现 {@link CacheProvider} 接口，提供透明的二级缓存能力：
 * <ul>
 *   <li><b>L1（本地缓存）</b>：进程内缓存，容量小、TTL 短（默认 30 秒），
 *       用于吸收高频读取请求，避免频繁访问 Redis</li>
 *   <li><b>L2（分布式缓存）</b>：基于 Redis 的远程缓存，TTL 长（由调用方指定），
 *       用于跨实例数据共享</li>
 * </ul>
 *
 * <p><b>L1 实现选择：</b>
 * <ul>
 *   <li>若 Caffeine 在 classpath 中：使用 Caffeine（高性能，支持 LRU 淘汰、命中率统计）</li>
 *   <li>否则：使用 ConcurrentHashMap + 时间戳（简单实现，无容量淘汰，仅 TTL 过期）</li>
 * </ul>
 *
 * <p><b>缓存读取策略：</b>
 * <ol>
 *   <li>先查 L1，命中则直接返回</li>
 *   <li>L1 未命中，查 L2（Redis），命中则回填 L1</li>
 *   <li>L2 未命中，执行 supplier 回源，同时写入 L1 和 L2</li>
 * </ol>
 *
 * <p><b>缓存失效策略：</b>
 * <ul>
 *   <li>删除操作：先删 L2，再删 L1</li>
 *   <li>写入操作：只写 L2，L1 数据等待自然过期或下次读取时回填</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public MultiLevelCacheProvider multiLevelCache(RedisStringOps stringOps,
 *                                                     RedisTemplate<String, Object> redisTemplate,
 *                                                     RedisProperties properties) {
 *         return new MultiLevelCacheProvider(stringOps, redisTemplate, properties)
 *                 .withL1Config(2000, 30);  // L1: 2000条, 30秒TTL
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MultiLevelCacheProvider implements CacheProvider, Closeable {

    // ==================== 配置常量 ====================

    /** L1 默认最大容量（条目数 */
    private static final int DEFAULT_L1_MAX_SIZE = 1000;
    /** L1 默认 TTL（秒） */
    private static final long DEFAULT_L1_TTL_SECONDS = 30;
    /** Caffeine 缓存类名（用于反射检测） */
    private static final String CAFFEINE_CACHE_CLASS = "com.github.benmanes.caffeine.cache.Cache";
    private static final String CAFFEINE_BUILDER_CLASS = "com.github.benmanes.caffeine.cache.Caffeine";

    // ==================== 依赖组件 ====================

    private final RedisStringOps stringOps;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;

    // ==================== L1 本地缓存 ====================

    /** Caffeine 缓存实例（可选） */
    private final Object caffeineCache;

    /** L1 是否使用 Caffeine */
    private final boolean usingCaffeine;

    /** 简单 ConcurrentHashMap 实现（非 Caffeine 时的回退） */
    private final ConcurrentMap<String, TtlValue> simpleL1Cache;

    // ==================== 配置 ====================

    private int l1MaxSize = DEFAULT_L1_MAX_SIZE;
    private long l1TtlSeconds = DEFAULT_L1_TTL_SECONDS;

    /**
     * 带 TTL 的值包装
     */
    private static class TtlValue {
        final Object value;
        final long expireAt;

        TtlValue(Object value, long ttlSeconds) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + (ttlSeconds * 1000);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    // ==================== 构造 ====================

    /**
     * 创建多级缓存提供者
     *
     * @param stringOps      Redis String 操作组件
     * @param redisTemplate  RedisTemplate 实例
     * @param redisProperties Redis 配置
     */
    public MultiLevelCacheProvider(RedisStringOps stringOps,
                                   RedisTemplate<String, Object> redisTemplate,
                                   RedisProperties redisProperties) {
        this.stringOps = Objects.requireNonNull(stringOps, "stringOps 不能为 null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为 null");
        this.redisProperties = redisProperties;

        // 尝试初始化 Caffeine L1 缓存
        Object cache = null;
        boolean caffeineAvailable = false;
        try {
            cache = createCaffeineCache();
            caffeineAvailable = true;
            log.info("【MultiLevelCache】使用 Caffeine 作为 L1 缓存 | maxSize={} | ttl={}s",
                    l1MaxSize, l1TtlSeconds);
        } catch (NoClassDefFoundError | Exception e) {
            log.info("【MultiLevelCache】Caffeine 不可用，使用 ConcurrentHashMap 作为 L1 回退");
        }
        this.caffeineCache = cache;
        this.usingCaffeine = caffeineAvailable;
        this.simpleL1Cache = caffeineAvailable ? null : new ConcurrentHashMap<>();
    }

    /**
     * 创建 Caffeine 缓存实例（通过编译时引用，运行时 optional）
     */
    private Object createCaffeineCache() {
        // 此代码需要 Caffeine 在 classpath 中，pom.xml 已声明为 optional
        return com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(java.time.Duration.ofSeconds(l1TtlSeconds))
                .build();
    }

    // ==================== Fluent 配置 API ====================

    /**
     * 设置 L1 缓存参数
     *
     * @param maxSize    最大条目数
     * @param ttlSeconds TTL（秒）
     * @return this
     */
    public MultiLevelCacheProvider withL1Config(int maxSize, long ttlSeconds) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("L1 maxSize 必须大于 0");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("L1 ttlSeconds 必须大于 0");
        }
        this.l1MaxSize = maxSize;
        this.l1TtlSeconds = ttlSeconds;
        return this;
    }

    // ==================== CacheProvider 实现 ====================

    /**
     * 获取缓存（L1 → L2 多级读取）
     */
    @Override
    public <T> T get(String key, Class<T> clazz) {
        // 1. 尝试 L1
        Object l1Value = getFromL1(key);
        if (l1Value != null) {
            log.debug("【MultiLevelCache】L1 命中 | key={}", key);
            if (clazz.isInstance(l1Value)) {
                return clazz.cast(l1Value);
            }
        }

        // 2. 尝试 L2（Redis）
        T l2Value = stringOps.get(key, clazz);
        if (l2Value != null) {
            log.debug("【MultiLevelCache】L2 命中 | key={}", key);
            // 回填 L1
            putToL1(key, l2Value);
            return l2Value;
        }

        return null;
    }

    /**
     * 写入缓存（只写 L2，L1 自然过期或删除）
     */
    @Override
    public boolean set(String key, Object value, long expireSeconds) {
        boolean result = stringOps.set(key, value, expireSeconds);
        if (result) {
            // 删除 L1 中的旧值，让下次读取时回填最新数据
            invalidateL1(key);
        }
        return result;
    }

    /**
     * 写入缓存（L1 + L2 两级写入，适用于预热场景）
     *
     * @param key           缓存 key
     * @param value         值
     * @param expireSeconds L2 过期时间（秒）
     * @return true-写入成功
     */
    public boolean setWithL1(String key, Object value, long expireSeconds) {
        boolean result = stringOps.set(key, value, expireSeconds);
        if (result) {
            putToL1(key, value);
        }
        return result;
    }

    /**
     * 删除缓存（L1 + L2 两级删除）
     */
    @Override
    public boolean delete(String key) {
        boolean result = stringOps.del(key);
        invalidateL1(key);
        return result;
    }

    /**
     * 批量删除
     */
    @Override
    public void delete(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        stringOps.del(keys);
        keys.forEach(this::invalidateL1);
    }

    /**
     * 执行 Lua 脚本（多级缓存不支持脚本操作，转发到 Redis）
     */
    @Override
    public <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args) {
        RedisScript<T> redisScript = new DefaultRedisScript<>(script, returnType);
        return redisTemplate.execute(redisScript, keys, args);
    }

    /**
     * 多级缓存 getOrCompute
     *
     * <p>读取顺序：L1 → L2 → supplier 回源
     */
    @Override
    public <T> T getOrCompute(String key, long expireSeconds,
                              java.util.function.Supplier<T> supplier, Class<T> clazz) {
        // 1. 尝试多级读取
        T value = get(key, clazz);
        if (value != null) {
            return value;
        }

        // 2. 回源（使用简单同步锁防止击穿）
        synchronized (this) {
            // 双重检查
            value = get(key, clazz);
            if (value != null) {
                return value;
            }
            // 执行回源
            T computed = supplier.get();
            if (computed != null) {
                set(key, computed, expireSeconds);
            }
            return computed;
        }
    }

    // ==================== L1 辅助方法 ====================

    /**
     * 从 L1 读取
     */
    private Object getFromL1(String key) {
        if (usingCaffeine) {
            try {
                // 通过 Caffeine Cache.getIfPresent 获取
                return ((com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCache)
                        .getIfPresent(key);
            } catch (Exception e) {
                log.debug("【MultiLevelCache】L1 读取失败 | key={}", key);
                return null;
            }
        } else {
            // ConcurrentHashMap 实现
            TtlValue ttlValue = simpleL1Cache.get(key);
            if (ttlValue == null) {
                return null;
            }
            if (ttlValue.isExpired()) {
                simpleL1Cache.remove(key);
                return null;
            }
            return ttlValue.value;
        }
    }

    /**
     * 写入 L1 缓存
     */
    private void putToL1(String key, Object value) {
        if (value == null) {
            return;
        }
        if (usingCaffeine) {
            try {
                ((com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCache)
                        .put(key, value);
            } catch (Exception e) {
                log.debug("【MultiLevelCache】L1 写入失败 | key={}", key);
            }
        } else {
            // ConcurrentHashMap 实现：简单容量保护
            if (simpleL1Cache.size() >= l1MaxSize) {
                // 清理过期条目
                simpleL1Cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
                // 仍然超限则放弃写入（下次自然回填）
                if (simpleL1Cache.size() >= l1MaxSize) {
                    log.debug("【MultiLevelCache】L1 容量已满，跳过写入 | key={}", key);
                    return;
                }
            }
            simpleL1Cache.put(key, new TtlValue(value, l1TtlSeconds));
        }
    }

    /**
     * 失效 L1 缓存
     */
    private void invalidateL1(String key) {
        if (usingCaffeine) {
            try {
                ((com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCache)
                        .invalidate(key);
            } catch (Exception e) {
                log.debug("【MultiLevelCache】L1 失效失败 | key={}", key);
            }
        } else {
            simpleL1Cache.remove(key);
        }
    }

    /**
     * 清空所有 L1 缓存
     */
    public void invalidateAllL1() {
        if (usingCaffeine) {
            try {
                ((com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCache)
                        .invalidateAll();
                log.info("【MultiLevelCache】L1 全部失效（Caffeine）");
            } catch (Exception e) {
                log.warn("【MultiLevelCache】L1 全部失效失败", e);
            }
        } else {
            simpleL1Cache.clear();
            log.info("【MultiLevelCache】L1 全部失效（ConcurrentHashMap）");
        }
    }

    /**
     * 获取 L1 缓存当前大小
     */
    public long getL1Size() {
        if (usingCaffeine) {
            try {
                return ((com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCache)
                        .estimatedSize();
            } catch (Exception e) {
                return -1;
            }
        } else {
            return simpleL1Cache.size();
        }
    }

    /**
     * 检查是否使用 Caffeine
     */
    public boolean isUsingCaffeine() {
        return usingCaffeine;
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭缓存（清理资源）
     */
    @PreDestroy
    @Override
    public void close() {
        invalidateAllL1();
        log.info("【MultiLevelCache】多级缓存已关闭");
    }
}
