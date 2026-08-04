package com.remisoft.common.socket.config;

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

import com.remisoft.common.socket.cluster.WebSocketClusterPublisher;
import com.remisoft.common.socket.cluster.WebSocketClusterSubscriber;
import com.remisoft.common.socket.compress.MessageCompressor;
import com.remisoft.common.socket.resilience.WebSocketCircuitBreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 集群广播自动装配。
 *
 * <p>当 classpath 存在 {@link StringRedisTemplate} 且 {@code remi.websocket.cluster.enabled=true} 时自动生效。
 *
 * <p>自动注册：
 * <ul>
 *   <li>{@link WebSocketClusterPublisher} — Redis Pub/Sub 发布者（含熔断保护 P0-2）</li>
 *   <li>{@link WebSocketClusterSubscriber} — Redis Pub/Sub 订阅者（含消息解压 P2-3）</li>
 *   <li>{@link RedisMessageListenerContainer} — Redis 监听容器</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, SimpMessagingTemplate.class})
@ConditionalOnProperty(prefix = "remi.websocket.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketClusterAutoConfiguration {

    /**
     * 创建集群广播发布者 Bean。
     *
     * @param redisTemplate Redis 模板
     * @param properties    WebSocket 配置属性
     * @param circuitBreaker 熔断器
     * @return 集群广播发布者实例
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public WebSocketClusterPublisher webSocketClusterPublisher(
            StringRedisTemplate redisTemplate,
            WebSocketProperties properties,
            WebSocketCircuitBreaker circuitBreaker) {
        log.info("[WS-Cluster] 注册 WebSocketClusterPublisher, channel={}", properties.getCluster().getChannel());
        return new WebSocketClusterPublisher(redisTemplate, properties, circuitBreaker);
    }

    /**
     * 创建集群广播订阅者 Bean。
     *
     * @param messagingTemplate STOMP 消息模板
     * @param messageCompressor 消息压缩器
     * @return 集群广播订阅者实例
     */
    @Bean
    public WebSocketClusterSubscriber webSocketClusterSubscriber(
            SimpMessagingTemplate messagingTemplate,
            MessageCompressor messageCompressor) {
        log.info("[WS-Cluster] 注册 WebSocketClusterSubscriber");
        return new WebSocketClusterSubscriber(messagingTemplate, messageCompressor);
    }

    /**
     * 创建 Redis 消息监听容器 Bean。
     *
     * @param connectionFactory Redis 连接工厂
     * @param subscriber        集群广播订阅者
     * @param properties        WebSocket 配置属性
     * @return Redis 消息监听容器实例
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
