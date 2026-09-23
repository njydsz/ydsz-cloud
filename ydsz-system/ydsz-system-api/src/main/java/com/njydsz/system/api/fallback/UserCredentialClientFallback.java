package com.njydsz.system.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.client.UserCredentialClient;
import com.njydsz.system.domain.dto.VerifyPasswordRequest;

/**
 * {@link UserCredentialClient} 的 FallbackFactory。
 *
 * <p>用户中心服务不可用时降级返回 false（拒绝认证），宁可拒绝也不误放。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>密码校验失败 = 认证拒绝（false），安全防护优先</li>
 *   <li>记录 WARN 日志便于排查认证拒绝原因</li>
 *   <li>密码字段可能在日志中以掩码形式出现（日志系统脱敏），不做明文打印</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class UserCredentialClientFallback implements FallbackFactory<UserCredentialClient> {

  @Override
  public UserCredentialClient create(Throwable cause) {
    log.warn("[UserCredentialClient] 降级触发，拒绝二次认证: {}", cause.getMessage());
    return new UserCredentialClient() {
      @Override
      public YdszResponse<Boolean> verifyPassword(VerifyPasswordRequest request) {
        log.warn(
            "[UserCredentialClient] verifyPassword 降级: userId={}, reason=用户中心服务不可用",
            request == null ? null : request.getUserId());
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "用户中心服务不可用，无法完成二次认证");
      }
    };
  }
}
