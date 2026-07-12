package com.njydsz.pmis.common.redis.guard;

import com.njydsz.pmis.common.redis.ops.ValueOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Redis 缓存防护组件 —— 防穿透 + 防击穿 + 防雪崩。
 * <p>
 * 对标 ydsz-common RedisCacheGuard，三大防护策略：
 * <ul>
 *   <li>防穿透：查询不存在数据时缓存空值（短 TTL），防止恶意频繁查询 DB</li>
 *   <li>防击穿：热点 Key 过期时加分布式锁，仅一个线程回源 DB，其余等待</li>
 *   <li>防雪崩：TTL 添加随机抖动，避免大量 Key 同时过期</li>
 * </ul>
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class RedisCacheGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheGuard.class);

    /** 空值标记 */
    private static final String NULL_PLACEHOLDER = "\0NULL\0";

    /** 默认空值缓存时间 */
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    /** 默认锁等待时间 */
    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);

    /** 默认锁持有时间 */
    private static final Duration LOCK_HOLD = Duration.ofSeconds(10);

    /** TTL 随机抖动基数（秒） */
    private static final int TTL_JITTER_BASE = 60;

    private final ValueOps valueOps;

    public RedisCacheGuard(ValueOps valueOps) {
        this.valueOps = valueOps;
    }

    /**
     * 带防护的缓存读取。
     * <p>
     * 防穿透 + 防雪崩（不包含防击穿，如需防击穿使用 {@link #getWithBreakdownGuard}）。
     * </p>
     *
     * @param key       缓存键
     * @param ttl       缓存过期时间（会自动添加随机抖动）
     * @param loader    回源加载器
     * @return 缓存值，不存在返回 null
     */
    public String getWithGuard(String key, Duration ttl, Supplier<String> loader) {
        // 1. 读缓存
        String cached = valueOps.get(key);

        // 2. 命中空值标记 → 直接返回 null（防穿透）
        if (NULL_PLACEHOLDER.equals(cached)) {
            return null;
        }

        // 3. 命中正常值 → 返回
        if (cached != null) {
            return cached;
        }

        // 4. 未命中 → 回源
        String value = loader.get();

        // 5. 回源结果为 null → 缓存空值标记（防穿透）
        if (value == null) {
            valueOps.set(key, NULL_PLACEHOLDER, NULL_TTL);
            log.debug("Cache miss (null cached): key={}", key);
            return null;
        }

        // 6. 回源结果不为 null → 缓存（TTL 加随机抖动防雪崩）
        Duration jitteredTtl = jitter(ttl);
        valueOps.set(key, value, jitteredTtl);
        return value;
    }

    /**
     * 带防击穿的缓存读取。
     * <p>
     * 防穿透 + 防击穿 + 防雪崩。
     * 热点 Key 过期时，仅一个线程回源 DB，其余线程等待后重读缓存。
     * </p>
     *
     * @param key       缓存键
     * @param ttl       缓存过期时间
     * @param loader    回源加载器
     * @return 缓存值，不存在返回 null
     */
    public String getWithBreakdownGuard(String key, Duration ttl, Supplier<String> loader) {
        // 1. 读缓存
        String cached = valueOps.get(key);

        // 2. 命中空值标记 → 直接返回 null
        if (NULL_PLACEHOLDER.equals(cached)) {
            return null;
        }

        // 3. 命中正常值 → 返回
        if (cached != null) {
            return cached;
        }

        // 4. 未命中 → 尝试获取锁（防击穿）
        String lockKey = key + ":lock";
        boolean locked = valueOps.setIfAbsent(lockKey, "1", LOCK_HOLD);

        if (locked) {
            try {
                // 双重检查：获取锁后再次读缓存（可能其他线程已加载）
                cached = valueOps.get(key);
                if (cached != null) {
                    return NULL_PLACEHOLDER.equals(cached) ? null : cached;
                }

                // 回源
                String value = loader.get();
                if (value == null) {
                    valueOps.set(key, NULL_PLACEHOLDER, NULL_TTL);
                    return null;
                }
                valueOps.set(key, value, jitter(ttl));
                return value;
            } finally {
                valueOps.delete(lockKey);
            }
        } else {
            // 未获取锁 → 短暂等待后重读缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cached = valueOps.get(key);
            if (cached != null && !NULL_PLACEHOLDER.equals(cached)) {
                return cached;
            }
            // 等待后仍无数据 → 直接回源（降级策略）
            return loader.get();
        }
    }

    /**
     * TTL 添加随机抖动，防止雪崩。
     *
     * @param baseTtl 基础 TTL
     * @return 加噪后的 TTL
     */
    private Duration jitter(Duration baseTtl) {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, TTL_JITTER_BASE);
        return baseTtl.plusSeconds(jitterSeconds);
    }

    /**
     * 主动失效缓存。
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        valueOps.delete(key);
        valueOps.delete(key + ":lock");
    }
}
