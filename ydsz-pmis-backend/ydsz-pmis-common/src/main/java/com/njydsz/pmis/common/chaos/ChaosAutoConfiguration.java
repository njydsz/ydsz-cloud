package com.njydsz.pmis.common.chaos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 混沌工程自动配置 (批次 20 P3-1)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Configuration
public class ChaosAutoConfiguration {

    /**
     * 当容器中不存在 {@link ChaosService} 时注册默认实例（FeatureFlagService 为 null，仅用于本地测试）
     *
     * @return ChaosService 实例
     */
    @Bean
    @ConditionalOnMissingBean(ChaosService.class)
    public ChaosService chaosService() {
        return new ChaosService(null);
    }
}
