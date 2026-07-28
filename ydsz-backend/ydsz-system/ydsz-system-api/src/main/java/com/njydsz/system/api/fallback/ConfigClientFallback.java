package com.njydsz.system.api.fallback;

import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.system.api.client.ConfigClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ConfigClient} 的 FallbackFactory。
 *
 * <p>系统管理服务不可用时降级返回 null，仅记录 WARN 日志，
 * 保证调用方主流程不受影响（配置查询是辅助功能，不应阻断业务）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConfigClientFallback implements FallbackFactory<ConfigClient> {

    @Override
    public ConfigClient create(Throwable cause) {
        log.warn("[ConfigClient] 降级触发: {}", cause.getMessage());
        return new ConfigClient() {
            @Override
            public BaseResponse<String> getConfig(Map<String, String> request) {
                log.warn("[ConfigClient] getConfig 降级: key={}, reason=系统管理服务不可用",
                        request == null ? null : request.get("key"));
                return BaseResponse.success(null);
            }
        };
    }
}
