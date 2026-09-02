package com.njydsz.common.core.feature;

/**
 * 特性开关静态门面。
 *
 * <p>供非 Spring 注入场景（静态工具、切面、非 Bean 上下文）便捷访问特性开关。 通过
 * {@link #setService(FeatureFlagService)} 在应用启动时注入实际服务实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FeatureFlagService
 */
public final class FeatureFlagContext {

  private FeatureFlagContext() {}

  /** 当前生效的特性开关服务（默认空实现：全部开启） */
  private static volatile FeatureFlagService service = new AlwaysEnabledService();

  /**
   * 注入特性开关服务（应用启动时由自动配置调用）。
   *
   * @param featureFlagService 特性开关服务
   */
  public static void setService(FeatureFlagService featureFlagService) {
    if (featureFlagService != null) {
      service = featureFlagService;
    }
  }

  /**
   * 查询特性开关是否开启（未配置时默认开启）。
   *
   * @param name 开关名称
   * @return 开启返回 true
   */
  public static boolean isEnabled(String name) {
    return service.isEnabled(name);
  }

  /**
   * 查询特性开关是否开启，并指定未配置时的默认值。
   *
   * @param name 开关名称
   * @param defaultValue 未配置时的默认值
   * @return 开启返回 true
   */
  public static boolean isEnabled(String name, boolean defaultValue) {
    return service.isEnabled(name, defaultValue);
  }

  /** 默认空实现：所有开关默认开启，保证未配置时业务零影响 */
  private static final class AlwaysEnabledService implements FeatureFlagService {

    @Override
    public boolean isEnabled(String name) {
      return true;
    }

    @Override
    public boolean isEnabled(String name, boolean defaultValue) {
      return defaultValue;
    }
  }
}
