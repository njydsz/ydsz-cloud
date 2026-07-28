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
 * RocketMQ 网关配置。
 *
 * <p>封装 RocketMQ 生产者/消费者 Bean 注册逻辑，支持事务消息、顺序消息、延迟消息。
 *
 * <p>通过 {@code ydsz.event.rocketmq.*} 配置 NameServer 地址、Topic、生产组、消费组等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnBean(type = "org.apache.rocketmq.spring.core.RocketMQTemplate")
public class RocketMqGatewayConfiguration {

    /** 日志实例 */
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
