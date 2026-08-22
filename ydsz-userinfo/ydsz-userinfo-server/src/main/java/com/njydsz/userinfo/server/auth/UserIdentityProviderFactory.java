package com.njydsz.userinfo.server.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.auth.UserIdentityProvider;
import com.njydsz.userinfo.domain.enums.IdentityProviderType;

/**
 * 身份提供者工厂（P2-2 多账号认证体系路由）。
 *
 * <p>根据用户认证源类型路由到对应的 {@link UserIdentityProvider} 实现。
 *
 * <p>使用策略模式：所有 Provider 实现作为 Spring Bean 注入，工厂根据
 * {@link IdentityProviderType} 查找匹配的实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class UserIdentityProviderFactory {

  private final Map<IdentityProviderType, UserIdentityProvider> providerMap;

  /**
   * 构造工厂实例。
   *
   * @param providers 所有 UserIdentityProvider 实现的 Spring Bean 列表
   */
  public UserIdentityProviderFactory(List<UserIdentityProvider> providers) {
    providerMap = new HashMap<>();
    for (UserIdentityProvider provider : providers) {
      providerMap.put(provider.getType(), provider);
    }
  }

  /**
   * 获取指定类型的身份提供者。
   *
   * @param type 认证源类型
   * @return 对应的 Provider 实现；未找到返回 null
   */
  public UserIdentityProvider getProvider(IdentityProviderType type) {
    return providerMap.get(type);
  }

  /**
   * 根据用户记录的认证源标识获取 Provider。
   *
   * @param userIdentityProvider 用户记录中的认证源标识字符串
   * @return 对应的 Provider 实现；未找到返回 LOCAL Provider
   */
  public UserIdentityProvider getProviderByUser(String userIdentityProvider) {
    IdentityProviderType type = IdentityProviderType.fromCode(userIdentityProvider);
    UserIdentityProvider provider = providerMap.get(type);
    return provider != null ? provider : providerMap.get(IdentityProviderType.LOCAL);
  }

  /**
   * 判断是否支持指定的认证源类型。
   *
   * @param type 认证源类型
   * @return true 表示有对应的 Provider 实现
   */
  public boolean supports(IdentityProviderType type) {
    return providerMap.containsKey(type);
  }
}
