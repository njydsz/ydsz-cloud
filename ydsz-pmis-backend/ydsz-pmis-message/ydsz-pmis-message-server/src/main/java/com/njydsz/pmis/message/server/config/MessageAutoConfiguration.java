package com.njydsz.pmis.message.server.config;

import com.njydsz.pmis.message.server.channel.ChannelRouter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息模块自动装配。
 *
 * <p>对关键 Bean（MessageProperties / ChannelProperties / ChannelRouter）
 * 提供 {@code @ConditionalOnMissingBean} 兜底保护：当组件扫描未覆盖时由此处注册。
 *
 * <p>P1.3.0 重构：RealtimePushService 已改为委托 common-websocket 的
 * RealtimePushTemplate，不再需要在此手动注册；WebSocketConfig / WebSocketClusterConfig
 * 已由 common-websocket 自动装配接管。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MessageAutoConfiguration {

    /**
     * 兜底注册消息全局配置。
     *
     * @return MessageProperties
     */
    @Bean
    @ConditionalOnMissingBean(MessageProperties.class)
    public MessageProperties messageProperties() {
        return new MessageProperties();
    }

    /**
     * 兜底注册通道相关配置。
     *
     * @return ChannelProperties
     */
    @Bean
    @ConditionalOnMissingBean(ChannelProperties.class)
    public ChannelProperties channelProperties() {
        return new ChannelProperties();
    }

    /**
     * 兜底注册通道路由器。
     *
     * @param applicationContext Spring 上下文
     * @param messageProperties  消息配置
     * @return ChannelRouter
     */
    @Bean
    @ConditionalOnMissingBean(ChannelRouter.class)
    public ChannelRouter channelRouter(ApplicationContext applicationContext,
                                       MessageProperties messageProperties) {
        ChannelRouter router = new ChannelRouter(applicationContext, messageProperties);
        router.initChannels();
        return router;
    }
}
