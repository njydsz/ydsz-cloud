package com.njydsz.gateway.config;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL 注入检测过滤器配置属性。
 *
 * <p>控制网关层 SQL 注入检测行为：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     sql-injection:
 *       enabled: true
 *       mode: STANDARD          # STANDARD / STRICT
 *       auto-block: true
 *       auto-block-threshold: 3
 *       auto-block-ttl-seconds: 3600
 *       whitelist-param-names:
 *         - tenantId
 *         - page
 *         - size
 *         - sort
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.sql-injection")
public class SqlInjectionProperties {

  /** 是否启用 SQL 注入检测 */
  private boolean enabled = true;

  /** 检测模式：STRICT(严格，更多规则) / STANDARD(标准) */
  private String mode = "STANDARD";

  /** 命中后是否自动封禁 IP（通过 Redis） */
  private boolean autoBlock = true;

  /** 触发自动封禁的命中次数阈值 */
  private int autoBlockThreshold = 3;

  /** 自动封禁时长（秒） */
  private long autoBlockTtlSeconds = 3600;

  /** 白名单参数名（不参与 SQL 注入检测） */
  private List<String> whitelistParamNames = List.of("tenantId", "page", "size", "sort");
}
