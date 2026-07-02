package com.njydsz.pmis.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketConfig 配置单元测试。
 *
 * <p>验证消息代理前缀与 STOMP 端点注册符合 P0-2 实时推送设计。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("WebSocketConfig 配置测试")
class WebSocketConfigTest {

    @Test
    @DisplayName("configureMessageBroker 应启用 /topic /queue broker 并设置前缀")
    void configureMessageBroker_shouldConfigureBroker() {
        WebSocketConfig config = new WebSocketConfig();
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    @DisplayName("registerStompEndpoints 应注册 /ws 端点并启用 SockJS")
    void registerStompEndpoints_shouldRegisterWsEndpoint() {
        WebSocketConfig config = new WebSocketConfig();
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
    }
}
