package com.njydsz.pmis.notification.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Feign Fallback
 *
 * <p>用户服务不可用时返回 503 降级结果，避免通知发送流程被阻塞。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return id -> Result.failed(503, "用户服务暂不可用");
    }
}
