package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 登录风控与 MFA 配置属性（从 {@link UserInfoProperties} 拆分的子配置）。
 *
 * <p>聚焦风险评分权重、异常时段、频率阈值、可信代理等登录风控参数。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.risk}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     risk:
 *       window-seconds: 300
 *       mfa-risk-threshold: 60
 *       ip-weight: 30
 *       time-weight: 20
 *       device-weight: 25
 *       frequency-weight: 25
 *       anomaly-start-hour: 0
 *       anomaly-end-hour: 6
 *       frequency-window-minutes: 5
 *       frequency-threshold: 3
 *       trusted-proxies:
 *         - "10.0.0.0/8"
 *         - "172.16.0.0/12"
 * </pre>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.risk")
public class UserLoginRiskProperties {

  /** 风险因子采集窗口（秒），默认 5 分钟。 */
  private long windowSeconds = 300;

  /** 触发 MFA 的风险评分阈值（含），默认 60。 */
  private int mfaRiskThreshold = 60;

  /** IP 风险权重，默认 30。 */
  private int ipWeight = 30;

  /** 时间异常权重，默认 20。 */
  private int timeWeight = 20;

  /** 设备异常权重，默认 25。 */
  private int deviceWeight = 25;

  /** 频率异常权重，默认 25。 */
  private int frequencyWeight = 25;

  /** 异常时段起始小时（默认 0）。 */
  private int anomalyStartHour = 0;

  /** 异常时段结束小时（默认 6）。 */
  private int anomalyEndHour = 6;

  /** 频率异常窗口（分钟），默认 5 分钟。 */
  private int frequencyWindowMinutes = 5;

  /** 频率异常阈值（窗口内尝试次数），默认 3 次。 */
  private int frequencyThreshold = 3;

  /** 可信代理 IP 列表，为空时不信任任何代理头。 */
  private java.util.List<String> trustedProxies = java.util.List.of();
}
