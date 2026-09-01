package com.njydsz.common.base.api;

import java.lang.reflect.Method;
import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * API 版本注解解析器。
 *
 * <p>从 Spring MVC Handler（Controller 方法）上解析 {@link ApiVersion} 注解， 优先级：方法级 > 类级 > 默认值。
 *
 * <p>供拦截器、OpenAPI 自定义处理器、日志埋点等场景复用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ApiVersionResolver {

  /** 默认 API 版本（未标注时返回） */
  public static final String DEFAULT_VERSION = "v1";

  private ApiVersionResolver() {
    // 工具类
  }

  /**
   * 从 handler 对象上解析 {@link ApiVersion} 注解。
   *
   * <p>解析优先级：
   *
   * <ol>
   *   <li>若 handler 为 Method，优先查找方法级注解，未找到则查找声明类级注解
   *   <li>若 handler 为 Class，查找类级注解
   *   <li>都未找到返回 {@code Optional.empty()}
   * </ol>
   *
   * @param handler Spring MVC handler（方法或类）
   * @return API 版本注解；未找到返回 {@code Optional.empty()}
   */
  public static Optional<ApiVersion> resolve(Object handler) {
    if (handler instanceof Method method) {
      // 优先方法级
      ApiVersion annotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
      if (annotation != null) {
        return Optional.of(annotation);
      }
      // 回退到声明类级
      return resolveFromClass(method.getDeclaringClass());
    }
    if (handler instanceof Class<?> clazz) {
      return resolveFromClass(clazz);
    }
    return Optional.empty();
  }

  /**
   * 从 Class 上解析 {@link ApiVersion} 注解（使用 Spring 的合并注解语义，支持元注解）。
   *
   * @param clazz Controller 类
   * @return API 版本注解；未找到返回 {@code Optional.empty()}
   */
  public static Optional<ApiVersion> resolveFromClass(Class<?> clazz) {
    return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(clazz, ApiVersion.class));
  }

  /**
   * 获取 API 版本号字符串（带降级）。
   *
   * <p>解析优先级同 {@link #resolve}，未找到时返回 {@link #DEFAULT_VERSION}。
   *
   * @param handler Spring MVC handler
   * @return API 版本号字符串（永不为 {@code null}）
   */
  public static String resolveVersion(Object handler) {
    return resolve(handler).map(ApiVersion::value).orElse(DEFAULT_VERSION);
  }
}
