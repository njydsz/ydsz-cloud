package com.njydsz.common.core.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.validation.annotation.Validated;

/**
 * Core 模块运行时配置属性。
 *
 * <p>通过 {@code ydsz.core.*} 前缀绑定 application.yml 中的配置项， 提供分页参数运行时覆盖能力。
 *
 * <h3>配置示例</h3>
 *
 * <pre>{@code
 * ydsz:
 *   core:
 *     enabled: true
 *     max-page-size: 1000
 *     default-page-size: 20
 *     api-version:
 *       default: "v1"
 *       header: "X-Api-Version"
 *       routes:
 *         v2: ["/api/v2/**"]
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CoreAutoConfiguration
 * @see com.njydsz.common.core.constant.PageConstants
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

  /**
   * 是否启用 Core 模块自动配置。
   *
   * <p>设为 {@code false} 可完全禁用 CoreAutoConfiguration 注册的 Bean （RequestContext
   * 工具类仍可直接使用，其为纯静态工具不依赖自动配置）。
   */
  private boolean enabled = true;

  /**
   * 运行时最大每页记录数上限。
   *
   * <p>由 {@link com.njydsz.common.core.constant.PageConstants#getMaxPageSize()} 读取，
   * 防止客户端一次性拉取过多数据导致内存/CPS 压力。默认 1000。
   */
  @Min(1)
  @Max(5000)
  private int maxPageSize = 1000;

  /**
   * 运行时默认每页记录数。
   *
   * <p>由 {@link com.njydsz.common.core.constant.PageConstants#getDefaultPageSize()} 读取， 分页查询未指定
   * pageSize 时使用。默认 20。
   */
  @Min(1)
  @Max(5000)
  private int defaultPageSize = 20;

  /**
   * 默认语言环境。
   *
   * <p>用于 i18n 消息解析、{@link com.njydsz.common.core.context.RequestContext#setLanguage(String)} 兜底等。
   * 支持任意 JDK {@link java.util.Locale} 格式（如 {@code zh-CN}、{@code en-US}）。
   *
   * @since 1.11.0
   */
  private String defaultLocale = "zh-CN";

  /**
   * 租户 MDC 过滤器执行顺序。
   *
   * <p>默认 {@code HIGHEST_PRECEDENCE + 100}，高于业务过滤器。 由 web/auth 模块的 FilterRegistrationBean 消费。
   */
  private int tenantMdcFilterOrder = Ordered.HIGHEST_PRECEDENCE + 100;

  /**
   * API 版本路由配置。
   *
   * <p>供 web 模块消费以建立版本号→路由映射与默认版本策略。 业务模块需要版本灰度分流时可按此配置实现 {@code HandlerMapping} 路由。
   *
   * @since 1.10.0
   */
  private ApiVersionConfig apiVersion = new ApiVersionConfig();

  /**
   * 特性开关（Feature Flag）映射。
   *
   * <p>键为开关名称（小写点分格式，如 {@code user.register.sms}）， 值为是否开启（默认开启）。 用于灰度发布、渐进式上线与紧急熔断。
   *
   * <p>示例：{@code ydsz.core.feature-flags: { user.register.sms: true }}
   *
   * @since 1.14.0
   */
  private Map<String, Boolean> featureFlags = Collections.emptyMap();

  /**
   * 校验分页范围合法性：defaultPageSize 不应大于 maxPageSize。
   *
   * <p>该校验在应用启动时执行（@Validated + @AssertTrue）， 配置不合法时快速失败阻止启动。
   *
   * @return 分页范围是否合法
   */
  @AssertTrue(message = "ydsz.core.default-page-size must be <= ydsz.core.max-page-size")
  public boolean isPaginationRangeValid() {
    return defaultPageSize <= maxPageSize;
  }

  /**
   * API 版本配置属性（嵌套配置对象）。
   *
   * <p>绑定到 {@code ydsz.core.api-version.*}。
   *
   * @since 1.10.0
   */
  @Data
  public static class ApiVersionConfig {

    /** 默认 API 版本号。 */
    private String defaultVersion = "v1";

    /** 客户端声明版本的 HTTP 请求头名称。 */
    private String header = "X-Api-Version";

    /**
     * 版本号 → URL 路径模式的路由规则。
     *
     * <p>Map key 为版本号（如 "v2"），value 为该版本匹配的 URL Ant 路径模式列表。 web 模块可根据此映射做版本路由分发。不配置时使用 header 兜底。
     *
     * <p>示例：{@code {"v2": ["/api/v2/**"]}}
     */
    private Map<String, List<String>> routes = Collections.emptyMap();
  }
}
