package com.njydsz.pmis.common.exception.handler;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;

/**
 * Validation 异常处理器自动配置
 *
 * <p>仅在 Web 应用且类路径存在 {@link jakarta.validation} 时装配。
 * 通过 {@code ydsz.exception.global-handler-enabled=true}（默认启用）控制是否启用。</p>
 *
 * <p><b>设计说明：</b>将 {@code @AutoConfiguration} 与 {@code @RestControllerAdvice} 解耦，
 * 与 {@link MvcExceptionHandlerAutoConfiguration} 配合使用。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see ValidationExceptionHandler
 */
@AutoConfiguration(after = MvcExceptionHandlerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ConstraintViolationException.class, ValidationExceptionHandler.class})
@ConditionalOnProperty(prefix = "ydsz.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class ValidationExceptionHandlerAutoConfiguration {

    @Bean
    public ValidationExceptionHandler validationExceptionHandler(MessageSource messageSource,
                                                                   ObjectProvider<ExceptionMetrics> exceptionMetrics) {
        return new ValidationExceptionHandler(messageSource, exceptionMetrics.getIfAvailable());
    }
}
