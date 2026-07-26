package com.njydsz.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.redis.service.RedisWorkerIdRegistry;
import com.njydsz.common.util.id.SnowflakeAutoConfiguration;
import com.njydsz.common.util.id.WorkerIdRegistry;

/**
 * Snowflake Redis 注册中心自动配置
 *
 * <p>当 classpath 上存在 {@link RedisTemplate} 与 {@link WorkerIdRegistry} 时，
 * 自动装配 {@link RedisWorkerIdRegistry} 作为 WorkerIdRegistry 的实现，
 * 供 {@link SnowflakeAutoConfiguration} 通过 {@code ObjectProvider<WorkerIdRegistry>} 注入。
 *
 * <p>{@link RedisWorkerIdRegistry} 内部已自带心跳续约任务与 {@link jakarta.annotation.PreDestroy} 释放逻辑，
 * 此配置类仅负责 Bean 注册，无需额外启动定时任务。
 *
 * <p><b>配置开关：</b>
 * <pre>{@code
 * ydsz:
 *   snowflake:
 *     redis-registry:
 *       enabled: true   # 默认启用，关闭后回退到本地分配策略
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RedisWorkerIdRegistry
 * @see SnowflakeAutoConfiguration
 */
@AutoConfiguration
@AutoConfigureBefore(SnowflakeAutoConfiguration.class)
@ConditionalOnClass({RedisTemplate.class, WorkerIdRegistry.class})
@ConditionalOnProperty(prefix = "ydsz.snowflake.redis-registry", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SnowflakeRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkerIdRegistry.class)
    @ConditionalOnBean(RedisTemplate.class)
    public RedisWorkerIdRegistry redisWorkerIdRegistry(RedisTemplate<String, Object> redisTemplate,
                                                        RedisProperties redisProperties) {
        return new RedisWorkerIdRegistry(redisTemplate, redisProperties);
    }
}
