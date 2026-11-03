package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.fallback.ApiPermissionRegistrationClientFallback;
import com.njydsz.system.domain.dto.ApiPermissionRegistrationRequest;

/**
 * API 权限注册 Feign 客户端。
 *
 * <p>system 扫描 {@code @AuthApiPermission} 后，将权限项注册到 userinfo 的 RBAC 权限表。
 *
 * @author ydsz-team
 * @since 26.10.30
 */
@FeignClient(
    name = FeignClientConstants.USERINFO,
    contextId = "apiPermissionRegistrationClient",
    fallbackFactory = ApiPermissionRegistrationClientFallback.class)
public interface ApiPermissionRegistrationClient {

  /**
   * 幂等注册 API 权限项。
   *
   * @param request API 权限注册请求
   * @return 权限项 ID
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_API_PERMISSION_REGISTER)
  YdszResponse<String> register(@RequestBody ApiPermissionRegistrationRequest request);
}
