package com.njydsz.system.api.fallback;

import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.client.AppInfoClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link AppInfoClient} 的 FallbackFactory。
 *
 * <p>系统管理服务不可用时降级返回统一错误码
 * ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})，
 * 仅记录 WARN 日志，保证调用方走"服务不可用"分支而非异常中断。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AppInfoClientFallback implements FallbackFactory<AppInfoClient> {

    @Override
    public AppInfoClient create(Throwable cause) {
        log.warn("[AppInfoClient] 降级触发: {}", cause.getMessage());
        return new AppInfoClient() {
            @Override
            public BaseResponse<Boolean> validateClient(Map<String, String> request) {
                log.warn("[AppInfoClient] validateClient 降级: appKey={}, reason=系统管理服务不可用",
                        request == null ? null : request.get("appKey"));
                return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "系统管理服务不可用");
            }
        };
    }
}
