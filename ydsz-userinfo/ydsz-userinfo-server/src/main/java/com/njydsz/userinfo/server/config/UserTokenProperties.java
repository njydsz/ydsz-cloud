package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token 与会话配置属性（从 {@link UserInfoProperties} 拆分的子配置）。
 *
 * <p>聚焦 access_token 有效期、分端会话限制、Token 自动续签等会话管理参数。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.token}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     token:
 *       ttl-seconds: 7200
 *       max-sessions-per-user: 5
 *       auto-renewal-enabled: true
 *       auto-renewal-threshold-percent: 10
 *       max-sessions-per-device-type:
 *         web: 3
 *         app: 2
 *         api: -1
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.token")
public class UserTokenProperties {

  /** access_token 有效期（秒），默认 2 小时。 */
  private long ttlSeconds = 7200;

  /** 单用户最大并发会话数（0 表示不限制），默认 5。 */
  private int maxSessionsPerUser = 5;

  /** 是否启用 Token 自动续签，默认 true。 */
  private boolean autoRenewalEnabled = true;

  /** Token 自动续签阈值百分比（0-100），默认 10%。 */
  private int autoRenewalThresholdPercent = 10;

  /** 分端会话限制配置（deviceType → 最大会话数，-1 表示不限制）。 */
  private Map<String, Integer> maxSessionsPerDeviceType = new HashMap<>();

  /**
   * 获取指定设备类型的最大会话数。
   *
   * <p>未配置分端限制时回退到全局 {@link #getMaxSessionsPerUser()}。
   *
   * @param deviceType 设备类型编码（如 web/app/api）
   * @return 该设备类型的最大会话数
   */
  public int getMaxSessionsForDevice(String deviceType) {
    return maxSessionsPerDeviceType.getOrDefault(deviceType, maxSessionsPerUser);
  }
}
