package com.njydsz.pmis.common.socket.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.njydsz.pmis.common.socket.auth.WebSocketAuthInterceptor;
import com.njydsz.pmis.common.socket.interceptor.StompMessageInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketConfigurer implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    @Autowired(required = false)
    private WebSocketAuthInterceptor authInterceptor;
    @Autowired(required = false)
    private StompMessageInterceptor stompMessageInterceptor;

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

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{properties.getHeartbeat().getServerInterval(), properties.getHeartbeat().getClientInterval()});
        registry.setApplicationDestinationPrefixes("/app");
}

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(properties.getMessageSizeLimit());
        registration.setSendTimeLimit((int) properties.getSendTimeoutMs());
}

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        if (stompMessageInterceptor != null) {
            registration.interceptors(stompMessageInterceptor);
}
}
}
