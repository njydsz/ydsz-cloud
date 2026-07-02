package com.njydsz.pmis.common.featureflag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 特性开关自动配置 (批次 20 P2-3)
 *
 * <p>注册 {@link LocalFeatureFlagService} 默认实现, 业务模块可注入
 * {@link FeatureFlagService} 接口使用.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Configuration
public class FeatureFlagAutoConfiguration {

    /**
     * 注册特性开关服务默认实现
     *
     * @return LocalFeatureFlagService 实例
     */
    @Bean
    @ConditionalOnMissingBean(FeatureFlagService.class)
    public FeatureFlagService featureFlagService() {
        return new LocalFeatureFlagService();
    }
}
