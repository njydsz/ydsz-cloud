package com.remisoft.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.remisoft.common.exception.handler.JdbcExceptionHandler;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

/**
 * JDBC 异常处理器配置。
 *
 * <p>捕获 JDBC 层的 {@link org.springframework.dao.DataAccessException}，转换为标准错误响应。
 *
 * <p>识别唯一索引冲突、外键约束、连接超时、死锁等典型数据库异常，给出语义化错误信息。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandlerAutoConfiguration {

    /**
     * 创建 JDBC 异常处理器 Bean
     *
     * @param environment       Spring 环境对象
     * @param exceptionMetrics  异常指标统计器（可选）
     * @param properties       异常模块配置属性（可选）
     * @return JDBC 异常处理器实例
     */
    @Bean
    @ConditionalOnMissingBean(JdbcExceptionHandler.class)
    public JdbcExceptionHandler jdbcExceptionHandler(Environment environment,
                                                       ObjectProvider<ExceptionMetrics> exceptionMetrics,
                                                       ObjectProvider<ExceptionProperties> properties) {
        return new JdbcExceptionHandler(environment, exceptionMetrics.getIfAvailable(),
                properties.getIfAvailable());
    }
}
