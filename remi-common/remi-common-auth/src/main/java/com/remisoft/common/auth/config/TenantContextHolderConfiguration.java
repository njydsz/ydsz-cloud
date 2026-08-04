package com.remisoft.common.auth.config;

import com.remisoft.common.auth.context.TenantContextHolderImpl;
import com.remisoft.common.tenant.spi.TenantContextHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 租户上下文持有者配置。
 *
 * <p>注册 {@code TenantContextHolder} Bean（基于 ThreadLocal / TransmittableThreadLocal），
 *
 * <p>在请求进入时由 {@code WebAuthFilter} 写入租户 ID，请求结束由 {@code BaseHttpInterceptor} 清理。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
public class TenantContextHolderConfiguration {

    /**
     * 注册租户上下文持有者 Bean。
     *
     * <p>基于 ThreadLocal（或 TransmittableThreadLocal）在请求链路中传递租户 ID。
     * 仅在容器中不存在该类型 Bean 时创建，允许业务方自定义租户上下文实现。
     * 请求进入时由 WebAuthFilter 写入租户 ID，结束时由 BaseHttpInterceptor 清理，须确保配对以避免线程复用串号。</p>
     *
     * @return 租户上下文持有者实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantContextHolder tenantContextHolder() {
        return new TenantContextHolderImpl();
    }
}
