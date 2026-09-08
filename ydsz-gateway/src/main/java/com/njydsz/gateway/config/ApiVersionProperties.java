package com.njydsz.gateway.config;

import java.util.List;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P3-7: 网关 API 版本管理配置属性
 *
 * <p>支持 API 版本协商策略和弃用管理：
 *
 * <ul>
 *   <li>版本协商方式：Path（默认）→ Header → Query（优先级递减）
 *   <li>弃用版本管理：配置废弃版本及 Sunset 日期，网关自动注入响应头
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     api-version:
 *       enabled: true
 *       # 支持的版本列表
 *       supported-versions:
 *         - v1
 *         - v2
 *       # 默认版本（未指定版本时使用）
 *       default-version: v2
 *       # 弃用版本及 Sunset 日期（RFC 8594）
 *       deprecated-versions:
 *         v1:
 *           sunset: "2026-12-31T23:59:59Z"
 *           replacement: /api/v2
 *           message: "v1 将于 2026 年底下线，请迁移至 v2"
 *       # Header 协商配置
 *       header-negotiation:
 *         enabled: true
 *         header-name: X-API-Version
 *       # Query 协商配置
 *       query-negotiation:
 *         enabled: true
 *         param-name: api-version
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.api-version")
public class ApiVersionProperties {

  /** 是否启用 API 版本管理（默认 true）。 */
  private boolean enabled = true;

  /** 支持的版本列表（如 {@code v1, v2}），用于版本号校验。 */
  private List<String> supportedVersions = List.of("v1", "v2");

  /** 请求未指定版本时使用的默认版本。 */
  private String defaultVersion = "v2";

  /** 弃用版本配置：版本号 → 弃用详情（Sunset 日期 + 替代路径 + 说明）。 */
  private Map<String, DeprecatedVersion> deprecatedVersions = Map.of();

  /** Header 版本协商配置（{@code X-API-Version} 头）。 */
  private HeaderNegotiationConfig headerNegotiation = new HeaderNegotiationConfig();

  /** Query 版本协商配置（{@code api-version} 查询参数）。 */
  private QueryNegotiationConfig queryNegotiation = new QueryNegotiationConfig();

  /** Header 版本协商配置：是否启用 + 请求头名称。 */
  @Data
  public static class HeaderNegotiationConfig {
    /** 是否启用 Header 协商 */
    private boolean enabled = true;

    /** 版本请求头名称 */
    private String headerName = "X-API-Version";
  }

  /** Query 版本协商配置：是否启用 + 查询参数名。 */
  @Data
  public static class QueryNegotiationConfig {
    /** 是否启用 Query 参数版本协商（默认 true）。 */
    private boolean enabled = true;

    /** 版本查询参数名称（默认 {@code api-version}）。 */
    private String paramName = "api-version";
  }

  /** 弃用版本详情：Sunset 日期 + 替代路径 + 说明。 */
  @Data
  public static class DeprecatedVersion {
    /** Sunset 日期（RFC 3339 / ISO 8601 格式，如 {@code 2026-12-31T23:59:59Z}）。 */
    private String sunset;

    /** 替代版本路径（如 {@code /api/v2}）。 */
    private String replacement;

    /** 自定义弃用说明（中文文案，前端可展示给用户）。 */
    private String message;
  }
}
