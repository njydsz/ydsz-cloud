package com.njydsz.pmis.common.exception.config;

import com.njydsz.pmis.common.exception.handler.JdbcExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

/**
 * JDBC 异常处理器自动配置
 *
 * <p>仅在 spring-jdbc 存在时注册 JdbcExceptionHandler。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JdbcExceptionHandler.class)
    public JdbcExceptionHandler jdbcExceptionHandler(MessageSource messageSource) {
        return new JdbcExceptionHandler(messageSource);
    }
}
