package com.njydsz.pmis.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 消息代理配置。
 * <p>
 * 使用 STOMP 协议实现实时推送，客户端连接 /ws 后订阅 /topic/notifications/{userId} 接收通知。
 * </p>
 *
 * @author pmis
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 服务端推送目的地前缀
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送目的地前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户私有频道前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
