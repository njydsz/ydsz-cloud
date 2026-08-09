package com.njydsz.common.core.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.BaseResponse;

/**
 * Core 模块自动配置类。
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定，
 * 使 {@code ydsz.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。</p>
 *
 * <p>当 Spring {@link MessageSource} 可用时，自动注册 {@link SpringMessageResolver}
 * 并绑定到 {@link BaseResponse}，使响应消息支持国际化。</p>
 *
 * <p><b>启用条件：</b>当 {@code ydsz.core.enabled=true} 时生效（默认启用）。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

    /**
     * 注册 SpringMessageResolver 并注入到 BaseResponse。
     *
     * <p>通过静态持有方式使统一的国际化解析能力在任意位置可用
     * （包括非 Spring Bean 中的静态工厂方法）。
     * 仅当容器中存在 MessageSource Bean 时生效（如 starter 模块配置了 MessageSource）。</p>
     *
     * @param messageSource Spring 消息源
     * @return SpringMessageResolver 实例
     */
    @Bean
    @ConditionalOnBean(MessageSource.class)
    public SpringMessageResolver springMessageResolver(MessageSource messageSource) {
        SpringMessageResolver resolver = new SpringMessageResolver(messageSource);
        BaseResponse.setResolverIfAbsent(resolver);
        return resolver;
    }

    /**
     * 将 CoreProperties 中的分页配置传播到 PageConstants 运行时覆盖值。
     */
    @Bean
    PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
        return new PageConstantsInitializer(properties);
    }

    static class PageConstantsInitializer implements SmartInitializingSingleton {

        private final CoreProperties properties;

        PageConstantsInitializer(CoreProperties properties) {
            this.properties = properties;
        }

        @Override
        public void afterSingletonsInstantiated() {
            PageConstants.init(properties);
        }
    }
}
