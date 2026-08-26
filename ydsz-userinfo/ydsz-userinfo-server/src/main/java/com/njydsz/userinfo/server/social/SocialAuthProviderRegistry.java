package com.njydsz.userinfo.server.social;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.social.SocialAuthProvider;

/**
 * 社交认证提供者注册表（P3-4：运行时动态注册预留）。
 *
 * <p>提供运行时注册/注销自定义社交认证 Provider 的能力，作为统一扩展点。
 * 内置 Provider（IM、企业微信）通过 Spring 自动注入，自定义 Provider
 * 可通过 {@link #register(SocialAuthProvider)} 动态注册（如 JustAuth 平台适配）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>不引入 JustAuth SDK，仅预留扩展接口</li>
 *   <li>线程安全：使用 {@link ConcurrentHashMap} 保证并发注册/查询安全</li>
 *   <li>平台标识唯一：重复注册时覆盖（后注册优先）并记录 WARN 日志</li>
 * </ul>
 *
 * <p><b>扩展使用示例：</b>
 *
 * <pre>{@code
 * // 自定义平台 Provider（实现 SocialAuthProvider 接口）
 * SocialAuthProvider customProvider = new CustomPlatformProvider(config);
 * registry.register(customProvider);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SocialAuthProviderRegistry {

  /** 平台标识 → Provider 实例映射 */
  private final Map<String, SocialAuthProvider> providers = new ConcurrentHashMap<>();

  /**
   * 注册社交认证 Provider。
   *
   * <p>如果该平台已存在 Provider，将被覆盖（后注册优先），并记录 WARN 日志。
   *
   * @param provider 社交认证 Provider 实例，不可为 null
   * @throws IllegalArgumentException provider 为 null 或 platform 为空时抛出
   */
  public void register(SocialAuthProvider provider) {
    if (provider == null) {
      throw new IllegalArgumentException("SocialAuthProvider must not be null");
    }
    String platform = provider.getPlatform();
    if (platform == null || platform.isBlank()) {
      throw new IllegalArgumentException("SocialAuthProvider platform must not be blank");
    }
    SocialAuthProvider previous = providers.put(platform.toUpperCase(), provider);
    if (previous != null) {
      log.warn("SocialAuthProvider 被覆盖注册: platform={}, 旧={}, 新={}",
          platform, previous.getClass().getSimpleName(), provider.getClass().getSimpleName());
    } else {
      log.info("SocialAuthProvider 注册成功: platform={}, class={}",
          platform, provider.getClass().getSimpleName());
    }
  }

  /**
   * 注销指定平台的 Provider。
   *
   * @param platform 平台标识
   * @return 被移除的 Provider，不存在时返回 null
   */
  public SocialAuthProvider unregister(String platform) {
    if (platform == null || platform.isBlank()) {
      return null;
    }
    SocialAuthProvider removed = providers.remove(platform.toUpperCase());
    if (removed != null) {
      log.info("SocialAuthProvider 注销: platform={}", platform);
    }
    return removed;
  }

  /**
   * 获取指定平台的 Provider。
   *
   * @param platform 平台标识
   * @return Provider 实例，不存在时返回 null
   */
  public SocialAuthProvider getProvider(String platform) {
    if (platform == null || platform.isBlank()) {
      return null;
    }
    return providers.get(platform.toUpperCase());
  }

  /**
   * 获取所有已注册的 Provider 列表。
   *
   * @return Provider 列表（不可修改快照）
   */
  public List<SocialAuthProvider> getAllProviders() {
    return List.copyOf(providers.values());
  }

  /**
   * 获取所有已注册的平台标识。
   *
   * @return 平台标识集合
   */
  public List<String> getRegisteredPlatforms() {
    return providers.keySet().stream().sorted().collect(Collectors.toList());
  }

  /**
   * 判断指定平台是否已注册 Provider。
   *
   * @param platform 平台标识
   * @return true 表示已注册
   */
  public boolean isRegistered(String platform) {
    if (platform == null || platform.isBlank()) {
      return false;
    }
    return providers.containsKey(platform.toUpperCase());
  }

  /**
   * 获取已注册的 Provider 数量。
   *
   * @return Provider 数量
   */
  public int size() {
    return providers.size();
  }
}
