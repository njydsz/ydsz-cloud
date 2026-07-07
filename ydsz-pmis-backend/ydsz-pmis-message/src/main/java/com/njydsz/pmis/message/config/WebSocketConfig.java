package com.njydsz.pmis.message.config;

import com.njydsz.pmis.message.realtime.WebSocketAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 消息代理配置（STOMP 协议）。
 *
 * <p>客户端连接 {@code /ws} 后，订阅 {@code /topic/user/{userId}/notifications} 接收个人通知，
 * 订阅 {@code /topic/broadcast} 接收广播，订阅 {@code /topic/{topic}} 接收主题消息。
 * 心跳 10s/10s（服务端 / 客户端），由 STOMP 协议层自动保活。
 *
 * <p>P0-4 增强：注册 {@link WebSocketAuthHandshakeInterceptor}，握手时校验 JWT token，
 * 拒绝未认证连接；在线状态 / 离线消息补偿由 {@code OnlineUserService} /
 * {@code OfflineMessageService} / {@code WebSocketSessionListener} 协作完成。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** P0-4: 握手鉴权拦截器 */
    private final WebSocketAuthHandshakeInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 服务端推送目的地前缀，心跳 10s 间隔
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
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
