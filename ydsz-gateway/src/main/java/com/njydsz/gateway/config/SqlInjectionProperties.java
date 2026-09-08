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

  /** 是否启用 SQL 注入检测（默认 true）。 */
  private boolean enabled = true;

  /** 检测模式：{@code STANDARD}（标准规则集） / {@code STRICT}（含额外严格规则）。 */
  private String mode = "STANDARD";

  /** 命中 SQL 注入后是否自动封禁来源 IP（通过 Redis 黑名单）。 */
  private boolean autoBlock = true;

  /** 触发自动封禁的累计命中次数阈值（默认 3 次）。 */
  private int autoBlockThreshold = 3;

  /** 自动封禁 IP 的 TTL（秒，默认 3600s = 1 小时）。 */
  private long autoBlockTtlSeconds = 3600;

  /** 白名单参数名：不涉及 SQL 查询的参数跳过检测（如分页/排序字段）。 */
  private List<String> whitelistParamNames = List.of("tenantId", "page", "size", "sort");
}
