package com.njydsz.pmis.message.server.config;

import com.njydsz.pmis.common.websocket.auth.WebSocketAuthInterceptor;
import com.njydsz.pmis.common.websocket.config.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 消息代理配置（STOMP 协议）。
 *
 * <p>P1.3.0 重构：鉴权拦截器、在线状态、离线补偿、集群广播等通用能力
 * 已由 {@code ydsz-pmis-common-websocket} 自动装配提供，本类仅保留
 * STOMP 端点和 Broker 前缀配置（因为 {@code @EnableWebSocketMessageBroker}
 * 必须在业务 {@code @Configuration} 类上显式声明）。
 *
 * <p>配置参数从 {@link WebSocketProperties} 读取，支持 YAML 动态配置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{
                        properties.getHeartbeat().getServerInterval(),
                        properties.getHeartbeat().getClientInterval()});
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var registration = registry.addEndpoint(properties.getEndpoint())
                .setAllowedOriginPatterns(
                        properties.getAllowedOriginPatterns().toArray(new String[0]));
        if (properties.isSockJsEnabled()) {
            registration.withSockJS();
        }
    }
}
