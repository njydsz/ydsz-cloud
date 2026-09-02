package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GeoIP 地理围栏配置属性（P3-3）。
 *
 * <p>配置 IP 地理位置解析和异常登录检测相关参数。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.geoip}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     geoip:
 *       enabled: true
 *       mmdb-path: /data/GeoLite2-City.mmdb
 *       anomaly-threshold-km: 500
 *       risk-score-anomaly: 25
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.geoip")
public class GeoIpProperties {

  /** 默认anomalyThresholdKm值（可被配置文件覆盖） */
  private static final double DEFAULT_ANOMALY_THRESHOLD_KM = 500;

  /** 默认riskScoreAnomaly值（可被配置文件覆盖） */
  private static final int DEFAULT_RISK_SCORE_ANOMALY = 25;

  /** 是否启用 GeoIP 地理围栏 */
  private boolean enabled = true;

  /**
   * GeoIP2 MMDB 数据库文件路径。
   *
   * <p>文件可从 MaxMind 官方下载 GeoLite2-City.mmdb（免费）。
   * 如果文件不存在或路径为空，服务将降级为不解析地理位置（不影响其他功能）。
   */
  private String mmdbPath = "";

  /**
   * 异常登录距离阈值（公里）。
   *
   * <p>当本次登录地与上次登录地距离超过此阈值时，视为异常登录。
   * 默认 500 公里（约等于跨省/跨国距离）。
   */
  private double anomalyThresholdKm = DEFAULT_ANOMALY_THRESHOLD_KM;

  /**
   * 地理位置异常时的风险评分附加值。
   *
   * <p>触发地理异常时，在基础风险评分上增加此分数。
   * 默认 25 分（可将 SAFE 提升至 MEDIUM，或 MEDIUM 提升至 HIGH）。
   */
  private int riskScoreAnomaly = DEFAULT_RISK_SCORE_ANOMALY;

  /** 是否将地理位置信息记录到登录历史 */
  private boolean logLocationEnabled = true;
}
