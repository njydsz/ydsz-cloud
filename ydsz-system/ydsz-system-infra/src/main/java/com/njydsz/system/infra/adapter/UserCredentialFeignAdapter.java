package com.njydsz.system.infra.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.api.client.UserCredentialClient;
import com.njydsz.system.domain.dto.VerifyPasswordRequest;
import com.njydsz.system.domain.port.UserCredentialPort;

/**
 * 用户凭据校验 Feign 适配器（DDD 端口-适配器模式）。
 *
 * <p>实现 domain 层的 {@link UserCredentialPort} 端口，通过 api 层的 {@link UserCredentialClient} Feign 接口调用用户中心服务。
 * infra 层允许依赖 api 层（Feign 契约），server 层仅依赖 domain 端口，解除层级耦合。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCredentialFeignAdapter implements UserCredentialPort {

  private final UserCredentialClient userCredentialClient;

  @Override
  public boolean verifyPassword(String userId, String password) {
    VerifyPasswordRequest request = new VerifyPasswordRequest();
    request.setUserId(userId);
    request.setPassword(password);
    YdszResponse<Boolean> response = userCredentialClient.verifyPassword(request);
    return response != null && response.getData() != null && Boolean.TRUE.equals(response.getData());
  }
}
