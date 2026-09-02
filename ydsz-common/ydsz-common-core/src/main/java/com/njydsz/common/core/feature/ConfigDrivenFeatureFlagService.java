package com.njydsz.common.core.feature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于配置的特性开关服务实现（零第三方依赖）。
 *
 * <p>开关状态来源于 {@link com.njydsz.common.core.config.CoreProperties} 的
 * {@code featureFlags} 映射（配置前缀 {@code ydsz.core.feature-flags}）。 支持运行期通过 Spring
 * 配置刷新（{@code RefreshScope} / Nacos 动态刷新）更新映射后重新生效。
 *
 * <p>线程安全：内部使用 {@link ConcurrentHashMap}，映射替换采用原子引用切换， 并发读无锁。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FeatureFlagService
 */
public class ConfigDrivenFeatureFlagService implements FeatureFlagService {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigDrivenFeatureFlagService.class);

  /** 当前生效的开关映射快照（可被原子替换） */
  private volatile Map<String, Boolean> flags = new ConcurrentHashMap<>();

  /**
   * 构造器。
   *
   * @param initialFlags 初始开关映射（可为 null，表示全部默认开启）
   */
  public ConfigDrivenFeatureFlagService(Map<String, Boolean> initialFlags) {
    if (initialFlags != null && !initialFlags.isEmpty()) {
      this.flags = new ConcurrentHashMap<>(initialFlags);
    }
  }

  /**
   * 运行期替换开关映射快照（供配置刷新回调调用）。
   *
   * @param newFlags 新的开关映射（可为 null，表示全部默认开启）
   */
  public void refresh(Map<String, Boolean> newFlags) {
    Map<String, Boolean> next =
        newFlags == null || newFlags.isEmpty()
            ? new ConcurrentHashMap<>()
            : new ConcurrentHashMap<>(newFlags);
    this.flags = next;
    LOG.info("Feature flags refreshed, {} active flags", next.size());
  }

  @Override
  public boolean isEnabled(String name) {
    return isEnabled(name, true);
  }

  @Override
  public boolean isEnabled(String name, boolean defaultValue) {
    if (name == null || name.isBlank()) {
      return defaultValue;
    }
    Boolean value = flags.get(name);
    return value != null ? value : defaultValue;
  }
}
