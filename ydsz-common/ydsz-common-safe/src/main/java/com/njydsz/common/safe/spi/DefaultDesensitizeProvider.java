package com.njydsz.common.safe.spi;

import com.njydsz.common.safe.desensitize.SensitiveType;
import com.njydsz.common.safe.desensitize.SensitiveUtils;

/**
 * 默认数据脱敏策略提供者。
 *
 * <p>基于 ydzz-common-safe 内置的 {@link SensitiveUtils} 和 {@link SensitiveType} 提供标准脱敏规则。
 *
 * <p>由 {@link com.njydsz.common.safe.config.SafeConfiguration} 以 {@code @Bean} + {@code @Primary} 注册，
 * 业务模块可声明自定义 {@link DesensitizeProvider} 并使用 {@code @Primary} 覆盖本默认实现。
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see DesensitizeProvider
 */
public class DefaultDesensitizeProvider implements DesensitizeProvider {

  @Override
  public String desensitize(String raw, String type) {
    return defaultDesensitize(raw, type);
  }

  @Override
  public boolean supports(String type) {
    try {
      SensitiveType.valueOf(type.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
