package com.remisoft.common.exception.handler;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.remisoft.common.exception.config.ExceptionProperties;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

/**
 * Validation 异常处理器自动配置
 *
 * <p>仅在 Web 应用且类路径存在 {@link jakarta.validation} 时装配。
 * 通过 {@code remi.exception.global-handler-enabled=true}（默认启用）控制是否启用。</p>
 *
 * <p><b>设计说明：</b>将 {@code @AutoConfiguration} 与 {@code @RestControllerAdvice} 解耦，
 * 与 {@link MvcExceptionHandlerAutoConfiguration} 配合使用。</p>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ValidationExceptionHandler
 */
@AutoConfiguration(after = MvcExceptionHandlerAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ConstraintViolationException.class, ValidationExceptionHandler.class})
@ConditionalOnProperty(prefix = "remi.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class ValidationExceptionHandlerAutoConfiguration {

    /**
     * 创建校验异常处理器 Bean
     *
     * @param environment       Spring 环境对象
     * @param messageSource    国际化消息源
     * @param exceptionMetrics 异常指标统计器（可选）
     * @param properties       异常模块配置属性
     * @return 校验异常处理器实例
     */
    @Bean
    public ValidationExceptionHandler validationExceptionHandler(Environment environment,
                                                                   MessageSource messageSource,
                                                                   ObjectProvider<ExceptionMetrics> exceptionMetrics,
                                                                   ExceptionProperties properties) {
        return new ValidationExceptionHandler(environment, messageSource,
                exceptionMetrics.getIfAvailable(), properties);
    }
}
