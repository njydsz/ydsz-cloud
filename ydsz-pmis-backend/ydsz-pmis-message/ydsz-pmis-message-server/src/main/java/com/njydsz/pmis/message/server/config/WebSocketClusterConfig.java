package com.njydsz.pmis.message.server.config;

import com.njydsz.pmis.message.server.realtime.WebSocketClusterPublisher;
import com.njydsz.pmis.message.server.realtime.WebSocketClusterSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * WebSocket 集群推�?Redis 监听容器配置�? *
 * <p>注册 {@link RedisMessageListenerContainer}，将 {@link WebSocketClusterSubscriber}
 * 绑定�?Redis Channel {@code pmis:ws:cluster:push}，实现多节点推送消息的跨实例广播�? *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketClusterConfig {

    @Bean
    public RedisMessageListenerContainer wsClusterListenerContainer(
            RedisConnectionFactory connectionFactory,
            WebSocketClusterSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber,
                new ChannelTopic(WebSocketClusterPublisher.CHANNEL));
        log.info("[WS-Cluster] Redis 监听容器已注�? channel={}", WebSocketClusterPublisher.CHANNEL);
        return container;
    }
}
