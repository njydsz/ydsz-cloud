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
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.ip-control")
public class IpAccessControlProperties {

  /** 是否启用 IP 黑名单检查 */
  private boolean blacklistEnabled = true;

  /** 本地缓存 TTL（秒） */
  private long blacklistTtlSeconds = 10;

  /** 本地缓存最大容量 */
  private long blacklistMaxSize = 50_000;

  /** Redis 异常时的降级策略：fail-open（放行）或 fail-closed（拒绝） */
  private String blacklistFailMode = "fail-open";

  /** 是否启用 IP 白名单检查 */
  private boolean whitelistEnabled = false;

  /** IP 白名单（逗号分隔，支持 CIDR 与单个 IP） */
  private String whitelist = "";

  /** 白名单放行的路径前缀 */
  private List<String> whitelistSkipPaths = new ArrayList<>();
}
