package com.njydsz.common.socket.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.njydsz.common.socket.auth.WebSocketAuthInterceptor;
import com.njydsz.common.socket.interceptor.StompMessageInterceptor;
import com.njydsz.common.socket.session.LocalSessionRegistry;
import com.njydsz.common.socket.session.SessionWebSocketHandlerDecoratorFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket STOMP 配置类。
 *
 * <p>注册 STOMP 端点、配置消息代理（SimpleBroker /app 前缀）、
 * 设置传输参数（消息大小限制、发送超时）以及客户端入站通道拦截器。
 *
 * <p>认证拦截器（{@link WebSocketAuthInterceptor}）和消息拦截器（{@link StompMessageInterceptor}）
 * 为可选依赖，未配置时降级跳过。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketConfigurer implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final LocalSessionRegistry sessionRegistry;
    @Autowired(required = false)
    private WebSocketAuthInterceptor authInterceptor;
    @Autowired(required = false)
    private StompMessageInterceptor stompMessageInterceptor;

    /**
     * 注册 STOMP 端点。
     *
     * <p>配置 WebSocket 端点路径、SockJS 支持、CORS 跨域策略以及认证拦截器。
     *
     * @param registry STOMP 端点注册表
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var registration = registry.addEndpoint(properties.getEndpoint());
        if (properties.isSockJsEnabled()) {
            registration.withSockJS();
}
        List<String> origins = properties.getAllowedOriginPatterns();
        if (origins != null && !origins.isEmpty()) {
            registration.setAllowedOriginPatterns(origins.toArray(new String[0]));
}
        if (authInterceptor != null) {
            registration.addInterceptors(authInterceptor);
        }
    }

    /**
     * 配置消息代理。
     *
     * <p>启用 SimpleBroker，设置心跳间隔和应用目标前缀。
     *
     * @param registry 消息代理注册表
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{properties.getHeartbeat().getServerInterval(), properties.getHeartbeat().getClientInterval()});
        registry.setApplicationDestinationPrefixes("/app");
}

    /**
     * 配置 WebSocket 传输参数。
     *
     * <p>设置消息大小限制、发送超时时间以及 Session 追踪装饰器工厂。
     *
     * @param registration WebSocket 传输注册表
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(properties.getMessageSizeLimit());
        registration.setSendTimeLimit((int) properties.getSendTimeoutMs());
        registration.addDecoratorFactory(new SessionWebSocketHandlerDecoratorFactory(sessionRegistry));
    }

    /**
     * 配置客户端入站通道拦截器。
     *
     * <p>注册 STOMP 消息拦截器，用于速率限制和审计日志。
     *
     * @param registration 通道注册表
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        if (stompMessageInterceptor != null) {
            registration.interceptors(stompMessageInterceptor);
}
}
}
