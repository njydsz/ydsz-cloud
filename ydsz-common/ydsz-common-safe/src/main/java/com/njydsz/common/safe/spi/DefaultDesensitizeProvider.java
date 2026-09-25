package com.njydsz.common.safe.spi;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 默认数据脱敏策略提供者。
 *
 * <p>基于 ydzs-common-safe 内置的 {@link
 * com.njydsz.common.safe.desensitize.SensitiveUtils SensitiveUtils} 和 {@link
 * com.njydsz.common.safe.desensitize.SensitiveType SensitiveType} 提供标准脱敏规则。
 *
 * <p>声明为 {@link Primary} 保证在业务模块未自定义实现时自动生效；
 * 业务模块可通过实现 {@link DesensitizeProvider} 并使用 {@code @Primary} 覆盖本默认实现。
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see DesensitizeProvider
 */
@Component
@Primary
public class DefaultDesensitizeProvider implements DesensitizeProvider {

  @Override
  public String desensitize(String raw, String type) {
    return defaultDesensitize(raw, type);
  }

  @Override
  public boolean supports(String type) {
    try {
      com.njydsz.common.safe.desensitize.SensitiveType.valueOf(type.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
