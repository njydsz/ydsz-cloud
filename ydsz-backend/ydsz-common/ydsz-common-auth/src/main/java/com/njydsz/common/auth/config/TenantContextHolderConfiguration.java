package com.njydsz.common.auth.config;

import com.njydsz.common.auth.context.TenantContextHolderImpl;
import com.njydsz.common.core.context.TenantContextHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 租户上下文持有者配置
 *
 * <p>注册 TenantContextHolder 实现，供其他模块（如 Redis）使用
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
public class TenantContextHolderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantContextHolder tenantContextHolder() {
        return new TenantContextHolderImpl();
    }
}
