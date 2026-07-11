package com.njydsz.pmis.message.api.fallback;
import com.njydsz.pmis.message.api.client.NotificationClient;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.api.dto.NotificationFeignDTO;
import com.njydsz.pmis.message.api.dto.RealtimePushDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * NotificationClient 降级工厂
 *
 * <p>notification 模块不可用时静默降级（返回 0，不影响工作流主流程）。
 * 工作流通知是"增强体验"而非"业务必须"，降级更安全。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class NotificationClientFallback implements FallbackFactory<NotificationClient> {

    @Override
    public NotificationClient create(Throwable cause) {
        return new NotificationClient() {
            @Override
            public Result<Integer> send(NotificationFeignDTO payload) {
                log.warn("[Feign] NotificationClient 降级 send: title={}",
                        payload == null ? "null" : payload.getTitle());
                return Result.ok(0);
            }

            @Override
            public Result<Map<String, Object>> pushRealtime(String userId, String type, RealtimePushDTO payload) {
                log.warn("[Feign] NotificationClient 降级 pushRealtime: userId={} type={} cause={}",
                        userId, type, cause == null ? "null" : cause.getMessage());
                return Result.ok(Collections.emptyMap());
            }

            @Override
            public Result<Map<String, Object>> broadcast(String type, RealtimePushDTO payload) {
                log.warn("[Feign] NotificationClient fallback broadcast: type={} cause={}",
                        type, cause == null ? "null" : cause.getMessage());
                return Result.ok(Collections.emptyMap());
            }
        };
    }
}
