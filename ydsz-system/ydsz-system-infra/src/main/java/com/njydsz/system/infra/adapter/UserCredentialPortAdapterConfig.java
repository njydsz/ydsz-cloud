package com.njydsz.system.infra.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.system.domain.port.UserCredentialPort;

/**
 * 用户凭据校验端口适配器配置（DDD 端口-适配器模式）。
 *
 * <p>infra 层提供 {@link UserCredentialPort} 的具体实现。
 *
 * <p>当前实现：
 *
 * <ul>
 *   <li>远程模式（生产环境）：通过 Feign Client 调用用户中心服务，需要 LoadBalancer 装配</li>
 *   <li>降级模式（本地开发）：返回 false（安全降级：无可用服务时拒绝认证）</li>
 * </ul>
 *
 * <p>infra 层允许依赖 api 层（Feign 契约），server 层仅依赖 domain 端口，解除层级耦合。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Configuration
public class UserCredentialPortAdapterConfig {

  /**
   * 创建端口适配器。当前实现为降级模式（安全拒绝），待 LoadBalancer 完整配置后可切换为 Feign 适配器。
   *
   * @return 端口适配器实例
   */
  @Bean
  public UserCredentialPort userCredentialPort() {
    log.info("[ydsz-system] UserCredentialPort 使用降级适配器（本地开发模式）");
    return new NoopUserCredentialAdapter();
  }

  /**
   * 空实现适配器：返回 false（安全降级：拒绝认证）。
   */
  @Slf4j
  static class NoopUserCredentialAdapter implements UserCredentialPort {

    @Override
    public boolean verifyPassword(String userId, String password) {
      log.warn("UserCredentialPort 处于降级模式，无法校验密码，拒绝认证: userId={}", userId);
      return false;
    }
  }
}
