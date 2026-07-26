package com.njydsz.common.redis.tenant;

import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.redis.config.RedisProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 租户级 Redis 隔离自动配置
 *
 * <p>当 ydsz.redis.tenant.enabled=true 时，注册 TenantRedisKeyPrefixer Bean，
 * 为所有 Redis key 自动添加租户前缀，实现租户间数据隔离。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   redis:
 *     tenant:
 *       enabled: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(name = "ydsz.redis.tenant.enabled", havingValue = "true")
public class TenantRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TenantContextHolder.class)
    public TenantRedisKeyPrefixer tenantRedisKeyPrefixer(TenantContextHolder tenantContextHolder,
                                                          RedisProperties redisProperties) {
        boolean enabled = redisProperties.getTenant().isEnabled();
        return new TenantRedisKeyPrefixer(tenantContextHolder, enabled);
    }
}
