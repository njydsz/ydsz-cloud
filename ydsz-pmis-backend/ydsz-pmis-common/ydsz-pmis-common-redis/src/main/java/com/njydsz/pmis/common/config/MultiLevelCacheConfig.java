package com.njydsz.pmis.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置 (P1-11: Caffeine L1 + Redis L2)
 *
 * <h3>架构</h3>
 * <pre>
 *   请求 → L1 Caffeine (本地内存, ~0ms) → L2 Redis (~1ms) → DB
 * </pre>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>字典数据：变更极少，读取极频繁 → L1 TTL 5min</li>
 *   <li>系统配置：变更较少，读取频繁 → L1 TTL 2min</li>
 *   <li>权限数据：变更少，读取频繁 → L1 TTL 5min</li>
 *   <li>不适用：实时性要求高的数据（如库存、余额）不进 L1</li>
 * </ul>
 *
 * <h3>一致性策略</h3>
 * <ul>
 *   <li>L1 (Caffeine) 为短 TTL 本地缓存，自然过期后回源 L2</li>
 *   <li>L2 (Redis) 为共享缓存，通过 @CacheEvict 主动失效</li>
 *   <li>跨实例 L1 失效：通过 Redis Pub/Sub 广播失效消息（可选，L1 短 TTL 可接受秒级延迟）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 * &#64;Autowired
 * private MultiLevelCacheService cacheService;
 *
 * // 读取
 * DictItem item = cacheService.get("dict:gender:1", DictItem.class, () -> dictService.getById(1));
 *
 * // 写入/更新（自动失效 L1 + L2）
 * cacheService.put("dict:gender:1", item);
 *
 * // 删除
 * cacheService.evict("dict:gender:1");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Configuration
@ConditionalOnClass({Caffeine.class, RedisTemplate.class})
@ConditionalOnProperty(name = "pmis.cache.multi-level.enabled", havingValue = "true", matchIfMissing = true)
public class MultiLevelCacheConfig {

    /**
     * L1 本地缓存 (Caffeine)
     *
     * <p>配置策略：
     * <ul>
     *   <li>最大 1000 条记录（防止 OOM）</li>
     *   <li>写入后 5 分钟过期（短 TTL 保证最终一致性）</li>
     *   <li>基于容量淘汰（LRU）</li>
     * </ul>
     */
    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats() // 开启统计（配合 Actuator 暴露命中率）
                .build();
    }

    /**
     * L1 字典数据专用缓存 (更长 TTL, 更大容量)
     * <p>字典数据变更极少, 可使用更长 TTL 和更大容量
     */
    @Bean
    public Cache<String, Object> dictLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * L1 系统配置专用缓存
     */
    @Bean
    public Cache<String, Object> configLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
