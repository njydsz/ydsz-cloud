package com.njydsz.common.web.interceptor;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.base.api.ApiVersionResolver;

/**
 * API 版本响应头拦截器。
 *
 * <p>从 Controller 方法/类上解析 {@link ApiVersion} 注解，将版本信息写入 HTTP 响应头：
 *
 * <ul>
 *   <li>{@code X-Api-Version} — API 版本号（如 "v1"、"v2"）
 *   <li>{@code Deprecation} — 废弃标记（RFC 8594），值为 {@code "true"} 时表示接口已废弃
 *   <li>{@code Link} — 替代接口链接（RFC 5988），指向替代接口的 URL
 *   <li>{@code Sunset} — 计划移除时间（RFC 8594），值为 ISO 8601 日期或版本号
 * </ul>
 *
 * <p><b>装配方式：</b>通过 {@code WebMvcConfigurer.addInterceptors} 注册，无需自动装配。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Configuration
 * public class WebConfig implements WebMvcConfigurer {
 *   @Override
 *   public void addInterceptors(InterceptorRegistry registry) {
 *     registry.addInterceptor(new ApiVersionInterceptor());
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ApiVersion
 * @see ApiVersionResolver
 */
public class ApiVersionInterceptor implements HandlerInterceptor {

  /** API 版本响应头名称 */
  public static final String HEADER_API_VERSION = "X-Api-Version";

  /** 废弃标记响应头名称（RFC 8594） */
  public static final String HEADER_DEPRECATION = "Deprecation";

  /** 替代接口链接响应头名称（RFC 5988） */
  public static final String HEADER_LINK = "Link";

  /** 计划移除时间响应头名称（RFC 8594） */
  public static final String HEADER_SUNSET = "Sunset";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    // 仅处理 Spring MVC HandlerMethod
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    // 解析 API 版本注解
    Optional<ApiVersion> apiVersion = ApiVersionResolver.resolve(handlerMethod.getMethod());

    if (apiVersion.isPresent()) {
      ApiVersion version = apiVersion.get();

      // 设置 API 版本头
      response.setHeader(HEADER_API_VERSION, version.value());

      // 废弃接口附加 Deprecation / Link / Sunset 头
      if (version.deprecated()) {
        response.setHeader(HEADER_DEPRECATION, "true");

        if (!version.replacement().isBlank()) {
          response.setHeader(HEADER_LINK, "<" + version.replacement() + ">; rel=\"successor-version\"");
        }

        if (!version.removal().isBlank()) {
          response.setHeader(HEADER_SUNSET, version.removal());
        }
      }
    } else {
      // 未标注时设置默认版本
      response.setHeader(HEADER_API_VERSION, ApiVersionResolver.DEFAULT_VERSION);
    }

    return true;
  }

  /**
   * 获取当前请求的 API 版本号（供日志/指标使用）。
   *
   * @param handler Spring MVC handler
   * @return API 版本号
   */
  @Nullable
  public static String getVersion(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return null;
    }
    return ApiVersionResolver.resolveVersion(handlerMethod.getMethod());
  }

  /**
   * 判断当前请求的接口是否已废弃。
   *
   * @param handler Spring MVC handler
   * @return {@code true} 表示已废弃
   */
  public static boolean isDeprecated(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return false;
    }
    return ApiVersionResolver.resolve(handlerMethod.getMethod())
        .map(ApiVersion::deprecated)
        .orElse(false);
  }
}
