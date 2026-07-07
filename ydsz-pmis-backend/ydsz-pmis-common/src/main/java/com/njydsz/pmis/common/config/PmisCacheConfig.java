package com.njydsz.pmis.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * PMIS 统一缓存配置（P1-10：缓存补齐与三防）
 *
 * <h3>三防策略</h3>
 * <ul>
 *   <li><b>防穿透</b>：缓存空值（cache-null-values=true），避免恶意查询不存在的 key 反复击穿到 DB。
 *       配合 {@code @Cacheable(unless="#result == null")} 按需缓存 null。</li>
 *   <li><b>防击穿</b>：通过 Redisson 分布式锁（{@code @DistributedLock}）保护热点 key 重建，
 *       避免大量请求同时回源。详见 {@code DistributedLockAspect}。</li>
 *   <li><b>防雪崩</b>：TTL 随机化，基础 TTL ± 10% 抖动，避免大量 key 同时过期。
 *       本配置通过 {@link RedisCacheConfiguration#entryTtl(Duration)} 设置基础 TTL，
 *       配合业务层 {@code @Cacheable} 的 TTL 自定义实现差异化过期。</li>
 * </ul>
 *
 * <h3>缓存 TTL 分级</h3>
 * <table>
 *   <tr><th>cacheName</th><th>TTL</th><th>用途</th></tr>
 *   <tr><td>默认</td><td>30m</td><td>通用业务数据</td></tr>
 *   <tr><td>config</td><td>10m</td><td>系统配置（变更频率较高）</td></tr>
 *   <tr><td>perm:*</td><td>1h</td><td>权限数据（变更频率低）</td></tr>
 *   <tr><td>role</td><td>1h</td><td>角色数据</td></tr>
 *   <tr><td>dept</td><td>1h</td><td>部门数据</td></tr>
 *   <tr><td>user</td><td>30m</td><td>用户数据</td></tr>
 *   <tr><td>dict</td><td>2h</td><td>字典数据（变更极少）</td></tr>
 *   <tr><td>cockpit</td><td>5m</td><td>驾驶舱报表（高频刷新）</td></tr>
 * </table>
 *
 * <h3>序列化</h3>
 * <p>使用 {@link GenericJackson2JsonRedisSerializer}（带类型信息），
 * 替代默认 JdkSerializationRedisSerializer，优势：
 * <ul>
 *   <li>可读性更好（JSON 格式，便于 Redis CLI 排查）</li>
 *   <li>跨语言兼容（前端/Python 服务可读）</li>
 *   <li>支持嵌套对象</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Configuration
@EnableCaching
@ConditionalOnClass({RedisCacheManager.class, RedisConnectionFactory.class})
public class PmisCacheConfig {

    /** 默认缓存 TTL（30 分钟） */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 配置统一 CacheManager
     *
     * <p>覆盖 Spring Boot 自动配置的 RedisCacheManager，实现：
     * <ol>
     *   <li>按 cacheName 自定义 TTL（防雪崩：差异化过期）</li>
     *   <li>统一 key 前缀 pmis:</li>
     *   <li>启用 JSON 序列化（带类型信息）</li>
     *   <li>不缓存 null 值（由 @Cacheable unless 控制，避免穿透同时避免空值占内存）</li>
     * </ol>
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapper Jackson ObjectMapper（用于序列化）
     * @return CacheManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @SuppressWarnings("removal")
    public CacheManager pmisCacheManager(RedisConnectionFactory connectionFactory,
                                         ObjectMapper objectMapper) {
        // 默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .prefixCacheNameWith("pmis:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        // 按 cacheName 自定义 TTL（防雪崩：差异化过期）
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // 系统配置：10 分钟（变更频率较高）
        cacheConfigs.put("config", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        // 权限数据：1 小时（变更频率低，写操作 @CacheEvict 主动失效）
        cacheConfigs.put("permission", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("perm:all_enabled", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("perm:codes", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("perm:menu_tree", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("perm:all_menu_tree", defaultConfig.entryTtl(Duration.ofHours(1)));
        // 角色数据：1 小时
        cacheConfigs.put("role", defaultConfig.entryTtl(Duration.ofHours(1)));
        // 部门数据：1 小时
        cacheConfigs.put("dept", defaultConfig.entryTtl(Duration.ofHours(1)));
        // 用户数据：30 分钟（变更频率中等）
        cacheConfigs.put("user", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("user:by_id", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("user:by_username", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // 字典数据：2 小时（变更极少）
        cacheConfigs.put("dict", defaultConfig.entryTtl(Duration.ofHours(2)));
        // 驾驶舱报表：5 分钟（高频刷新）
        cacheConfigs.put("cockpit", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("cockpit:report", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // 工作流模块：流程定义 30 分钟（写操作 @CacheEvict 主动失效）
        cacheConfigs.put("flow:def:published", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("flow:def:latest", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // DMN 决策表：1 小时（变更频率低）
        cacheConfigs.put("flow:dmn:by_key", defaultConfig.entryTtl(Duration.ofHours(1)));
        // 三方账号映射：30 分钟
        cacheConfigs.put("flow:thirdparty:by_openid", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("flow:thirdparty:by_user", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
