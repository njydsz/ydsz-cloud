package com.njydsz.common.feign.assembler;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * NameAssembler 自动配置。
 *
 * <p>在未注册自定义 NameAssembler 时，注册 {@link NoOpNameAssembler} 兜底。
 * 业务模块（如 ydsz-userinfo-api）通过
 * {@code @ConditionalOnMissingBean(NameAssembler.class)} 覆盖此兜底。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NameAssemblerProperties.class)
@ConditionalOnProperty(prefix = "ydsz.feign.name-assembler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NameAssemblerAutoConfiguration {

    /**
     * 注册 NoOp 兜底实现（仅在无自定义实现时生效）。
     *
     * @param properties 富化组件配置
     * @return NoOpNameAssembler 实例
     */
    @Bean
    @ConditionalOnMissingBean(NameAssembler.class)
    public NameAssembler noOpNameAssembler(NameAssemblerProperties properties) {
        return new NoOpNameAssembler();
    }
}
