package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 访问控制配置属性。
 *
 * <p>统一配置 IP 黑名单（Redis 动态 + 本地缓存）和白名单（配置文件）：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     ip-control:
 *       # IP 黑名单（Redis 动态管理）
 *       blacklist-enabled: true
 *       blacklist-ttl-seconds: 10
 *       blacklist-max-size: 50000
 *       blacklist-fail-mode: fail-open
 *       # IP 白名单（配置文件）
 *       whitelist-enabled: false
 *       whitelist: "192.168.1.0/24,10.0.0.1"
 *       whitelist-skip-paths:
 *         - /health
 *         - /auth/login
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.ip-control")
public class IpAccessControlProperties {

  /** 是否启用 IP 黑名单检查（默认 true，基于 Redis + 本地 L1 缓存两级架构）。 */
  private boolean blacklistEnabled = true;

  /** 黑名单 L1 本地缓存 TTL（秒，默认 10s）。 */
  private long blacklistTtlSeconds = 10;

  /** 黑名单 L1 本地缓存最大条目数（默认 50,000）。 */
  private long blacklistMaxSize = 50_000;

  /** Redis 异常时黑名单检查的降级策略：{@code fail-open}（放行）或 {@code fail-closed}（拒绝）。 */
  private String blacklistFailMode = "fail-open";

  /** 是否启用 IP 白名单检查（默认 false：仅在明确配置白名单后启用）。 */
  private boolean whitelistEnabled = false;

  /** IP 白名单配置字符串（逗号分隔，支持 CIDR 表示法与单个 IP）。 */
  private String whitelist = "";

  /** 白名单放行的路径前缀（命中这些路径跳过 IP 校验，如健康检查端点）。 */
  private List<String> whitelistSkipPaths = new ArrayList<>(4);
}
