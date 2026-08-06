package com.remisoft.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import com.remisoft.common.redis.service.RedisWorkerIdRegistry;
import com.remisoft.common.util.id.WorkerIdRegistry;

/**
 * Snowflake Redis 注册中心自动配置
 *
 * <p>当 classpath 上存在 {@link RedisTemplate} 与 {@link WorkerIdRegistry} 时，
 * 自动装配 {@link RedisWorkerIdRegistry} 作为 WorkerIdRegistry 的实现，
 * 供 {@link com.remisoft.common.util.id.SnowflakeIdGenerator} 通过
 * {@code ObjectProvider<WorkerIdRegistry>} 注入。
 *
 * <p>{@link RedisWorkerIdRegistry} 内部已自带心跳续约任务与 {@link jakarta.annotation.PreDestroy} 释放逻辑，
 * 此配置类仅负责 Bean 注册，无需额外启动定时任务。
 *
 * <p><b>配置开关：</b>
 * <pre>{@code
 * remi:
 *   snowflake:
 *     redis-registry:
 *       enabled: true   # 默认启用，关闭后回退到本地分配策略
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see RedisWorkerIdRegistry
 */
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, WorkerIdRegistry.class})
@ConditionalOnProperty(prefix = "remi.snowflake.redis-registry", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SnowflakeRedisAutoConfiguration {

    /**
     * 注册基于 Redis 的 WorkerId 注册中心，作为 {@link WorkerIdRegistry} 的实现。
     *
     * <p>仅在容器中尚不存在 {@code WorkerIdRegistry} 且已有 {@code RedisTemplate} 时装配，
     * 由 {@code SnowflakeIdGenerator} 通过 {@code ObjectProvider<WorkerIdRegistry>} 惰性注入。
     * 本 Bean 自带心跳续约与 {@code @PreDestroy} 释放逻辑，配置类不额外启动定时任务。
     *
     * @param redisTemplate  基础模板，不会为 null
     * @param redisProperties 全局配置（含 key 前缀、租约时长），不会为 null
     * @return Redis 版 WorkerId 注册中心实例
     */
    @Bean
    @ConditionalOnMissingBean(WorkerIdRegistry.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisWorkerIdRegistry redisWorkerIdRegistry(RedisTemplate<String, Object> redisTemplate,
                                                        RedisProperties redisProperties) {
        return new RedisWorkerIdRegistry(redisTemplate, redisProperties);
    }
}
