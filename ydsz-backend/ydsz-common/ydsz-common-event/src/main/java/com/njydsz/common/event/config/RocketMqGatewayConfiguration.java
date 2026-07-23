package com.njydsz.common.event.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.gateway.RocketMqEventPublishGateway;

/**
 * RocketMQ 事件投递网关自动配置
 *
 * <p>仅当 classpath 存在 {@code RocketMQTemplate} 且容器中有该 Bean 时装配。
 * 使用单独配置类隔离 RocketMQ 依赖，避免 NoClassDefFoundError。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnBean(type = "org.apache.rocketmq.spring.core.RocketMQTemplate")
public class RocketMqGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RocketMqGatewayConfiguration.class);

    /**
     * 注册 RocketMQ 事件投递网关
     *
     * @param rocketMQTemplate RocketMQ 模板（由 rocketmq-spring-boot-starter 自动注册）
     * @return RocketMQ 网关实例
     */
    @Bean
    @ConditionalOnMissingBean(EventPublishGateway.class)
    public EventPublishGateway rocketMqEventPublishGateway(
            RocketMQTemplate rocketMQTemplate) {
        log.info("RocketMqEventPublishGateway registered: topic=ydsz-outbox-events");
        return new RocketMqEventPublishGateway(rocketMQTemplate, null);
    }
}
