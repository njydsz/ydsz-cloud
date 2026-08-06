package com.remisoft.common.exception.handler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.remisoft.common.exception.config.ExceptionProperties;
import com.remisoft.common.exception.config.I18nConfiguration;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

/**
 * WebFlux 全局异常处理器自动配置
 *
 * <p>仅在 WebFlux 应用且类路径存在 {@link WebFluxExceptionHandler} 时装配。
 * 通过 {@code remi.exception.global-handler-enabled=true}（默认启用）控制是否启用。</p>
 *
 * <p><b>设计说明：</b>将 {@code @AutoConfiguration} 与 {@code @RestControllerAdvice} 解耦，
 * 避免在 Advice 类上叠加 Spring Boot 自动配置语义，提升可测试性与可读性。</p>
 *
 * @author remi-team
 * @since 1.0.0
 * @see WebFluxExceptionHandler
 */
@AutoConfiguration(after = I18nConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFluxConfigurer.class, WebFluxExceptionHandler.class})
@ConditionalOnProperty(prefix = "remi.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebFluxExceptionHandlerAutoConfiguration {

    /**
     * 创建 WebFlux 全局异常处理器 Bean
     *
     * @param environment       Spring 环境对象
     * @param messageSource    国际化消息源
     * @param exceptionMetrics  异常指标统计器（可选）
     * @param properties       异常模块配置属性（可选）
     * @return WebFlux 全局异常处理器实例
     */
    @Bean
    public WebFluxExceptionHandler webFluxExceptionHandler(Environment environment,
                                                           MessageSource messageSource,
                                                           ObjectProvider<ExceptionMetrics> exceptionMetrics,
                                                           ObjectProvider<ExceptionProperties> properties) {
        return new WebFluxExceptionHandler(environment, messageSource,
                exceptionMetrics.getIfAvailable(),
                properties.getIfAvailable());
    }
}
