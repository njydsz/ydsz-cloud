package com.njydsz.common.jdbc.config;

import java.util.Set;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 安全查询配置属性（ORDER BY 注入防护 + 深度分页检测）
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     safe-query:
 *       enabled: true
 *       strict-mode: false
 *       order-by-whitelist: [id, created_at, updated_at]
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.7.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.safe-query")
public class SafeQueryProperties {

  /** 是否启用安全查询拦截（默认 true） */
  private boolean enabled = true;

  /**
   * 严格模式（默认 false）
   *
   * <ul>
   *   <li>true: 非法排序字段抛出异常
   *   <li>false: 忽略非法排序字段（仅日志警告）
   * </ul>
   */
  private boolean strictMode = false;

  /**
   * 排序字段白名单
   *
   * <p>配置后，仅允许白名单中的字段参与排序。为空时仅使用正则校验。
   */
  private Set<String> orderByWhitelist;
}
