package com.njydsz.pmis.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisOperations;

/**
 * Redis 层自动配置
 *
 * <p>聚合 redis 模块所有配置类，通过 Spring Boot 3 自动装配机制注册。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link PmisCacheConfig} - Redis 缓存配置（序列化/TTL）</li>
 *   <li>{@link MultiLevelCacheConfig} - Caffeine + Redis 二级缓存</li>
 *   <li>{@link BloomFilterConfig} - 布隆过滤器（防缓存穿透）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(RedisOperations.class)
@Import({
    PmisCacheConfig.class,
    MultiLevelCacheConfig.class,
    BloomFilterConfig.class
})
public class RedisAutoConfiguration {
}
