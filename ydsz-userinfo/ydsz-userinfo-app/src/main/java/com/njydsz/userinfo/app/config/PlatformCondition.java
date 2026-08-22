package com.njydsz.userinfo.app.config;

import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 平台条件匹配逻辑（P1-2 双入口架构）。
 *
 * <p>读取 {@code ydsz.userinfo.platform} 配置值，与 {@link ConditionalOnPlatform#value()} 比对。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class PlatformCondition implements Condition {

  private static final String PLATFORM_PROPERTY = "ydsz.userinfo.platform";

  @SuppressWarnings("unchecked")
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Map<String, Object> platformValue = metadata.getAnnotationAttributes(
        ConditionalOnPlatform.class.getName());
    if (platformValue == null) {
      return false;
    }
    String expected = (String) platformValue.get("value");
    if (expected == null || expected.isBlank()) {
      return false;
    }
    String actual = context.getEnvironment().getProperty(PLATFORM_PROPERTY, "web");
    return expected.equalsIgnoreCase(actual);
  }
}
