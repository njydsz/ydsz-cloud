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
 * JDBC 异常处理器配置。
 *
 * <p>捕获 JDBC 层的 {@link org.springframework.dao.DataAccessException}，转换为标准 {@code Result} 错误响应。
 *
 * <p>识别唯一索引冲突、外键约束、连接超时、死锁等典型数据库异常，给出语义化错误信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandlerAutoConfiguration {

    /**
     * 创建 JDBC 异常处理器 Bean
     *
     * @param messageSource   国际化消息源
     * @param exceptionMetrics 异常指标统计器（可选）
     * @param properties      异常模块配置属性（可选）
     * @param alertPublisher  异常告警发布器（可选）
     * @return JDBC 异常处理器实例
     */
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
