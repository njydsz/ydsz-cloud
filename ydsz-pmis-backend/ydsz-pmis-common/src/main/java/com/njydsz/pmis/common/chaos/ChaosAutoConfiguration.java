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

    @Bean
    @ConditionalOnMissingBean(ChaosService.class)
    public ChaosService chaosService() {
        return new ChaosService(null);
    }
}
