package com.njydsz.pmis.common.socket.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.pmis.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.pmis.common.socket.cluster.WebSocketClusterSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 集群广播自动装配。
 *
 * <p>当 classpath 存在 {@link StringRedisTemplate} 且 {@code pmis.websocket.cluster.enabled=true} 时自动生效。
 *
 * <p>自动注册：
 * <ul>
 *   <li>{@link WebSocketClusterPublisher} — Redis Pub/Sub 发布者</li>
 *   <li>{@link WebSocketClusterSubscriber} — Redis Pub/Sub 订阅者</li>
 *   <li>{@link RedisMessageListenerContainer} — Redis 监听容器</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, SimpMessagingTemplate.class})
@ConditionalOnProperty(prefix = "pmis.websocket.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketClusterAutoConfiguration {

    /**
     * 集群广播发布者。
     *
     * @param redisTemplate Redis 模板
     * @param properties    WebSocket 配置
     * @return 集群发布者
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public WebSocketClusterPublisher webSocketClusterPublisher(
            StringRedisTemplate redisTemplate,
            WebSocketProperties properties) {
        log.info("[WS-Cluster] 注册 WebSocketClusterPublisher, channel={}", properties.getCluster().getChannel());
        return new WebSocketClusterPublisher(redisTemplate, properties);
    }

    /**
     * 集群广播订阅者。
     *
     * @param messagingTemplate STOMP 消息模板
     * @return 集群订阅者
     */
    @Bean
    public WebSocketClusterSubscriber webSocketClusterSubscriber(SimpMessagingTemplate messagingTemplate) {
        log.info("[WS-Cluster] 注册 WebSocketClusterSubscriber");
        return new WebSocketClusterSubscriber(messagingTemplate);
    }

    /**
     * Redis 监听容器（绑定订阅者到 Channel）。
     *
     * @param connectionFactory Redis 连接工厂
     * @param subscriber        集群订阅者
     * @param properties        WebSocket 配置
     * @return Redis 监听容器
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer wsClusterListenerContainer(
            RedisConnectionFactory connectionFactory,
            WebSocketClusterSubscriber subscriber,
            WebSocketProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber,
                new ChannelTopic(properties.getCluster().getChannel()));
        log.info("[WS-Cluster] Redis 监听容器已注册, channel={}", properties.getCluster().getChannel());
        return container;
    }
}
