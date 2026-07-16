package com.njydsz.common.socket.config;

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

import com.njydsz.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.common.socket.cluster.WebSocketClusterSubscriber;
import com.njydsz.common.socket.compress.MessageCompressor;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 集群广播自动装配。
 *
 * <p>当 classpath 存在 {@link StringRedisTemplate} 且 {@code ydsz.websocket.cluster.enabled=true} 时自动生效。
 *
 * <p>自动注册：
 * <ul>
 *   <li>{@link WebSocketClusterPublisher} — Redis Pub/Sub 发布者（含熔断保护 P0-2）</li>
 *   <li>{@link WebSocketClusterSubscriber} — Redis Pub/Sub 订阅者（含消息解压 P2-3）</li>
 *   <li>{@link RedisMessageListenerContainer} — Redis 监听容器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, SimpMessagingTemplate.class})
@ConditionalOnProperty(prefix = "ydsz.websocket.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketClusterAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public WebSocketClusterPublisher webSocketClusterPublisher(
            StringRedisTemplate redisTemplate,
            WebSocketProperties properties,
            WebSocketCircuitBreaker circuitBreaker) {
        log.info("[WS-Cluster] 注册 WebSocketClusterPublisher, channel={}", properties.getCluster().getChannel());
        return new WebSocketClusterPublisher(redisTemplate, properties, circuitBreaker);
    }

    @Bean
    public WebSocketClusterSubscriber webSocketClusterSubscriber(
            SimpMessagingTemplate messagingTemplate,
            MessageCompressor messageCompressor) {
        log.info("[WS-Cluster] 注册 WebSocketClusterSubscriber");
        return new WebSocketClusterSubscriber(messagingTemplate, messageCompressor);
    }

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
