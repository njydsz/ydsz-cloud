package com.njydsz.message.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 消息服务自动配置。
 *
 * <p>封装 ydsz-message 服务的 Bean 注册：通知桥接、消息去重、聚合、批处理、灰度、撤回、订阅、
 *
 * <p>回执、指标、渠道桥接、用户渠道绑定、退订等。
 *
 * <p>通过 {@code ydsz.message.*} 控制各功能模块的开关。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Configuration
public class MessageServiceAutoConfiguration {

    /**
     * 注册 SpEL 表达式解析器，供 {@code RouteRuleServiceImpl} 求值路由条件使用。
     *
     * @return SpEL 表达式解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }
}
