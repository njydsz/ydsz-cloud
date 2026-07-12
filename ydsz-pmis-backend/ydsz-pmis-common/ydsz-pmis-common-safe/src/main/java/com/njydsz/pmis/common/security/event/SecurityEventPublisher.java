package com.njydsz.pmis.common.security.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 安全事件发布器 —— 统一发布安全事件供审计和告警消费。
 * <p>
 * 对标 remi-comm SecurityEventPublisher，通过 Spring ApplicationEvent
 * 机制解耦安全事件的生产和消费。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class SecurityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public SecurityEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布安全事件。
     *
     * @param type      事件类型
     * @param userId    用户 ID
     * @param clientIp  客户端 IP
     * @param userAgent User-Agent
     * @param details   详细信息
     */
    public void publish(SecurityEvent.Type type, String userId, String clientIp,
                        String userAgent, Map<String, Object> details) {
        SecurityEvent event = new SecurityEvent(type, userId, clientIp, userAgent, details);
        log.info("Security event: type={}, userId={}, ip={}", type, userId, clientIp);
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布登录成功事件。
     */
    public void publishLoginSuccess(String userId, String clientIp, String userAgent) {
        publish(SecurityEvent.Type.LOGIN_SUCCESS, userId, clientIp, userAgent, Map.of());
    }

    /**
     * 发布登录失败事件。
     */
    public void publishLoginFailure(String userId, String clientIp, String userAgent, String reason) {
        publish(SecurityEvent.Type.LOGIN_FAILURE, userId, clientIp, userAgent, Map.of("reason", reason));
    }

    /**
     * 发布权限拒绝事件。
     */
    public void publishPermissionDenied(String userId, String clientIp, String userAgent, String resource) {
        publish(SecurityEvent.Type.PERMISSION_DENIED, userId, clientIp, userAgent,
                Map.of("resource", resource));
    }

    /**
     * 发布限流事件。
     */
    public void publishRateLimitExceeded(String userId, String clientIp, String userAgent, String endpoint) {
        publish(SecurityEvent.Type.RATE_LIMIT_EXCEEDED, userId, clientIp, userAgent,
                Map.of("endpoint", endpoint));
    }
}
