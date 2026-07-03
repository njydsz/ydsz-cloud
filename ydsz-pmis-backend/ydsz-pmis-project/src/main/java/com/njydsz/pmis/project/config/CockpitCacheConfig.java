package com.njydsz.pmis.project.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

/**
 * 驾驶舱 Redis 缓存配置
 *
 * <p>为 CockpitReportService 的 @Cacheable 方法提供 RedisCacheManager，
 * 统一 TTL 5 分钟，避免高频驾驶舱查询反复击穿到数据库。
 *
 * <p>使用默认 JdkSerializationRedisSerializer（被缓存的 VO/Map 均已实现 Serializable），
 * 不缓存 null 值，由 @Cacheable 的 unless 条件过滤空结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class CockpitCacheConfig {

    /** 驾驶舱缓存统一 TTL：5 分钟 */
    private static final Duration COCKPIT_CACHE_TTL = Duration.ofMinutes(5);

    @Bean
    public CacheManager cockpitCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(COCKPIT_CACHE_TTL)
                .disableCachingNullValues();
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
}
