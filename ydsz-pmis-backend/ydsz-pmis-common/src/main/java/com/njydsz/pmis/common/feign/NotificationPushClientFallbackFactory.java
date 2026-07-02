package com.njydsz.pmis.common.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * NotificationPushClient 降级工厂。
 *
 * <p>推送服务不可用时静默降级，不影响主业务流程（推送属于"增强体验"而非"业务必须"）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Component
public class NotificationPushClientFallbackFactory implements FallbackFactory<NotificationPushClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushClientFallbackFactory.class);

    @Override
    public NotificationPushClient create(Throwable cause) {
        log.warn("[Feign] 通知推送服务降级: {}", cause == null ? "null" : cause.getMessage());
        return new NotificationPushClient() {
            @Override
            public Map<String, Object> pushToUser(Long userId, String type, Object payload) {
                Map<String, Object> r = new HashMap<>();
                r.put("success", false);
                r.put("reason", "notification-service-unavailable");
                return r;
            }

            @Override
            public Map<String, Object> broadcast(String type, Object payload) {
                Map<String, Object> r = new HashMap<>();
                r.put("success", false);
                r.put("reason", "notification-service-unavailable");
                return r;
            }
        };
    }
}
