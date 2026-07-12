package com.njydsz.pmis.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * 网关异常处理配置（P0-1）
 *
 * <p>注册 {@link GatewayExceptionHandler} 为全局异常处理器，
 * 替代 Spring Cloud Gateway 默认的 HTML 错误页。
 *
 * <p>通过 {@code @Order(-2)} 确保优先于默认的
 * {@code DefaultErrorWebExceptionHandler}（Order=0）。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@Configuration
public class GatewayErrorConfig {

    /**
     * 注册自定义网关异常处理器
     *
     * <p>使用 {@code ObjectProvider} 延迟获取 {@link WebProperties}，
     * 兼容不同 Spring Boot 版本的自动装配差异。
     *
     * @param errorAttributes    错误属性
     * @param serverProperties   服务器属性（含 error 配置）
     * @param webProperties      Web 属性
     * @param applicationContext Spring 上下文
     * @param configurer         编解码器配置
     * @return 网关异常处理器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ErrorWebExceptionHandler gatewayErrorHandler(
            ErrorAttributes errorAttributes,
            ServerProperties serverProperties,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer configurer) {
        GatewayExceptionHandler handler = new GatewayExceptionHandler(
                errorAttributes,
                webProperties.getResources(),
                serverProperties,
                applicationContext,
                configurer
        );
        return handler;
    }
}
