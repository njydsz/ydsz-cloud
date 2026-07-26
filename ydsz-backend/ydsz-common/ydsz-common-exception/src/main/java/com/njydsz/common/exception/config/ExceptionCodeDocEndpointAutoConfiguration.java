package com.njydsz.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.endpoint.ExceptionCodeDocEndpoint;

/**
 * 异常错误码文档端点自动配置
 *
 * <p>仅在 Spring Boot Actuator 存在时注册 {@link ExceptionCodeDocEndpoint}，
 * 通过 {@code /actuator/exception-codes} 暴露所有已注册的异常错误码。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCodeDocEndpoint
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnProperty(prefix = "ydsz.exception", name = "doc-endpoint-enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionCodeDocEndpointAutoConfiguration {

    /**
     * 创建错误码文档端点 Bean
     *
     * @param messageSource 国际化消息源
     * @return 错误码文档端点实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionCodeDocEndpoint.class)
    public ExceptionCodeDocEndpoint exceptionCodeDocEndpoint(MessageSource messageSource) {
        return new ExceptionCodeDocEndpoint(messageSource);
    }
}
