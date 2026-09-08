package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 弃用配置属性。
 *
 * <p>在 {@code application.yml} 中通过 {@code ydsz.gateway.deprecation} 前缀配置，控制特定路径的弃用警告行为。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     deprecation:
 *       enabled: true
 *       apis:
 *         - path: /api/message/send
 *           since: "v1"
 *           replacement: "v2"
 *           removalDate: "2026-12-31"
 *           message: "请使用 /api/v2/message/send 替代"
 * </pre>
 *
 * <p>匹配规则：请求路径以 {@code path} 配置的前缀开头即视为弃用命中。
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.deprecation")
public class DeprecationProperties {

  /** 是否启用弃用警告系统 */
  private boolean enabled = true;

  /** 已弃用 API 配置列表 */
  private List<DeprecatedApiEntry> apis = new ArrayList<>(8);

  /** 单条弃用 API 配置项 */
  @Data
  public static class DeprecatedApiEntry {
    /** 弃用路径前缀（如 /api/message/send） */
    private String path;

    /** 弃用起始版本 */
    private String since;

    /** 替代路径或版本 */
    private String replacement;

    /** 预计移除日期（RFC 1123 格式字符串） */
    private String removalDate;

    /** 自定义弃用说明 */
    private String message;
  }
}
