package com.njydsz.system.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.client.ConfigClient;
import com.njydsz.system.api.dto.ConfigGetRequest;

/**
 * {@link ConfigClient} 的 FallbackFactory。
 *
 * <p>系统管理服务不可用时降级返回统一错误码 ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})， 仅记录 WARN
 * 日志，保证调用方主流程不受影响。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class ConfigClientFallback implements FallbackFactory<ConfigClient> {

  @Override
  public ConfigClient create(Throwable cause) {
    log.warn("[ConfigClient] 降级触发: {}", cause.getMessage());
    return new ConfigClient() {
      @Override
      public YdszResponse<String> getConfig(ConfigGetRequest request) {
        log.warn(
            "[ConfigClient] getConfig 降级: key={}, reason=系统管理服务不可用",
            request == null ? null : request.getKey());
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "系统管理服务不可用");
      }
    };
  }
}
