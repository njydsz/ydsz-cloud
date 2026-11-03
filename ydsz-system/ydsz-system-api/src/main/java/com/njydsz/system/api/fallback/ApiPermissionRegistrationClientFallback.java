package com.njydsz.system.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.client.ApiPermissionRegistrationClient;

/**
 * {@link ApiPermissionRegistrationClient} 降级工厂。
 *
 * <p>userinfo 不可用时返回空权限项 ID，由调用方记录告警且不阻断 system 启动。
 *
 * @author ydsz-team
 * @since 26.10.30
 */
@Slf4j
@Component
public class ApiPermissionRegistrationClientFallback
    implements FallbackFactory<ApiPermissionRegistrationClient> {

  @Override
  public ApiPermissionRegistrationClient create(Throwable cause) {
    return request -> {
      log.warn(
          "[ApiPermissionRegistrationClient] register 降级: apiCode={}, reason={}",
          request == null ? null : request.getApiCode(),
          cause == null ? "?" : cause.getMessage());
      return YdszResponse.error(
          FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE,
          "API permission registration is temporarily unavailable");
    };
  }
}
