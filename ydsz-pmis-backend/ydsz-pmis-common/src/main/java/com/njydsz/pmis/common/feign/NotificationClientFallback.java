package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

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
        log.warn("[Feign] NotificationClient 降级: cause={}", cause == null ? "null" : cause.getMessage());
        return payload -> {
            log.warn("[Feign] NotificationClient 降级返回 0: title={}",
                    payload == null ? "null" : payload.get("title"));
            return Result.ok(0);
        };
    }
}
