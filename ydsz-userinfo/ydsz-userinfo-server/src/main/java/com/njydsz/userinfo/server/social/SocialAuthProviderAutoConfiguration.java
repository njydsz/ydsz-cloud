package com.njydsz.userinfo.server.social;

import java.util.List;

import org.springframework.context.annotation.Configuration;

import com.njydsz.userinfo.domain.social.SocialAuthProvider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 社交认证 Provider 自动配置（P3-4）。
 *
 * <p>在应用启动时，将所有内置的 {@link SocialAuthProvider} Bean 自动注册到
 * {@link SocialAuthProviderRegistry}，确保运行时可通过 {@link SocialAuthProviderRegistry#getProvider(String)} 统一查找。
 *
 * <p>自定义 Provider 可在 Spring 容器初始化后通过 {@link SocialAuthProviderRegistry#register(SocialAuthProvider)} 动态注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SocialAuthProviderAutoConfiguration {

  private final List<SocialAuthProvider> socialAuthProviders;
  private final SocialAuthProviderRegistry registry;

  /**
   * 启动时将所有内置 Provider 注册到注册表。
   */
  @PostConstruct
  public void registerBuiltInProviders() {
    for (SocialAuthProvider provider : socialAuthProviders) {
      registry.register(provider);
    }
    log.info("社交认证 Provider 自动注册完成: 数量={}, 平台={}",
        registry.size(), registry.getRegisteredPlatforms());
  }
}
