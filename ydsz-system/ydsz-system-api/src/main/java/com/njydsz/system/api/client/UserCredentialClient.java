package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.fallback.UserCredentialClientFallback;
import com.njydsz.system.domain.dto.VerifyPasswordRequest;

/**
 * 用户凭据校验 Feign 客户端（供系统管理模块调用用户中心服务）。
 *
 * <p>提供跨服务的密码校验能力，用于二次认证场景。调用用户中心的内部 API
 * {@code POST /internal/user/verify-password} 完成密码验证。
 *
 * <p><b>安全说明：</b>
 *
 * <ul>
 *   <li>密码仅 POST 传输（避免 URL/日志泄露）</li>
 *   <li>调用方需确保请求走 HTTPS 或内网</li>
 *   <li>Fallback 降级返回 false（密码校验服务不可用时拒绝认证）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@FeignClient(
    name = FeignClientConstants.USERINFO,
    contextId = "userCredentialClient",
    fallbackFactory = UserCredentialClientFallback.class)
public interface UserCredentialClient {

  /**
   * 校验用户明文密码是否正确。
   *
   * @param request 密码校验请求（含 userId 和明文密码）
   * @return true 表示密码校验通过；false 表示用户不存在或密码错误
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_VERIFY_PASSWORD)
  YdszResponse<Boolean> verifyPassword(@RequestBody VerifyPasswordRequest request);
}
