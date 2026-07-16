package com.njydsz.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.handler.JdbcExceptionHandler;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * JDBC 异常处理器自动配置
 *
 * <p>仅在 spring-jdbc 存在时注册 JdbcExceptionHandler。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JdbcExceptionHandler.class)
    public JdbcExceptionHandler jdbcExceptionHandler(MessageSource messageSource,
                                                       ObjectProvider<ExceptionMetrics> exceptionMetrics,
                                                       ObjectProvider<ExceptionProperties> properties,
                                                       ObjectProvider<ExceptionAlertPublisher> alertPublisher) {
        return new JdbcExceptionHandler(messageSource, exceptionMetrics.getIfAvailable(),
                properties.getIfAvailable(), alertPublisher.getIfAvailable());
    }
}
