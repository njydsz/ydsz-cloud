package com.njydsz.common.safe.crypto;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * 防重放 Nonce 缓存。
 *
 * <p>用于接口签名防重放攻击的 nonce 存储，支持：
 * <ul>
 *   <li>TTL 自动过期：nonce 在指定时间后自动失效</li>
 *   <li>定时清理任务：{@link #cleanExpiredNonces()} 定期清理过期 nonce</li>
 *   <li>容量限制：最大 10000 条，避免内存膨胀</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Autowired
 * private NonceCache nonceCache;
 *
 * public void verifyRequest(String nonce, long timestamp) {
 *     if (nonceCache.exists(nonce)) {
 *         throw new BusinessException("请求已处理，请勿重复提交");
 *     }
 *     nonceCache.put(nonce);
 *     // 继续处理请求...
 * }
 * }</pre>
 *
 * @since 1.0.0
 * 
 */
public class NonceCache {

    private static final Logger log = LoggerFactory.getLogger(NonceCache.class);

    /**
     * 默认过期时间（秒），5 分钟
     */
    private static final long DEFAULT_EXPIRE_SECONDS = 300;

    /**
     * 默认最大缓存容量
     */
    private static final long DEFAULT_MAX_SIZE = 10000;

    /**
     * nonce 缓存，使用 ydsz-common-cache 实现 TTL 自动过期
     */
    private final Cache<String, Long> cache;

    /**
     * 过期时间（秒）
     */
    private final long expireSeconds;

    /**
     * 统计：累计添加的 nonce 数量
     */
    private final AtomicLong putCount = new AtomicLong(0);

    /**
     * 统计：累计被拒绝的重复 nonce 数量
     */
    private final AtomicLong rejectCount = new AtomicLong(0);

    /**
     * 构建 Nonce 缓存（使用默认配置）。
     */
    public NonceCache() {
        this(DEFAULT_EXPIRE_SECONDS, DEFAULT_MAX_SIZE);
    }

    /**
     * 构建 Nonce 缓存。
     *
     * @param expireSeconds 过期时间（秒）
     * @param maxSize       最大缓存容量
     */
    public NonceCache(long expireSeconds, long maxSize) {
        this.expireSeconds = expireSeconds;
        this.cache = YdszCache.<String, Long>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .removalListener((String key, Long value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Nonce 缓存淘汰: key={}, cause={}", key, cause);
                    }
                })
                .build();

        log.info("Nonce 缓存已初始化: expire={}s, maxSize={}", expireSeconds, maxSize);
    }

    /**
     * 检查 nonce 是否已存在。
     *
     * @param nonce 待检查的 nonce
     * @return 存在返回 true，不存在返回 false
     */
    public boolean exists(String nonce) {
        return cache.getIfPresent(nonce) != null;
    }

    /**
     * 存入 nonce。
     *
     * <p>如果 nonce 已存在，返回 false（表示请求重放）。
     * 如果 nonce 不存在，存入并返回 true。
     *
     * @param nonce 待存入的 nonce
     * @return 存入成功返回 true，nonce 已存在返回 false
     */
    public boolean put(String nonce) {
        return put(nonce, System.currentTimeMillis());
    }

    /**
     * 存入 nonce（带时间戳）。
     *
     * @param nonce     nonce 值
     * @param timestamp 时间戳
     * @return 存入成功返回 true，nonce 已存在返回 false
     */
    public boolean put(String nonce, long timestamp) {
        // 使用 putIfAbsent 保证原子性，避免并发请求同时通过检查
        Long previous = cache.putIfAbsent(nonce, timestamp);
        if (previous != null) {
            rejectCount.incrementAndGet();
            return false;
        }
        putCount.incrementAndGet();
        return true;
    }

    /**
     * 校验并消费 nonce（原子操作）。
     *
     * <p>用于接口签名防重放场景，将"检查 nonce 是否存在"和"存入 nonce"合并为原子操作，
     * 避免并发请求同时通过检查。如果 nonce 已存在，表示请求重放，返回 false。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * @Autowired
     * private NonceCache nonceCache;
     *
     * public void verifySignature(String nonce, String signature, String body) {
     *     // 原子校验 nonce 是否重复
     *     if (!nonceCache.verifyAndConsume(nonce)) {
     *         throw new BusinessException("请求已处理，请勿重复提交");
     *     }
     *     // 继续处理请求...
     * }
     * }</pre>
     *
     * @param nonce 待校验的 nonce
     * @return true 表示 nonce 首次出现（可继续处理），false 表示重复请求（应拒绝）
     */
    public boolean verifyAndConsume(String nonce) {
        if (nonce == null || nonce.isEmpty()) {
            return false;
        }
        return put(nonce, System.currentTimeMillis());
    }

    /**
     * 存入 nonce 并设置独立的过期时间。
     *
     * <p>注意：Caffeine 的过期时间是在缓存构建时全局设置的，
     * 此方法存入的 nonce 仍然使用全局 expireSeconds。
     * 如果需要独立过期时间，建议使用 Redis 实现。
     *
     * @param nonce           nonce 值
     * @param expireSeconds   独立过期时间（秒）（预留参数，当前使用全局配置）
     * @return 存入成功返回 true，nonce 已存在返回 false
     */
    public boolean put(String nonce, long timestamp, long expireSeconds) {
        return put(nonce, timestamp);
    }

    /**
     * 移除指定 nonce。
     *
     * @param nonce 待移除的 nonce
     */
    public void remove(String nonce) {
        cache.invalidate(nonce);
    }

    /**
     * 清空所有 nonce。
     */
    public void clear() {
        cache.invalidateAll();
        log.info("Nonce 缓存已清空");
    }

    /**
     * 获取当前缓存大小。
     *
     * @return 缓存条目数
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取过期时间（秒）。
     *
     * @return 过期时间
     */
    public long getExpireSeconds() {
        return expireSeconds;
    }

    /**
     * 累计添加的 nonce 数量。
     *
     * @return 添加次数
     */
    public long getPutCount() {
        return putCount.get();
    }

    /**
     * 累计被拒绝的重复 nonce 数量。
     *
     * @return 拒绝次数
     */
    public long getRejectCount() {
        return rejectCount.get();
    }

    /**
     * 定时清理过期 nonce。
     *
     * <p>ydsz-common-cache 的 TTL 是懒清理（访问时触发），此定时任务
     * 主动触发清理，确保不占用内存。
     * 执行频率默认 60 秒，可通过 {@code ydsz.safe.nonce-clean-interval} 配置。
     */
    @Scheduled(fixedRateString = "${ydsz.safe.nonce-clean-interval:60000}",
            initialDelayString = "${ydsz.safe.nonce-clean-initial-delay:60000}")
    public void cleanExpiredNonces() {
        long before = cache.estimatedSize();
        cache.cleanUp();
        long after = cache.estimatedSize();
        long cleaned = before - after;

        if (cleaned > 0) {
            log.info("Nonce 缓存定时清理完成: 清理前={}, 清理后={}, 清理数量={}", before, after, cleaned);
        }
    }
}
