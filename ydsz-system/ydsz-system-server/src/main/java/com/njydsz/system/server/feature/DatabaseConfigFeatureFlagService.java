package com.njydsz.system.server.feature;

import java.util.Map;

import lombok.RequiredArgsConstructor;

import com.njydsz.common.core.feature.FeatureFlagService;
import com.njydsz.system.server.service.ConfigService;

/**
 * 基于数据库配置的特性开关服务实现。
 *
 * <p>包装 {@link ConfigService#getFeatureFlags()} 提供的数据库公开配置，适配 core 的
 * {@link FeatureFlagService} 接口，使系统模块的特性开关查询归一化到统一抽象。
 *
 * <ul>
 *   <li>{@link #isEnabled(String)} 从数据库配置中查找开关键，将值解析为布尔后返回</li>
 *   <li>{@link #getFeatureFlags()} 直接委托 {@link ConfigService#getFeatureFlags()}</li>
 * </ul>
 *
 * <p>开关约定：配置分组 {@code configGroup == "feature-flag"} 或配置键以 {@code feature.} 前缀开头。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@RequiredArgsConstructor
public class DatabaseConfigFeatureFlagService implements FeatureFlagService {

  private final ConfigService configService;

  /**
   * 查询特性开关是否开启。
   *
   * <p>从数据库配置中查找开关键，将值解析为布尔：
   * 字符串 "true"/"false"、数值 1/0 等均可被正确解析。
   *
   * @param name 开关名称（小写点分格式）
   * @return 开启返回 true；未配置时返回 true（默认开启）
   */
  @Override
  public boolean isEnabled(String name) {
    return isEnabled(name, true);
  }

  /**
   * 查询特性开关是否开启，并指定未配置时的默认值。
   *
   * @param name 开关名称（小写点分格式）
   * @param defaultValue 未配置时的默认值
   * @return 开启返回 true；未配置时返回 {@code defaultValue}
   */
  @Override
  public boolean isEnabled(String name, boolean defaultValue) {
    if (name == null || name.isBlank()) {
      return defaultValue;
    }
    Map<String, Object> flags = configService.getFeatureFlags();
    Object value = flags.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean boolVal) {
      return boolVal;
    }
    if (value instanceof Number numVal) {
      return numVal.intValue() != 0;
    }
    String strVal = value.toString().trim().toLowerCase();
    if ("true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal) || "y".equals(strVal)) {
      return true;
    }
    if ("false".equals(strVal) || "0".equals(strVal) || "no".equals(strVal) || "n".equals(strVal)) {
      return false;
    }
    return defaultValue;
  }

  /**
   * 获取当前用户可见的全部特性开关映射。
   *
   * @return 开关名 → 开关值的映射；无开关时返回空 Map
   */
  @Override
  public Map<String, Object> getFeatureFlags() {
    return configService.getFeatureFlags();
  }
}
