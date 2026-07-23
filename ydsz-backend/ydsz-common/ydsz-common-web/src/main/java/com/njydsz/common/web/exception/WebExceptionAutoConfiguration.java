package com.njydsz.common.web.exception;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * Web 端异常处理器自动配置
 *
 * <p>仅在 Servlet Web 应用且类路径存在 {@link WebExceptionHandler} 时装配。
 * 通过 {@code @Bean} 方法创建 {@link WebExceptionHandler} 实例，
 * 注入 {@link ExceptionMetrics}、{@link ExceptionProperties}、{@link ExceptionAlertPublisher} 等可选依赖。
 *
 * <p><b>设计说明：</b>将 {@code @AutoConfiguration} 与 {@code @RestControllerAdvice} 解耦，
 * 避免在 Advice 类上叠加 Spring Boot 自动配置语义，提升可测试性与可读性。
 *
 * @author ydsz-team
 * @see WebExceptionHandler
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({MessageSource.class, WebExceptionHandler.class})
@ConditionalOnProperty(prefix = "ydsz.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebExceptionHandler.class)
    public WebExceptionHandler webExceptionHandler(MessageSource messageSource,
                                                    ObjectProvider<ExceptionMetrics> exceptionMetrics,
                                                    ObjectProvider<ExceptionProperties> properties,
                                                    ObjectProvider<ExceptionAlertPublisher> alertPublisher) {
        return new WebExceptionHandler(messageSource,
                exceptionMetrics.getIfAvailable(),
                properties.getIfAvailable(),
                alertPublisher.getIfAvailable());
    }
}
