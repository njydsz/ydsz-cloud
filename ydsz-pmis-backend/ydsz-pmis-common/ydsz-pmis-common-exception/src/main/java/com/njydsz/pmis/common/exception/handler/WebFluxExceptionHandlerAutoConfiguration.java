package com.njydsz.pmis.common.exception.handler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.njydsz.pmis.common.exception.config.I18nConfiguration;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;

/**
 * WebFlux 全局异常处理器自动配置
 *
 * <p>仅在 WebFlux 应用且类路径存在 {@link WebFluxExceptionHandler} 时装配。
 * 通过 {@code ydsz.exception.global-handler-enabled=true}（默认启用）控制是否启用。</p>
 *
 * <p><b>设计说明：</b>将 {@code @AutoConfiguration} 与 {@code @RestControllerAdvice} 解耦，
 * 避免在 Advice 类上叠加 Spring Boot 自动配置语义，提升可测试性与可读性。</p>
 *
 * @author ydsz-pmis-team
 * @since 3.0.0
 * @see WebFluxExceptionHandler
 */
@AutoConfiguration(after = I18nConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFluxConfigurer.class, WebFluxExceptionHandler.class})
@ConditionalOnProperty(prefix = "ydsz.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebFluxExceptionHandlerAutoConfiguration {

    @Bean
    public WebFluxExceptionHandler webFluxExceptionHandler(MessageSource messageSource,
                                                           ObjectProvider<ExceptionMetrics> exceptionMetrics) {
        return new WebFluxExceptionHandler(messageSource, exceptionMetrics.getIfAvailable());
    }
}
