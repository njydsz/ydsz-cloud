package com.remisoft.common.feign.assembler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NameAssembler 自动配置。
 *
 * <p>仅注册 {@link NameAssemblerProperties}，{@link NameAssembler} 接口的默认实现
 * 由持有 OrgQueryClient 的业务模块（如 remi-userinfo-api）通过自身的 AutoConfiguration 注册。
 *
 * <p>当 classpath 中没有 OrgQueryClient 时，本配置不会注册任何 NameAssembler Bean，
 * 业务方需要自行实现或通过 {@code @ConditionalOnMissingBean} 提供降级实现。
 *
 * <p>开关：{@code remi.feign.name-assembler.enabled=true}（默认开启）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "remi.feign.name-assembler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NameAssemblerProperties.class)
public class NameAssemblerAutoConfiguration {

    /**
     * 占位 Bean：当业务方未提供任何 NameAssembler 实现时，提供一个 NoOp 实现，
     * 避免业务模块因 {@code @Autowired NameAssembler} 注入失败而启动异常。
     *
     * <p>该实现的所有方法均返回空 Map / null，不进行任何 Feign 调用，
     * 适合不需要名称富化的场景（如纯网关、定时任务模块）。
     *
     * <p>业务方提供真实实现后（如 {@code UserInfoNameAssembler}），本 Bean 不会注册。
     */
    @Bean
    @ConditionalOnMissingBean(NameAssembler.class)
    public NameAssembler noOpNameAssembler() {
        return new NoOpNameAssembler();
    }
}
