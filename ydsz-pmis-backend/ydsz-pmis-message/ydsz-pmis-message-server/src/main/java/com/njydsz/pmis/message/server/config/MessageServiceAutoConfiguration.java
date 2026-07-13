package com.njydsz.pmis.message.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 消息服务自动配置�? *
 * <p>注册路由规则求值所需�?SpEL {@link ExpressionParser} Bean�? * 独立于通道 agent �?{@code MessageAutoConfiguration}，避免修改已存在配置类�? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MessageServiceAutoConfiguration {

    /**
     * 注册 SpEL 表达式解析器，供 {@code RouteRuleServiceImpl} 求值路由条件使用�?     *
     * @return SpEL 表达式解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }
}
