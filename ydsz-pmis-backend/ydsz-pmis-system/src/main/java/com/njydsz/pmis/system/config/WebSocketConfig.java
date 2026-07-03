package com.njydsz.pmis.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 消息代理配置。
 * <p>
 * 使用 STOMP 协议实现实时推送，客户端连接 /ws 后订阅 /user/queue/notifications 接收通知。
 * </p>
 * <p>
 * P0-1: 去掉 SockJS fallback，改为纯 STOMP over WebSocket，与前端 @stomp/stompjs 原生客户端直连。
 * 心跳间隔 10s/10s（服务端/客户端），由 STOMP 协议层自动保活。
 * </p>
 *
 * @author pmis
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 服务端推送目的地前缀，心跳 10s 间隔（对标钉钉/飞书实时推送）
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000});
        // 客户端发送目的地前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户私有频道前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
